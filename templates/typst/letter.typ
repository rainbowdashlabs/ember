#let data = json("data.json")

#let placeholder(key) = {
  if key in data.values {
    data.values.at(key)
  } else if data.showLabels {
    highlight(fill: luma(225), extent: 1pt)[\[#data.labels.at(key, default: key)\]]
  } else {
    box(width: 3cm, height: 0.9em, stroke: (bottom: 0.5pt + luma(120)))
  }
}

#let upright-families = data.at("uprightFamilies", default: ()).map(lower)

#let italic-fallback(body) = context {
  set text(font: text.font.filter(family => lower(family) not in upright-families))
  body
}

#let caption(body) = block(
  width: 100%,
  above: 0.5em,
  below: 1.2em,
  italic-fallback(text(size: 8.5pt, fill: luma(90), style: "italic", body)),
)

#let fit(it, max-height) = layout(region => {
  let natural = measure(it)
  if natural.width == 0pt or natural.height == 0pt { return it }
  let scale = calc.min(1, region.width / natural.width, max-height / natural.height)
  block(width: natural.width * scale, breakable: false, it)
})

#let font-span(family, body) = {
  let names = data.spanFonts.at(family, default: none)
  if names == none { body } else { text(font: names, body) }
}

#let text-block(c, max-height) = [
  #show image: it => fit(it, max-height)
  #eval(
    read(c.file),
    mode: "markup",
    scope: (
      ph: placeholder,
      font: font-span,
      horizontalrule: line(length: 100%, stroke: 0.5pt + luma(180)),
      caption: caption,
    ),
  )
]

#let picture(c, max-height) = {
  let limit = if c.at("maxHeightMm", default: none) == none { max-height } else { c.maxHeightMm * 1mm }
  align(center, fit(image(c.file), limit))
}

#let rule = 0.5pt + luma(150)

#let divider(c) = pad(y: 0.4em, if c.label == "" {
  line(length: 100%, stroke: rule)
} else {
  grid(
    columns: (1fr, auto, 1fr),
    column-gutter: 0.6em,
    align: horizon,
    line(length: 100%, stroke: rule),
    text(size: 0.8em, fill: luma(90), upper(c.label)),
    line(length: 100%, stroke: rule),
  )
})

#let signer-caption(c, index) = {
  let caption = c.at("captions", default: ()).at(index, default: "")
  if caption != "" { block(above: 0.3em, below: 0pt, text(size: 0.8em, caption)) }
}

#let signature(c, max-height) = grid(
  columns: c.fields.len() * (1fr,),
  column-gutter: 1.2em,
  ..c.fields.enumerate().map(((index, field)) => [
    #box(
      width: 100%,
      height: 1.6cm,
      link(data.signatureMarker + field, box(width: 100%, height: 100%, stroke: (bottom: 0.6pt + black))),
    )
    #signer-caption(c, index)
    #block(above: 0.3em, text(size: 0.85em, text-block(c, max-height)))
  ]),
)

#let fill-in(c) = grid(
  columns: c.fields.len() * (1fr,),
  column-gutter: 1.2em,
  ..c.fields.map(field => [
    #block(below: 0.25em, text(size: 0.85em, c.label + if c.required { " *" } else { "" }))
    #box(
      width: 100%,
      height: 0.8cm,
      link(data.fillInMarker + field, box(width: 100%, height: 100%, stroke: (bottom: 0.6pt + black))),
    )
  ]),
)

#let gutter = 0.8em

#let upright(c) = c.kind == "divider" and c.at("vertical", default: false)

#let rows(list, max-height, gap) = for (index, r) in list.enumerate() {
  let lines = r.at("lines", default: false)
  let drawn(c) = if c.kind == "text" {
    text-block(c, max-height)
  } else if c.kind == "image" {
    picture(c, max-height)
  } else if c.kind == "divider" {
    divider(c)
  } else if c.kind == "spacer" {
    block(height: c.heightMm * 1mm)
  } else if c.kind == "signature" {
    signature(c, max-height)
  } else if c.kind == "fillIn" {
    fill-in(c)
  } else if c.kind == "rows" {
    rows(c.rows, max-height, gap)
  } else {
    []
  }
  let columns = r.cells.map(c => if upright(c) {
    (
      (width: c.width / 2 * 1fr, body: [], starts: true),
      (width: 0pt, body: grid.cell(stroke: (left: rule), []), starts: false),
      (width: c.width / 2 * 1fr, body: [], starts: false),
    )
  } else {
    ((width: c.width * 1fr, body: drawn(c), starts: true),)
  }).join()
  let last = columns.len() - 1
  block(
    width: 100%,
    above: if index == 0 { 0pt } else { gap },
    below: 0pt,
    grid(
      columns: columns.map(column => column.width),
      column-gutter: if lines { 0pt } else { gutter },
      align: top,
      inset: if lines {
        (x, y) => (left: if x == 0 { 0pt } else { gutter / 2 }, right: if x == last { 0pt } else { gutter / 2 })
      } else { 0pt },
      stroke: if lines { (x, y) => if x > 0 and columns.at(x).starts { (left: rule) } } else { none },
      ..columns.map(column => column.body),
    ),
  )
}

#let letterhead(list, size, font) = if list.len() == 0 { none } else {
  set text(font: font, size: size)
  set par(leading: 0.45em, spacing: 0.6em)
  rows(list, 18mm, 0.4em)
}

#show emph: italic-fallback

#set document(
  title: data.title,
  date: datetime(year: data.date.year, month: data.date.month, day: data.date.day),
)
#let edge-gap = 8mm
#let left = data.page.marginLeftMm * 1mm
#let right = data.page.marginRightMm * 1mm
#let header = letterhead(data.header, 8.5pt, data.fonts.header)
#let footer = letterhead(data.footer, 7.5pt, data.fonts.footer)

#let reach(edge, width) = if edge == none { 0pt } else {
  measure(block(width: width, edge)).height + edge-gap
}

#set page(paper: "a4", margin: (left: left, right: right))
#set text(font: data.fonts.body, size: data.page.fontSizePt * 1pt, lang: data.language)
#set par(justify: false, leading: 0.65em)
#show link: set text(fill: rgb("#c71100"))

#context {
  let width = page.width - left - right
  set page(
    margin: (
      top: data.page.marginTopMm * 1mm + reach(header, width),
      bottom: data.page.marginBottomMm * 1mm + reach(footer, width),
      left: left,
      right: right,
    ),
    header: header,
    header-ascent: edge-gap,
    footer: footer,
    footer-descent: edge-gap,
  )
  rows(data.body, 9cm, 1em)
}
