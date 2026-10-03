--- Turns what the article editor writes beyond plain markdown into Typst, and keeps the pictures
--- the exporter placed next to the document.
---
--- Pictures: the exporter reads each picture it can reach and rewrites its source to a local name
--- such as `img-1.webp`. Anything still pointing elsewhere is a URL the Typst compiler cannot fetch,
--- and an image it cannot load aborts the whole render, so it becomes its alt text: the reader
--- still learns that something was there. A picture standing alone in its paragraph becomes a block
--- of its own, so the template can centre it. A picture the editor gave a width is written as an
--- `<img>` tag and keeps that width.
---
--- Formatting: coloured text, highlights and underlining reach markdown as HTML tags, and the
--- default highlight as `==text==`, none of which the markdown reader understands. A pair of tags
--- becomes the matching Typst function around what it encloses. A tag without its partner is
--- dropped rather than opened, because an unclosed bracket would abort the render.
---
--- Captions: the exporter marks the line under a picture as `<figcaption>`, which becomes a call
--- to the template's `caption` function so it prints set apart from the text around it.
---
--- Fonts: a letter sets words in a family of their own as `<span data-font="Family">`. With the
--- `font-spans` variable set, the pair becomes a call to the template's `font` function, which picks
--- the family when the letter is printed. Without it the tag is dropped and the words are kept, since
--- no other template defines that function.
---
--- Alignment: the editor stores a centred, right-aligned or justified paragraph or heading wrapped in
--- `<div data-align="...">`, a blank line on either side, so the markdown reader still reads what is
--- inside and leaves the two tags as blocks of HTML around it. A pair of them becomes `align(center)`,
--- `align(right)` or a block that justifies its paragraphs, around the converted blocks between.
--- Paired the same way as the inline tags, an unpaired one is dropped.

local PIXEL = 0.75
local FONT_SPANS = PANDOC_WRITER_OPTIONS.variables["font-spans"] ~= nil

local function is_printable(src)
  return src ~= nil and src:match("^img%-%d+%.%a+$") ~= nil
end

local function attribute(tag, name)
  return tag:match("%s" .. name .. '%s*=%s*"([^"]*)"')
end

local function css(style, property)
  if not style then return nil end
  local value = (";" .. style):match(";%s*" .. property .. "%s*:%s*([^;]+)")
  return value and value:gsub("%s+$", "")
end

local function typst_color(value)
  if not value then return nil end
  local hex = value:match("^#(%x+)$")
  if hex and (#hex == 3 or #hex == 6) then return 'rgb("#' .. hex .. '")' end
  local r, g, b = value:match("^rgba?%(%s*(%d+)%s*,%s*(%d+)%s*,%s*(%d+)")
  if r then return "rgb(" .. r .. ", " .. g .. ", " .. b .. ")" end
  return nil
end

local function typst_string(text)
  return '"' .. text:gsub("\\", "\\\\"):gsub('"', '\\"') .. '"'
end

local function unescape(text)
  return (text:gsub("&quot;", '"'):gsub("&lt;", "<"):gsub("&gt;", ">"):gsub("&amp;", "&"))
end

local function font_family(tag)
  local family = FONT_SPANS and attribute(tag, "data%-font")
  return family and unescape(family):match("^%s*(.-)%s*$")
end

local function opener(name, tag)
  if name == "u" then return "#underline[" end
  if name == "mark" then
    local fill = typst_color(attribute(tag, "data%-color") or css(attribute(tag, "style"), "background%-color"))
    return fill and ("#highlight(fill: " .. fill .. ")[") or "#highlight["
  end
  if name == "span" then
    local family = font_family(tag)
    if family then return "#font(" .. typst_string(family) .. ")[" end
    local fill = typst_color(css(attribute(tag, "style"), "color"))
    return fill and ("#text(fill: " .. fill .. ")[") or nil
  end
  return nil
end

local ALIGNMENTS = {
  center = "#align(center)[",
  right = "#align(right)[",
  justify = "#[#set par(justify: true)\n",
}

local function block_opener(name, tag)
  if name ~= "div" then return nil end
  return ALIGNMENTS[attribute(tag, "data%-align") or ""]
end

local function html_tag(el)
  if (el.t ~= "RawInline" and el.t ~= "RawBlock") or el.format ~= "html" then return nil end
  local closing, name = el.text:match("^%s*<(/?)(%a+)")
  if not name then return nil end
  return name:lower(), closing == "/"
end

local function picture(tag)
  local src = attribute(tag, "src")
  local alt = attribute(tag, "alt") or ""
  if not is_printable(src) then return nil, alt end
  local width = tonumber(attribute(tag, "width") or "")
  local size = width and (", width: " .. (width * PIXEL) .. "pt") or ""
  return "#image(" .. typst_string(src) .. size .. ")", alt
end

local function picture_inline(tag)
  local typst, alt = picture(tag)
  if typst then return pandoc.RawInline("typst", "#box(" .. typst:sub(2) .. ")") end
  return pandoc.Emph(pandoc.Inlines(alt))
end

local function pair_tags(elements)
  local open, pairs = {}, {}
  for i, el in ipairs(elements) do
    local name, closing = html_tag(el)
    if name then
      if not closing then
        table.insert(open, {name = name, index = i})
      else
        for j = #open, 1, -1 do
          if open[j].name == name then
            pairs[open[j].index] = i
            for _ = #open, j, -1 do table.remove(open) end
            break
          end
        end
      end
    end
  end
  return pairs
end

local function highlight_markers(inlines)
  local result, open_at = pandoc.Inlines{}, nil
  for _, el in ipairs(inlines) do
    if el.t ~= "Str" then
      result:insert(el)
    else
      local text = el.text
      if not open_at and text:match("^==[^=]") then
        result:insert(pandoc.RawInline("typst", "#highlight["))
        open_at = #result
        text = text:sub(3)
      end
      local body, tail = text:match("^(.-[^=])==(%p*)$")
      if open_at and body then
        result:insert(pandoc.Str(body))
        result:insert(pandoc.RawInline("typst", "]"))
        if tail ~= "" then result:insert(pandoc.Str(tail)) end
        open_at = nil
      else
        result:insert(pandoc.Str(text))
      end
    end
  end
  if open_at then result[open_at] = pandoc.Str("==") end
  return result
end

local function typst_inline(text) return pandoc.RawInline("typst", text) end

local function typst_block(text) return pandoc.RawBlock("typst", text) end

local function translate_pairs(elements, result, open_with, typst)
  local pairs = pair_tags(elements)
  local closers = {}
  for i, el in ipairs(elements) do
    local name, closing = html_tag(el)
    if name and closing then
      if closers[i] then result:insert(typst("]")) end
    elseif name then
      local start = pairs[i] and open_with(name, el.text)
      if start then
        result:insert(typst(start))
        closers[pairs[i]] = true
      end
    else
      result:insert(el)
    end
  end
  return result
end

local function with_pictures(inlines)
  return inlines:map(function(el)
    if html_tag(el) == "img" then return picture_inline(el.text) end
    return el
  end)
end

function Inlines(inlines)
  return highlight_markers(translate_pairs(with_pictures(inlines), pandoc.Inlines{}, opener, typst_inline))
end

function Blocks(blocks)
  return translate_pairs(blocks, pandoc.Blocks{}, block_opener, typst_block)
end

function Image(el)
  if is_printable(el.src) then
    return el
  end
  return pandoc.Emph(el.caption)
end

function RawBlock(el)
  if el.format ~= "html" then return nil end
  local caption = el.text:match("^%s*<figcaption>(.-)</figcaption>%s*$")
  if caption then
    local words = pandoc.utils.blocks_to_inlines(pandoc.read(caption, "gfm").blocks)
    local out = pandoc.Inlines{pandoc.RawInline("typst", "#caption[")}
    out:extend(words)
    out:insert(pandoc.RawInline("typst", "]"))
    return pandoc.Plain(out)
  end
  if not el.text:match("^%s*<img") then return nil end
  local typst, alt = picture(el.text)
  if typst then return pandoc.RawBlock("typst", typst) end
  return pandoc.Para{pandoc.Emph(pandoc.Inlines(alt))}
end

function Para(el)
  local only = el.content[1]
  if #el.content == 1 and only.t == "Image" and is_printable(only.src) then
    return pandoc.RawBlock("typst", "#image(" .. typst_string(only.src) .. ")")
  end
end
