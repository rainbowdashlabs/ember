#let data = json("data.json")

#set page(
  paper: "a4",
  margin: (top: 2.4cm, bottom: 1.6cm, x: 2cm),
  header: [
    #grid(
      columns: (auto, 1fr),
      column-gutter: 0.8em,
      align: (left + horizon, left + horizon),
      if data.hasLogo [ #image(data.logoFile, height: 1.1cm) ] else [],
      text(size: 11pt, weight: "bold")[#data.stationName],
    )
    #v(-0.4em)
    #line(length: 100%, stroke: 0.4pt + luma(180))
  ],
  footer: context {
    let page-num = here().page()
    let page-total = counter(page).final().first()
    align(center)[
      #text(size: 7pt, fill: luma(150))[
        Erstellt von #data.generatedBy am #data.generatedAt · Seite #page-num von #page-total · #link(data.baseUrl)[#data.baseUrl]
      ]
    ]
  },
)
#set text(font: "Liberation Sans", size: 10pt)
#set par(justify: false, leading: 0.65em)
#show link: set text(fill: rgb("#c71100"))
#show raw.where(block: true): block.with(
  fill: luma(245),
  inset: 8pt,
  radius: 3pt,
  width: 100%,
)

#align(center)[
  #text(size: 14pt, weight: "bold")[#data.fileName]
]

#if data.fileDescription != "" [
  #v(0.2em)
  #align(center)[
    #text(size: 10pt, fill: luma(100))[#data.fileDescription]
  ]
]

#v(1em)

// The body is the Typst markup pandoc produced from the file's Markdown. It is evaluated rather
// than included so the helpers pandoc expects resolve in this document's scope.
#eval(
  read("body.typ"),
  mode: "markup",
  scope: (horizontalrule: line(length: 100%, stroke: 0.5pt + luma(180))),
)
