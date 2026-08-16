-- Replaces images with their alternative text.
--
-- Knowledge-base images are served from authenticated URLs that the Typst compiler cannot fetch,
-- and an image it cannot load aborts the whole render. Keeping the alt text tells the reader that
-- something was there.
function Image(el)
  return pandoc.Emph(el.caption)
end
