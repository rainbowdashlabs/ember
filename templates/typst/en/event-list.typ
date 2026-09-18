#let data = json("data.json")

#set page(
  paper: "a4",
  flipped: data.columns.len() > 4,
  margin: 1.5cm,
  footer: context {
    let page-num = here().page()
    let page-total = counter(page).final().first()
    align(center)[
      #text(size: 7pt, fill: luma(150))[
        Created by #data.generatedBy on #data.generatedAt · Page #page-num of #page-total#if data.at("showInstanceUrl", default: true) [ · #link(data.baseUrl)[#data.baseUrl]]
      ]
    ]
  },
)
#set text(font: "Liberation Sans", size: 9pt)

#if data.hasLogo and data.stationName != "" [
  #align(center)[
    #grid(
      columns: (auto, auto),
      column-gutter: 0.5em,
      align: (right + horizon, left + horizon),
      image(data.logoFile, height: 1.5cm),
      text(size: 12pt, weight: "bold")[#data.stationName],
    )
  ]
  #v(0.3em)
] else if data.hasLogo [
  #align(center)[#image(data.logoFile, height: 1.5cm)]
  #v(0.3em)
] else if data.stationName != "" [
  #align(center)[#text(size: 12pt, weight: "bold")[#data.stationName]]
  #v(0.3em)
]

#align(center)[
  #text(size: 14pt, weight: "bold")[Appointments]
]

#v(0.3em)

#align(center)[
  #text(size: 9pt, fill: luma(100))[
    #data.dateRange
  ]
]

#v(1em)

#for category in data.categories [
  #if category.name != "" [
    #text(size: 11pt, weight: "bold")[#category.name]
    #v(0.4em)
  ]

  #let col-widths = ()
  #for _ in data.columns {
    col-widths = col-widths + (1fr,)
  }

  #table(
    columns: col-widths,
    stroke: 0.5pt + luma(180),
    inset: 5pt,
    align: center + horizon,
    ..for col in data.columns {
      (table.cell(fill: luma(230))[*#col*],)
    },
    ..for event in category.events {
      for val in event.values {
        (align(left)[#val],)
      }
    },
  )

  #v(1em)
]
