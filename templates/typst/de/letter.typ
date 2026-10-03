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

#let cell(c) = {
  let body = if c.kind == "image" {
    image(c.file, height: c.heightMm * 1mm)
  } else if c.kind == "text" {
    c.lines.map(line => text(line)).join(linebreak())
  } else {
    []
  }
  let where = if c.align == "center" { center } else if c.align == "right" { right } else { left }
  align(where + horizon, body)
}

#let row(cells, size) = if cells.len() == 0 { none } else {
  set text(size: size)
  set par(leading: 0.45em)
  grid(columns: (1fr,) * cells.len(), column-gutter: 0.8em, ..cells.map(cell))
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
  header: row(data.header, 8.5pt),
  header-ascent: 12%,
  footer: row(data.footer, 7.5pt),
  footer-descent: 12%,
)
#set text(font: "Liberation Sans", size: data.page.fontSizePt * 1pt, lang: "de")
#set par(justify: false, leading: 0.65em)
#show link: set text(fill: rgb("#c71100"))

#let picture-max-height = 9cm
#let fit-picture(it) = layout(region => {
  let natural = measure(it)
  if natural.width == 0pt or natural.height == 0pt { return it }
  let scale = calc.min(1, region.width / natural.width, picture-max-height / natural.height)
  block(width: natural.width * scale, breakable: false, it)
})
#let caption(body) = block(
  width: 100%,
  above: 0.5em,
  below: 1.2em,
  text(size: 8.5pt, fill: luma(90), style: "italic", body),
)

#[
  #show image: fit-picture
  #eval(
    read("body.typ"),
    mode: "markup",
    scope: (
      ph: placeholder,
      horizontalrule: line(length: 100%, stroke: 0.5pt + luma(180)),
      caption: caption,
    ),
  )
]
