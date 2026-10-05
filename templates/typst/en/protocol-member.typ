#let data = json("data.json")

#let person(name) = if name == none [Unknown] else [#name]
#let tick(checked) = if checked {
  box(stroke: 0.5pt, width: 10pt, height: 10pt, align(center + horizon)[#text(size: 7pt)[✓]])
} else {
  box(stroke: 0.5pt, width: 10pt, height: 10pt)[]
}

#let render-section(section, depth) = {
  if depth == 0 {
    v(0.4em)
    line(length: 100%, stroke: 0.5pt)
    table(
      columns: (1fr, auto, auto),
      stroke: none,
      inset: 3pt,
      text(size: 11pt, weight: "bold")[#section.name],
      text(size: 8pt)[Examiner: #if section.testers.len() == 0 [-] else { section.testers.map(person).join([, ]) }],
      align(right)[#text(weight: "bold")[#section.score \/ #section.max pt]],
    )
  } else {
    v(0.2em)
    table(
      columns: (1fr, auto),
      stroke: none,
      inset: 2pt,
      text(size: 9pt, weight: "bold")[#section.name],
      align(right)[#section.score \/ #section.max pt],
    )
  }
  for item in section.items {
    [#tick(item.checked) #item.label #h(1fr) #if item.bonus [+]#item.points pt]
    parbreak()
  }
  for child in section.children {
    render-section(child, depth + 1)
  }
}

#set document(title: data.protocolName)
#set page(paper: "a4", flipped: true, margin: 1.5cm, columns: 2)
#set text(font: "Liberation Sans", size: 9pt, lang: "en")

#align(center)[
  #if data.hasLogo [
    #grid(
      columns: (auto, 1fr),
      gutter: 0.5em,
      align: (center, left),
      image(data.logoFile, width: 1.2cm),
      align(horizon)[#text(size: 10pt, weight: "bold")[#data.stationName]],
    )
    #v(0.3em)
  ]
  #text(size: 14pt, weight: "bold")[#data.protocolName]
  #v(0.3em)
  Name: #strong(person(data.memberName)) #h(2em) Date: #data.testDate
  #v(0.5em)
]

#for section in data.sections {
  render-section(section, 0)
  parbreak()
}

#v(0.5em)
#line(length: 100%)
#text(size: 11pt, weight: "bold")[Total: #data.totalScore \/ #data.totalMax points]

#v(1.5em)
Signature: #box(width: 6cm, stroke: (bottom: 0.5pt))[]
