#let data = json("data.json")
#let r = data.record

#set document(title: "Signature record", author: r.station)
#set text(font: "Liberation Sans", size: 9.5pt, lang: "en")
#set page(
  paper: "a4",
  margin: 2cm,
  footer: context align(center, text(size: 7pt, fill: luma(90))[
    Signature record · Request #r.requestUid · Page #counter(page).display()
  ]),
)
#counter(page).update(r.firstPage)

#let mono(lines) = text(font: "DejaVu Sans Mono", size: 8.5pt, lines.join(linebreak()))

#let facts(..rows) = table(
  columns: (4.2cm, 1fr),
  stroke: 0.5pt + luma(200),
  inset: 5pt,
  table.header([*Item*], [*Value*]),
  ..rows.pos().flatten(),
)

#let role-label(f) = {
  if f.role == "PARTICIPANT" [Participant]
  else if f.role == "GUARDIAN" [Guardian #if f.place != none [#f.place]]
  else if f.role == "ANY_GUARDIAN" [One guardian]
  else if f.role == "ISSUER" [Issuer]
  else [#f.fieldName]
}

#let state-label(f) = {
  if f.state == "SIGNED" [Signed electronically]
  else if f.state == "OPEN" [Still open]
  else if f.state == "PAPER_CONFIRMED" [Signed on paper, confirmed by #f.settledByName on #f.settledAt]
  else if f.state == "WAIVED" [Waived by #f.settledByName on #f.settledAt]
  else if f.state == "WITHDRAWN" [No longer asked for]
  else [#f.state]
}

#let proof-label(p) = {
  if p == "PASSKEY" [Passkey]
  else if p == "SECURITY_KEY" [Security key]
  else if p == "TOTP" [Code from an authenticator app]
  else if p == "PASSWORD" [Password]
  else [#p]
}

#let stamp-kind(k) = {
  if k == "AT_REGISTRATION" [when the key was registered]
  else if k == "AFTER_REGISTRATION" [after the key was registered]
  else if k == "AT_FIRST_SIGNING" [at the first signature with the key]
  else [#k]
}

#let capacity-rows(a) = {
  if a.capacity == "GUARDIAN" {
    ([Acting], [as guardian of #a.memberName])
  } else if a.capacity == "MEMBER_THROUGH_ACCOUNT" {
    ([Acting], [personally, through the account of #a.accountHolderName])
  } else {
    ([Acting], [personally, through their own account])
  }
}

#let guardian-rows(a) = {
  if a.guardian == none { return () }
  let g = a.guardian
  (
    [Guardianship],
    [
      Place #g.place in the member's order#if g.linkedAt != none [, linked on #g.linkedAt]#if g.linkedByName != none [ by #g.linkedByName]
    ],
  )
}

#let proof-rows(a) = {
  let rows = (
    [Confirmed with], [#proof-label(a.proof)],
    [Bound to the document],
    if a.bound [Yes: the confirmation signs the content of this document.] else [No: the confirmation is only recorded next to the document.],
  )
  if a.userVerified != none {
    rows += ([Check on the device], if a.userVerified [Performed (PIN, biometrics or screen lock)] else [Not performed])
  }
  if a.keyStamp != none {
    rows += (
      [Timestamp of the key],
      if a.keyStamp.stamped [#a.keyStamp.stampedAt, #stamp-kind(a.keyStamp.kind)] else [None],
    )
  }
  rows
}

#let picture-source(s) = {
  if s == "DRAWN" [Drawn when signing]
  else if s == "TYPED" [Typed as a name when signing]
  else if s == "UPLOADED" [Uploaded as a photo or scan when signing]
  else if s == "SAVED" [Saved in the account beforehand]
  else [#s]
}

#let picture-rows(a) = {
  if a.picture == none { return () }
  (
    [Signature picture], [#picture-source(a.picture.source)],
    [Signature picture (SHA-256)], mono(a.picture.sha256),
  )
}

#let entry-rows(a) = {
  if a.entries.len() == 0 { return () }
  let name(e) = if e.at("label", default: none) == none { e.field } else { e.label }
  ([Entries], a.entries.map(e => [#name(e): #e.value]).join(linebreak()))
}

#let batch-rows(a) = {
  if a.batch == none { return () }
  let b = a.batch
  (
    [Confirmed together],
    [
      With one confirmation for #b.size fields, this being field #b.position (batch #b.uid). The other fields:
      #linebreak()
      #b.others.map(o => [Request #o.requestUid, field #raw(o.fieldName)]).join(linebreak())
    ],
  )
}

#let act-table(a) = facts(
  [Signed by], [#a.signerName],
  ..capacity-rows(a),
  ..guardian-rows(a),
  [Statement], [#a.statement],
  ..entry-rows(a),
  ..picture-rows(a),
  ..proof-rows(a),
  ..batch-rows(a),
  [Time], [#a.signedAt],
  [Network address (shortened)], if a.truncatedIp != none [#a.truncatedIp] else [Not known],
  [Browser], if a.userAgent != none [#a.userAgent] else [Not known],
)

#let field-table(f) = facts(
  [State], [#state-label(f)],
  ..if f.requestedSignerName != none { ([Asked of], [#f.requestedSignerName]) } else { () },
  [Statement], [#f.statement],
)

= Signature record

This record belongs to version #r.version of the document "#r.documentTitle" with the SHA-256 value below. It
states who signed it electronically, when and with what. The signatures are simple electronic signatures.
The station's seal on the document confirms that it has not changed since it was sealed. The record was
built from the details the version carries invisibly, and sealed by the station when it was built.

#if r.withdrawal != none [
  #let w = r.withdrawal
  == Withdrawal

  This agreement was withdrawn. The signatures below remain as evidence of what was agreed until the
  withdrawal. From the withdrawal on, they no longer apply.

  #facts(
    [Withdrawn by], [#w.withdrawnByName],
    [Acting], if w.capacity == "GUARDIAN" [as guardian of #w.memberName] else [for themselves],
    [Time], [#w.withdrawnAt],
    [Reason], if w.reason != none [#w.reason] else [None given],
    [Network address (shortened)], if w.truncatedIp != none [#w.truncatedIp] else [Not known],
    [Browser], if w.userAgent != none [#w.userAgent] else [Not known],
  )
]

== Document

#facts(
  [Station], [#r.station],
  [Document], [#r.documentTitle],
  [Concerns], [#r.member],
  [Version], [#r.version, sealed on #r.sealedAt],
  [Version (SHA-256)], mono(r.versionSha256),
  [Request], [#r.requestUid],
  [Content (SHA-256)], mono(r.contentSha256),
  [As of], [#r.assembledAt],
  [Signatures], [#r.signed of #r.fieldCount],
)

Every signature refers to the content with this SHA-256 value: the document as it was generated before the
first signature.

== Signatures

#for f in r.fields [
  === #role-label(f)

  #if f.act != none { act-table(f.act) } else { field-table(f) }
]

== Seal and time

#if r.timeBasis == "TIMESTAMP_SERVICE" [
  The version's seal carries a timestamp from an independent timestamp service. It proves that the document
  existed in this form at that time.
] else if r.timeBasis == "NO_SERVICE_ANSWERED" [
  The version's seal carries no timestamp, because no timestamp service answered when it was sealed. All
  times in this record therefore come only from the server's clock. A timestamp can be added later.
] else [
  When the version was sealed, this installation asked no timestamp service. All times in this record
  therefore come only from the server's clock. A timestamp can be added later.
]

The seals come from certificates of the station, issued by this installation's certificate authority. The
fingerprint (SHA-256) of the authority that issued this record's seal can be compared with the one on the
station's public pages:

#mono(r.authorityFingerprint)

The machine-readable evidence is attached to the version as #raw(r.evidenceFile), with this SHA-256 value:

#mono(r.evidenceSha256)

The seals can be checked at #link(r.verifyAddress)[#r.verifyAddress] or with a PDF program that shows
signatures. Browsers and phones usually do not show seals at all; this record is there for that.
