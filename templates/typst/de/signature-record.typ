#let data = json("data.json")
#let r = data.record

#set document(title: "Signaturnachweis", author: r.station)
#set text(font: "Liberation Sans", size: 9.5pt, lang: "de")
#set page(
  paper: "a4",
  margin: 2cm,
  footer: context align(center, text(size: 7pt, fill: luma(90))[
    Signaturnachweis · Anfrage #r.requestUid · Seite #counter(page).display()
  ]),
)
#counter(page).update(r.firstPage)

#let mono(lines) = text(font: "DejaVu Sans Mono", size: 8.5pt, lines.join(linebreak()))

#let facts(..rows) = table(
  columns: (4.2cm, 1fr),
  stroke: 0.5pt + luma(200),
  inset: 5pt,
  table.header([*Angabe*], [*Wert*]),
  ..rows.pos().flatten(),
)

#let role-label(f) = {
  if f.role == "PARTICIPANT" [Teilnehmende Person]
  else if f.role == "GUARDIAN" [Erziehungsberechtigte Person #if f.place != none [#f.place]]
  else if f.role == "ANY_GUARDIAN" [Eine erziehungsberechtigte Person]
  else if f.role == "ISSUER" [Ausstellende Person]
  else [#f.fieldName]
}

#let state-label(f) = {
  if f.state == "SIGNED" [Elektronisch unterschrieben]
  else if f.state == "OPEN" [Noch offen]
  else if f.state == "PAPER_CONFIRMED" [Auf Papier unterschrieben, bestätigt von #f.settledByName am #f.settledAt]
  else if f.state == "WAIVED" [Erlassen von #f.settledByName am #f.settledAt]
  else if f.state == "WITHDRAWN" [Nicht mehr verlangt]
  else [#f.state]
}

#let proof-label(p) = {
  if p == "PASSKEY" [Passkey]
  else if p == "SECURITY_KEY" [Sicherheitsschlüssel]
  else if p == "TOTP" [Code aus einer Authenticator-App]
  else if p == "PASSWORD" [Passwort]
  else [#p]
}

#let stamp-kind(k) = {
  if k == "AT_REGISTRATION" [bei der Registrierung des Schlüssels]
  else if k == "AFTER_REGISTRATION" [nach der Registrierung des Schlüssels]
  else if k == "AT_FIRST_SIGNING" [bei der ersten Unterschrift mit dem Schlüssel]
  else [#k]
}

#let capacity-rows(a) = {
  if a.capacity == "GUARDIAN" {
    ([Handelt], [als erziehungsberechtigte Person für #a.memberName])
  } else if a.capacity == "MEMBER_THROUGH_ACCOUNT" {
    ([Handelt], [selbst, über das Konto von #a.accountHolderName])
  } else {
    ([Handelt], [selbst, über das eigene Konto])
  }
}

#let guardian-rows(a) = {
  if a.guardian == none { return () }
  let g = a.guardian
  (
    [Erziehungsberechtigung],
    [
      Platz #g.place in der Reihenfolge des Mitglieds#if g.linkedAt != none [, verknüpft am #g.linkedAt]#if g.linkedByName != none [ von #g.linkedByName]
    ],
  )
}

#let proof-rows(a) = {
  let rows = (
    [Bestätigt mit], [#proof-label(a.proof)],
    [An das Dokument gebunden],
    if a.bound [Ja: die Bestätigung signiert den Inhalt dieses Dokuments.] else [Nein: die Bestätigung ist nur neben dem Dokument aufgezeichnet.],
  )
  if a.userVerified != none {
    rows += ([Prüfung am Gerät], if a.userVerified [Durchgeführt (PIN, Biometrie oder Gerätesperre)] else [Nicht durchgeführt])
  }
  if a.keyStamp != none {
    rows += (
      [Zeitstempel des Schlüssels],
      if a.keyStamp.stamped [#a.keyStamp.stampedAt, #stamp-kind(a.keyStamp.kind)] else [Keiner],
    )
  }
  rows
}

#let picture-source(s) = {
  if s == "DRAWN" [Beim Unterschreiben gezeichnet]
  else if s == "TYPED" [Beim Unterschreiben als Name getippt]
  else if s == "UPLOADED" [Beim Unterschreiben als Foto oder Scan hochgeladen]
  else if s == "SAVED" [Vorher im Konto gespeichert]
  else [#s]
}

#let picture-rows(a) = {
  if a.picture == none { return () }
  (
    [Unterschriftsbild], [#picture-source(a.picture.source)],
    [Unterschriftsbild (SHA-256)], mono(a.picture.sha256),
  )
}

#let entry-rows(a) = {
  if a.entries.len() == 0 { return () }
  let name(e) = if e.at("label", default: none) == none { e.field } else { e.label }
  ([Eingaben], a.entries.map(e => [#name(e): #e.value]).join(linebreak()))
}

#let act-table(a) = facts(
  [Unterschrieben von], [#a.signerName],
  ..capacity-rows(a),
  ..guardian-rows(a),
  [Erklärung], [#a.statement],
  ..entry-rows(a),
  ..picture-rows(a),
  ..proof-rows(a),
  [Zeitpunkt], [#a.signedAt],
  [Netzadresse (gekürzt)], if a.truncatedIp != none [#a.truncatedIp] else [Nicht bekannt],
  [Browser], if a.userAgent != none [#a.userAgent] else [Nicht bekannt],
)

#let field-table(f) = facts(
  [Stand], [#state-label(f)],
  ..if f.requestedSignerName != none { ([Angefragt bei], [#f.requestedSignerName]) } else { () },
  [Erklärung], [#f.statement],
)

= Signaturnachweis

Dieser Nachweis gehört zum Dokument davor. Er hält fest, wer es wann und womit elektronisch unterschrieben
hat. Die Unterschriften sind einfache elektronische Signaturen. Das Siegel der Wache bestätigt, dass das
Dokument seit dem Versiegeln nicht verändert wurde.

#if r.withdrawal != none [
  #let w = r.withdrawal
  == Widerruf

  Diese Vereinbarung wurde widerrufen. Die Unterschriften unten bleiben als Nachweis dafür, was bis zum
  Widerruf vereinbart war. Ab dem Widerruf gelten sie nicht mehr.

  #facts(
    [Widerrufen von], [#w.withdrawnByName],
    [Handelt], if w.capacity == "GUARDIAN" [als erziehungsberechtigte Person für #w.memberName] else [selbst],
    [Zeitpunkt], [#w.withdrawnAt],
    [Begründung], if w.reason != none [#w.reason] else [Keine angegeben],
    [Netzadresse (gekürzt)], if w.truncatedIp != none [#w.truncatedIp] else [Nicht bekannt],
    [Browser], if w.userAgent != none [#w.userAgent] else [Nicht bekannt],
  )
]

== Dokument

#facts(
  [Wache], [#r.station],
  [Betrifft], [#r.member],
  [Anfrage], [#r.requestUid],
  [Inhalt (SHA-256)], mono(r.contentSha256),
  [Stand vom], [#r.assembledAt],
  [Unterschriften], [#r.signed von #r.fieldCount],
)

Alle Unterschriften beziehen sich auf den Inhalt mit diesem SHA-256-Wert: das Dokument, wie es vor der ersten
Unterschrift erzeugt wurde.

== Unterschriften

#for f in r.fields [
  === #role-label(f)

  #if f.act != none { act-table(f.act) } else { field-table(f) }
]

== Siegel und Zeit

#if r.timeBasis == "TIMESTAMP_SERVICE" [
  Das Siegel trägt einen Zeitstempel eines unabhängigen Zeitstempeldienstes. Er belegt, dass das Dokument
  zu diesem Zeitpunkt in dieser Form bestand.
] else if r.timeBasis == "NO_SERVICE_ANSWERED" [
  Beim Versiegeln hat kein Zeitstempeldienst geantwortet. Alle Zeiten auf dieser Seite und im Siegel stammen
  deshalb nur von der Uhr des Servers. Ein Zeitstempel kann später ergänzt werden.
] else [
  Beim Versiegeln hat diese Installation keine Zeitstempel eingeholt. Alle Zeiten auf dieser Seite und im
  Siegel stammen deshalb nur von der Uhr des Servers. Ein Zeitstempel kann später ergänzt werden.
]

Das Siegel stammt von einem Zertifikat der Wache, ausgestellt von der Zertifizierungsstelle dieser
Installation. Ihr Fingerabdruck (SHA-256) lässt sich mit dem auf den öffentlichen Seiten der Wache
vergleichen:

#mono(r.authorityFingerprint)

Die maschinenlesbaren Nachweise liegen dieser PDF-Datei als Anhang #raw(r.evidenceFile) bei, mit diesem
SHA-256-Wert:

#mono(r.evidenceSha256)

Prüfen lässt sich das Siegel unter #link(r.verifyAddress)[#r.verifyAddress] oder mit einem PDF-Programm, das
Signaturen anzeigt. Browser und Telefone zeigen Siegel meist gar nicht an; dafür gibt es diese Seite.
