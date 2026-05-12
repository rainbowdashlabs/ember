#let data = json("data.json")

#set page(
  paper: "a4",
  margin: 2cm,
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
  #text(size: 16pt, weight: "bold")[#data.title]
]

#v(0.5em)

#align(center)[
  #text(size: 10pt, fill: luma(100))[
    #data.startTime – #data.endTime
  ]
]

#v(1em)

// Session fields
#if data.fields.len() > 0 [
  #table(
    columns: (auto, 1fr),
    stroke: 0.5pt + luma(180),
    inset: 6pt,
    ..for field in data.fields {
      (text(weight: "bold")[#field.name], field.value)
    }
  )
  #v(1em)
]

// Summary
#let present = data.entries.filter(e => e.status == "PRESENT").len()
#let absent = data.entries.filter(e => e.status == "ABSENT").len()
#let declined = data.entries.filter(e => e.status == "DECLINED").len()
#let unconfirmed = data.entries.filter(e => e.status == "UNCONFIRMED").len()

#text(size: 9pt, fill: luma(100))[
  Anwesend: #present · Abwesend: #absent · Abgemeldet: #declined · Offen: #unconfirmed · Gesamt: #data.entries.len()
]

#v(0.5em)

// Attendance by group
#for section in data.sections [
  #v(0.5em)
  #text(size: 11pt, weight: "bold")[#section.name]
  #v(0.3em)

  #table(
    columns: (auto, 1fr, auto, auto, auto),
    stroke: 0.5pt + luma(180),
    inset: 6pt,
    align: (center, left, center, center, center),
    table.header(
      [], [*Name*], [*Status*], [*Von*], [*Bis*],
    ),
    ..for (i, entry) in section.entries.enumerate() {
      let status = if entry.status == "PRESENT" {
        text(fill: rgb("#00C507"))[Anwesend]
      } else if entry.status == "ABSENT" {
        text(fill: rgb("#ec2929"))[Abwesend]
      } else if entry.status == "DECLINED" {
        text(fill: rgb("#3694FF"))[Abgemeldet]
      } else {
        text(fill: luma(150))[Offen]
      }
      (str(i + 1), entry.name, status, entry.checkIn, entry.checkOut)
    }
  )
]
