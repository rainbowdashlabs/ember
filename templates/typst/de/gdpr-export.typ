#let data = json("data.json")

#set page(
  paper: "a4",
  margin: 2cm,
  footer: context {
    let page-num = here().page()
    let page-total = counter(page).final().first()
    align(center)[
      #text(size: 7pt, fill: luma(150))[
        DSGVO-Datenauskunft · Exportiert am #data.exportedAt · Seite #page-num von #page-total
      ]
    ]
  },
)
#set text(font: "Liberation Sans", size: 10pt)

#align(center)[
  #text(size: 16pt, weight: "bold")[Datenauskunft (DSGVO)]
  #v(0.3em)
  #text(size: 9pt, fill: luma(100))[Exportiert am #data.exportedAt]
]

#v(1em)

// Account section
#if "account" in data [
  = Kontoinformationen

  #table(
    columns: (auto, 1fr),
    stroke: 0.5pt + luma(200),
    inset: 6pt,
    [*E-Mail*], [#data.account.email],
    [*Vorname*], [#data.account.firstName],
    [*Nachname*], [#data.account.lastName],
    [*E-Mail bestätigt*], [#if data.account.emailVerified [Ja] else [Nein]],
  )
  #v(0.5em)
]

// Consent records
#if "consentRecords" in data and data.consentRecords.len() > 0 [
  = Einwilligungsnachweise

  #table(
    columns: (1fr, auto, auto),
    stroke: 0.5pt + luma(200),
    inset: 6pt,
    [*Zeitpunkt*], [*Version*], [*Land*],
    ..data.consentRecords.map(r => (
      r.consented_at, str(r.consent_version), r.at("country", default: "-")
    )).flatten()
  )
  #v(0.5em)
]

// Station memberships
#if "stationMemberships" in data [
  #for station in data.stationMemberships [
    = Mitgliedschaft: #station.stationName

    #if "roles" in station and station.roles.len() > 0 [
      *Rollen:* #station.roles.map(r => r.role_name).join(", ")
      #v(0.3em)
    ]

    #if "profileFields" in station and station.profileFields.len() > 0 [
      == Profildaten
      #table(
        columns: (auto, 1fr),
        stroke: 0.5pt + luma(200),
        inset: 6pt,
        ..station.profileFields.map(f => (f.field_name, str(f.at("value", default: "-")))).flatten()
      )
      #v(0.3em)
    ]

    #if "inventoryItems" in station and station.inventoryItems.len() > 0 [
      == Inventar
      #table(
        columns: (1fr, auto, auto),
        stroke: 0.5pt + luma(200),
        inset: 6pt,
        [*Gegenstand*], [*Inventar*], [*Interne ID*],
        ..station.inventoryItems.map(i => (
          i.at("item_name", default: "-"),
          i.at("inventory_name", default: "-"),
          i.at("internal_id", default: "-"),
        )).flatten()
      )
      #v(0.3em)
    ]

    #if "attendance" in station and station.attendance.len() > 0 [
      == Anwesenheit (#station.attendance.len() Einträge)
      #text(size: 8pt, fill: luma(100))[Vollständige Daten im JSON-Export enthalten.]
      #v(0.3em)
    ]
  ]
]
