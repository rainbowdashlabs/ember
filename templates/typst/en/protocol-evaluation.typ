#let data = json("data.json")

#let person(name) = if name == none [Unknown] else [#name]
#let score-fill(score, max) = {
  if max == 0 { white } else {
    let ratio = score / max
    if ratio >= 0.9 { rgb("#dcfce7") }
    else if ratio >= 0.6 { rgb("#fef9c3") }
    else if ratio >= 0.3 { rgb("#ffedd5") }
    else { rgb("#fee2e2") }
  }
}
#let score-cell(cell, bold) = table.cell(
  fill: score-fill(cell.score, cell.max),
  if bold [*#cell.label*] else [#cell.label],
)
#let row-cells(row) = {
  if row.kind == "DETAIL" {
    (
      [#h(0.5em * row.depth)#text(size: 6.5pt)[#row.name]],
      [#text(size: 6.5pt)[#row.max]],
    ) + row.cells.map(cell => score-cell(cell, false))
  } else {
    let fill = if row.kind == "TOTAL" { luma(210) } else { luma(235) }
    let name = if row.kind == "TOTAL" [Total] else [#row.name]
    let cells = (
      table.cell(fill: fill)[*#name*],
      table.cell(fill: fill)[*#row.max*],
    ) + row.cells.map(cell => score-cell(cell, true))
    if row.kind == "SECTION" { cells + (table.hline(stroke: 0.8pt),) } else { cells }
  }
}

#set document(title: data.protocolName)
#set page(paper: "a4", flipped: true, margin: 1cm)
#set text(font: "Liberation Sans", size: 7pt, lang: "en")

#align(center)[
  #if data.hasLogo [
    #box(image(data.logoFile, width: 1cm)) #h(0.5em) #text(size: 9pt, weight: "bold")[#data.stationName]
    #v(0.3em)
  ]
  #text(size: 11pt, weight: "bold")[#data.protocolName - Evaluation]
  #v(0.2em)
  #text(size: 8pt)[Date: #data.testDate]
]
#v(0.3em)

#table(
  columns: (auto, 3em, 3em) + data.members.map(_ => 1fr),
  align: (left, center, center) + data.members.map(_ => center),
  stroke: 0.3pt + luma(180),
  table.header([*Topic*], [*Max*], [*Ø*], ..data.members.map(member => [*#person(member)*])),
  ..data.rows.map(row-cells).flatten(),
)
