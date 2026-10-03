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

#let text-block(c, max-height) = [
  #show image: it => fit(it, max-height)
  #eval(
    read(c.file),
    mode: "markup",
    scope: (
      ph: placeholder,
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

#let signature(c, max-height) = grid(
  columns: c.fields.len() * (1fr,),
  column-gutter: 1.2em,
  ..c.fields.map(field => [
    #box(
      width: 100%,
      height: 1.6cm,
      link(data.signatureMarker + field, box(width: 100%, height: 100%, stroke: (bottom: 0.6pt + black))),
    )
    #block(above: 0.3em, text(size: 0.85em, text-block(c, max-height)))
  ]),
)

#let gutter = 0.8em

#let rows(list, max-height, gap) = for (index, r) in list.enumerate() {
  let lines = r.at("lines", default: false)
  let last = r.cells.len() - 1
  block(
    width: 100%,
    above: if index == 0 { 0pt } else { gap },
    below: 0pt,
    grid(
      columns: r.cells.map(c => c.width * 1fr),
      column-gutter: if lines { 0pt } else { gutter },
      inset: if lines {
        (x, y) => (left: if x == 0 { 0pt } else { gutter / 2 }, right: if x == last { 0pt } else { gutter / 2 })
      } else { 0pt },
      stroke: if lines { (x, y) => if x > 0 { (left: rule) } } else { none },
      ..r.cells.map(c => if c.kind == "text" {
        text-block(c, max-height)
      } else if c.kind == "image" {
        picture(c, max-height)
      } else if c.kind == "divider" {
        divider(c)
      } else if c.kind == "spacer" {
        block(height: c.heightMm * 1mm)
      } else if c.kind == "signature" {
        signature(c, max-height)
      } else if c.kind == "rows" {
        rows(c.rows, max-height, gap)
      } else {
        []
      }),
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
#set page(
  paper: "a4",
  margin: (
    top: data.page.marginTopMm * 1mm,
    bottom: data.page.marginBottomMm * 1mm,
    left: data.page.marginLeftMm * 1mm,
    right: data.page.marginRightMm * 1mm,
  ),
  header: letterhead(data.header, 8.5pt, data.fonts.header),
  header-ascent: 12%,
  footer: letterhead(data.footer, 7.5pt, data.fonts.footer),
  footer-descent: 12%,
)
#set text(font: data.fonts.body, size: data.page.fontSizePt * 1pt, lang: "en")
#set par(justify: false, leading: 0.65em)
#show link: set text(fill: rgb("#c71100"))

#rows(data.body, 9cm, 1em)
