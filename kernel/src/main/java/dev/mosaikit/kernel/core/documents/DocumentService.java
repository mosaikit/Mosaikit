// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.core.documents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mosaikit.kernel.api.context.CurrentOrganization;
import dev.mosaikit.kernel.api.plugin.PluginManifest;
import dev.mosaikit.kernel.core.error.ConflictException;
import dev.mosaikit.kernel.core.error.InvalidInputException;
import dev.mosaikit.kernel.core.error.ResourceNotFoundException;
import dev.mosaikit.kernel.core.live.LiveBus;
import dev.mosaikit.kernel.core.live.LiveEvent;
import dev.mosaikit.kernel.core.plugin.InstalledPlugin;
import dev.mosaikit.kernel.core.plugin.PluginRegistry;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.data.Limit;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The collections of documents of plugins without a backend (ADR-0031, MK-046): only an active
 * plugin that declares a collection in its manifest has it, and a request reads and writes only the
 * documents of its organization.
 */
@ApplicationScoped
@Transactional
public class DocumentService {

    /** Largest document, as JSON. */
    static final int MAX_BYTES = 256 * 1024;

    /** Most documents of one collection of an organization. */
    static final long MAX_DOCUMENTS = 10_000;

    /** Most documents of one page. */
    static final int MAX_PAGE = 500;

    private final PluginDocuments documents;
    private final PluginRegistry registry;
    private final CurrentOrganization organization;
    private final SecurityIdentity identity;
    private final ObjectMapper json;
    private final Clock clock;
    private final LiveBus live;

    @Inject
    public DocumentService(
            PluginDocuments documents,
            PluginRegistry registry,
            CurrentOrganization organization,
            SecurityIdentity identity,
            ObjectMapper json,
            LiveBus live) {
        this(documents, registry, organization, identity, json, live, Clock.systemUTC());
    }

    DocumentService(
            PluginDocuments documents,
            PluginRegistry registry,
            CurrentOrganization organization,
            SecurityIdentity identity,
            ObjectMapper json,
            LiveBus live,
            Clock clock) {
        this.live = live;
        this.documents = documents;
        this.registry = registry;
        this.organization = organization;
        this.identity = identity;
        this.json = json;
        this.clock = clock;
    }

    public List<DocumentView> list(String plugin, String collection, int offset, int limit) {
        UUID org = organization.require();
        declared(plugin, collection);
        int size = Math.clamp(limit, 1, MAX_PAGE);
        int start = Math.max(offset, 0);
        return documents.list(org, plugin, collection, Limit.range(start + 1L, start + (long) size)).stream()
                .map(this::view)
                .toList();
    }

    public DocumentView get(String plugin, String collection, UUID id) {
        return view(find(plugin, collection, id));
    }

    public DocumentView create(String plugin, String collection, JsonNode data) {
        UUID org = organization.require();
        declared(plugin, collection);
        String text = checked(data);
        if (documents.count(org, plugin, collection) >= MAX_DOCUMENTS) {
            throw new ConflictException("The collection " + collection + " has " + MAX_DOCUMENTS
                    + " documents already, the most it can hold.");
        }
        PluginDocument document = new PluginDocument(org, plugin, collection, text, author(), clock.instant());
        documents.insert(document);
        changed(plugin, collection, document.getId(), "created");
        return view(document);
    }

    /**
     * Replaces a document.
     *
     * @param expectedVersion the version the caller read, or {@code null} to overwrite any version
     */
    public DocumentView replace(String plugin, String collection, UUID id, JsonNode data, Integer expectedVersion) {
        PluginDocument document = find(plugin, collection, id);
        if (expectedVersion != null && expectedVersion != document.getVersion()) {
            throw new ConflictException("The document changed since version " + expectedVersion
                    + ": read it again, it is at version " + document.getVersion() + ".");
        }
        document.replace(checked(data), author(), clock.instant());
        documents.update(document);
        changed(plugin, collection, id, "replaced");
        return view(document);
    }

    public void delete(String plugin, String collection, UUID id) {
        documents.delete(find(plugin, collection, id));
        changed(plugin, collection, id, "deleted");
    }

    /**
     * Tells the pages that subscribed to the collection, when the transaction commits (MK-031): only
     * which document changed, so that each page reads it again with its own rights.
     */
    private void changed(String plugin, String collection, UUID id, String action) {
        live.publish(new LiveEvent(
                "documents." + plugin + "." + collection,
                organization.require(),
                null,
                Map.of("id", id.toString(), "action", action)));
    }

    private PluginDocument find(String plugin, String collection, UUID id) {
        UUID org = organization.require();
        declared(plugin, collection);
        return documents
                .findById(id)
                .filter(document -> document.getOrganizationId().equals(org)
                        && document.getPluginId().equals(plugin)
                        && document.getCollection().equals(collection))
                .orElseThrow(() -> new ResourceNotFoundException("No document " + id + " in " + collection + "."));
    }

    /** The collection must be declared by an active plugin; otherwise it does not exist. */
    private void declared(String plugin, String collection) {
        boolean declared = registry.findActive(plugin)
                .flatMap(InstalledPlugin::manifest)
                .flatMap(PluginManifest::data)
                .filter(data -> data.declares(collection))
                .isPresent();
        if (!declared) {
            throw new ResourceNotFoundException(
                    "No active plugin " + plugin + " declares the collection " + collection + ".");
        }
    }

    private String checked(JsonNode data) {
        if (data == null || !data.isObject()) {
            throw new InvalidInputException(List.of("document must be a JSON object"));
        }
        try {
            String text = json.writeValueAsString(data);
            if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
                throw new InvalidInputException(List.of("document is larger than " + MAX_BYTES + " bytes"));
            }
            return text;
        } catch (JsonProcessingException e) {
            throw new InvalidInputException(List.of("document is not valid JSON"));
        }
    }

    private String author() {
        return identity.getPrincipal().getName();
    }

    private DocumentView view(PluginDocument document) {
        try {
            return new DocumentView(
                    document.getId(),
                    json.readTree(document.getData()),
                    document.getVersion(),
                    document.getCreatedAt(),
                    document.getCreatedBy(),
                    document.getUpdatedAt(),
                    document.getUpdatedBy());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The document " + document.getId() + " is not valid JSON", e);
        }
    }
}
