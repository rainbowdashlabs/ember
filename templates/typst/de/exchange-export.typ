#let data = json("data.json")

#set page(
  paper: "a4",
  flipped: true,
  margin: 1.5cm,
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
#set text(font: "Liberation Sans", size: 9pt)

// Header with logo and station name
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
  #text(size: 14pt, weight: "bold")[Tausch-Anfragen]
]

#v(0.5em)

#align(center)[
  #text(size: 9pt, fill: luma(100))[
    Erstellt am #data.generatedAt
  ]
]

#v(1em)

// Column widths: name cols auto, field cols auto, inventory cols 1fr each (alt + neu)
#let col-widths = (auto, auto)
#for _ in data.extraFields {
  col-widths = col-widths + (auto,)
}
#for _ in data.inventoryColumns {
  col-widths = col-widths + (1fr, 1fr)
}

#table(
  columns: col-widths,
  stroke: 0.5pt + luma(180),
  inset: 5pt,
  align: center + horizon,
  // Header row 1: name + fields + inventory names (spanning 2 cols each)
  table.cell(rowspan: 2, fill: luma(230))[*Nachname*],
  table.cell(rowspan: 2, fill: luma(230))[*Vorname*],
  ..for field in data.extraFields {
    (table.cell(rowspan: 2, fill: luma(230))[*#field*],)
  },
  ..for inv in data.inventoryColumns {
    (table.cell(colspan: 2, fill: luma(210))[*#inv*],)
  },
  // Header row 2: alt/neu sub-headers for each inventory
  ..for _ in data.inventoryColumns {
    (
      table.cell(fill: luma(230))[alt],
      table.cell(fill: luma(230))[neu],
    )
  },
  // Data rows
  ..for row in data.rows {
    let cells = (
      align(left)[#row.lastName],
      align(left)[#row.firstName],
    )
    for val in row.extraFieldValues {
      cells = cells + (align(left)[#val],)
    }
    for exchange in row.exchanges {
      cells = cells + (
        text(size: 8pt)[#exchange.oldSize],
        text(size: 8pt)[#exchange.newSize],
      )
    }
    cells
  },
)
