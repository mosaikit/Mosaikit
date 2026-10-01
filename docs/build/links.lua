-- SPDX-FileCopyrightText: 2026 Massimo Antonini
-- SPDX-License-Identifier: MPL-2.0
--
-- Links to other Markdown files of the repository mean nothing in a Word or PDF document: keep
-- their text, drop the link. Web links and anchors stay.
function Link(link)
  local target = link.target
  if target:match('^%a[%w+.-]*:') or target:sub(1, 1) == '#' then
    return link
  end
  return pandoc.Span(link.content)
end
