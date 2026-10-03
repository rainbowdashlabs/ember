#let data = json("data.json")

#let signature(key) = box(
  width: 6cm,
  height: 1.6cm,
  link(
    "ember-signature:" + key.split(".").at(1),
    box(width: 100%, height: 100%, stroke: (bottom: 0.6pt + black), inset: (bottom: 2pt), {
      if data.showLabels {
        align(bottom + left, text(size: 7pt, fill: luma(120), data.labels.at(key, default: key)))
      }
    }),
  ),
)

#let placeholder(key) = {
  if key.starts-with("signature.") {
    signature(key)
  } else if key in data.values {
    data.values.at(key)
  } else if data.showLabels {
    highlight(fill: luma(225), extent: 1pt)[\[#data.labels.at(key, default: key)\]]
  } else {
    box(width: 3cm, height: 0.9em, stroke: (bottom: 0.5pt + luma(120)))
  }
}

#let caption(body) = block(
  width: 100%,
  above: 0.5em,
  below: 1.2em,
  text(size: 8.5pt, fill: luma(90), style: "italic", body),
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

#let rows(list, max-height, gap) = for (index, r) in list.enumerate() {
  block(
    width: 100%,
    above: if index == 0 { 0pt } else { gap },
    below: 0pt,
    grid(
      columns: r.cells.map(c => c.width * 1fr),
      column-gutter: 0.8em,
      ..r.cells.map(c => if c.kind == "text" {
        text-block(c, max-height)
      } else if c.kind == "image" {
        picture(c, max-height)
      } else if c.kind == "rows" {
        rows(c.rows, max-height, gap)
      } else {
        []
      }),
    ),
  )
}

#let letterhead(list, size) = if list.len() == 0 { none } else {
  set text(size: size)
  set par(leading: 0.45em, spacing: 0.6em)
  rows(list, 18mm, 0.4em)
}

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
  header: letterhead(data.header, 8.5pt),
  header-ascent: 12%,
  footer: letterhead(data.footer, 7.5pt),
  footer-descent: 12%,
)
#set text(font: "Liberation Sans", size: data.page.fontSizePt * 1pt, lang: "de")
#set par(justify: false, leading: 0.65em)
#show link: set text(fill: rgb("#c71100"))

#rows(data.body, 9cm, 1em)
