#let data = json("data.json")

#set page(
  paper: "a4",
  margin: 2cm,
  footer: context {
    let page-num = here().page()
    let page-total = counter(page).final().first()
    align(center)[
      #text(size: 7pt, fill: luma(150))[
        GDPR Data Export · Exported at #data.exportedAt · Page #page-num of #page-total
      ]
    ]
  },
)
#set text(font: "Liberation Sans", size: 10pt)

#align(center)[
  #text(size: 16pt, weight: "bold")[Data Export (GDPR)]
  #v(0.3em)
  #text(size: 9pt, fill: luma(100))[Exported at #data.exportedAt]
]

#v(1em)

// Account section
#if "account" in data [
  = Account Information

  #table(
    columns: (auto, 1fr),
    stroke: 0.5pt + luma(200),
    inset: 6pt,
    [*Email*], [#data.account.email],
    [*First name*], [#data.account.firstName],
    [*Last name*], [#data.account.lastName],
    [*Email verified*], [#if data.account.emailVerified [Yes] else [No]],
  )
  #v(0.5em)
]

// Consent records
#if "consentRecords" in data and data.consentRecords.len() > 0 [
  = Consent Records

  #table(
    columns: (1fr, auto, auto),
    stroke: 0.5pt + luma(200),
    inset: 6pt,
    [*Timestamp*], [*Version*], [*Country*],
    ..data.consentRecords.map(r => (
      r.consented_at, str(r.consent_version), r.at("country", default: "—")
    )).flatten()
  )
  #v(0.5em)
]

// Station memberships
#if "stationMemberships" in data [
  #for station in data.stationMemberships [
    = Membership: #station.stationName

    #if "roles" in station and station.roles.len() > 0 [
      *Roles:* #station.roles.map(r => r.role_name).join(", ")
      #v(0.3em)
    ]

    #if "profileFields" in station and station.profileFields.len() > 0 [
      == Profile Data
      #table(
        columns: (auto, 1fr),
        stroke: 0.5pt + luma(200),
        inset: 6pt,
        ..station.profileFields.map(f => (f.field_name, str(f.at("value", default: "—")))).flatten()
      )
      #v(0.3em)
    ]

    #if "inventoryItems" in station and station.inventoryItems.len() > 0 [
      == Inventory
      #table(
        columns: (1fr, auto, auto),
        stroke: 0.5pt + luma(200),
        inset: 6pt,
        [*Item*], [*Inventory*], [*Internal ID*],
        ..station.inventoryItems.map(i => (
          i.at("item_name", default: "—"),
          i.at("inventory_name", default: "—"),
          i.at("internal_id", default: "—"),
        )).flatten()
      )
      #v(0.3em)
    ]

    #if "attendance" in station and station.attendance.len() > 0 [
      == Attendance (#station.attendance.len() entries)
      #text(size: 8pt, fill: luma(100))[Full data included in JSON export.]
      #v(0.3em)
    ]
  ]
]
