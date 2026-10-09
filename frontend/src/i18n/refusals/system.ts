/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    UPLOAD_WITHOUT_FILE,
    LOOK_NOT_OFFERED,
    STATION_NOT_HERE,
    TEST_MAIL_RECIPIENT_NOT_AN_ADDRESS,
} from './shared'

const LOCALE_NOT_GOOD = 'Das ist keine Sprache, für die diese Instanz Texte vorhält'
const TRACKED_TABLE_NOT_HERE = 'Dazu führt Data Tracking keinen Eintrag, es wurde nichts geändert'
const DEMO_UPLOADS_OFF = 'Das Hochladen von Dateien ist in der Demo abgeschaltet, es wurde nichts gespeichert'

/** What the refusals of the instance itself say in German, keyed by their `SY-` code. */
export default {
    'SY-001': LOCALE_NOT_GOOD,
    'SY-002': LOCALE_NOT_GOOD,
    'SY-003': LOCALE_NOT_GOOD,
    'SY-004': 'Diese Einstellung liegt außerhalb des erlaubten Bereichs, es wurde nichts gespeichert',
    'SY-005': 'In dieser Sprache wird auf dieser Instanz keine Post verfasst, es wurde nichts gespeichert',
    'SY-006': 'Ein nicht vertrautes Gerät darf eine Sitzung nicht länger halten als ein vertrautes, '
        + 'es wurde nichts gespeichert',
    'SY-007': 'auth.tokenPepper ist bereits gesetzt und wird von hier aus nicht ersetzt',
    'SY-008': 'Gib die Adresse des Dienstes an, der Passwörter gegen bekannte Datenlecks prüft',
    'SY-009': 'Der geheime Schlüssel für den zweiten Faktor ist bereits gesetzt und wird von hier aus nicht ersetzt',
    'SY-010': 'Gib den Namen an, den eine Authenticator-App für diese Instanz zeigen soll, '
        + 'es wurde nichts gespeichert',
    'SY-011': 'Das Verfahren muss SHA1, SHA256 oder SHA512 sein, es wurde nichts gespeichert',
    'SY-012': 'Attestation muss none, indirect oder direct sein, es wurde nichts gespeichert',
    'SY-013': 'Für diese Instanz ist kein Postanbieter eingerichtet, es wurde nichts versendet',
    'SY-014': 'Das ist kein Platz in der Liste der Postanbieter',
    'SY-015': 'Diesen Postanbieter kennt diese Instanz nicht',
    'SY-016': 'Auf dieser Stufe lässt sich das Log nicht führen',
    'SY-017': 'Um den Versand zu beenden, leere die Post-Einstellungen, statt eine leere Liste zu speichern, '
        + 'es wurde nichts geändert',
    'SY-018': 'Diese Datei ließ sich nicht als Dokument lesen, es wurde nichts übernommen',
    'SY-019': 'Gib das Dokument an, entweder als Datei oder als Text, es wurde nichts übernommen',
    'SY-020': 'Das ist keine Art von Rechtsdokument, die hier geführt wird',
    'SY-021': 'Nenne Methode und Pfad des Endpunkts, den du sehen willst',
    'SY-022': TRACKED_TABLE_NOT_HERE,
    'SY-023': TRACKED_TABLE_NOT_HERE,
    'SY-024': 'Das Installationsprogramm hat keine Antworten zum Merken geschickt, es wurde nichts gemerkt',
    'SY-025': 'Mit diesem Code kann das Installationsprogramm keine Antworten mehr abholen',
    'SY-026': 'Schreib auf, was schiefgelaufen ist, bevor du die Meldung abschickst, es wurde nichts gesendet',
    'SY-027': 'Diese Problemmeldung gibt es nicht mehr',
    'SY-028': 'Diese Problemmeldung wurde ohne Bild geschrieben',
    'SY-029': 'Das Bild zu dieser Meldung gibt es nicht mehr',
    'SY-030': 'Dieses Problem gibt es nicht mehr',
    'SY-031': 'Hier gibt es keine Sitemap',
    'SY-032': UPLOAD_WITHOUT_FILE,
    'SY-033': 'Diese Datei ließ sich nicht als Tabelle aus Zeilen und Spalten lesen',
    'SY-034': 'Die Konfigurationsdatei ließ sich nicht schreiben, die Einstellungen bleiben daher wie sie waren',
    'SY-035': LOOK_NOT_OFFERED,
    'SY-036': 'Das ist in der Demo abgeschaltet, es wurde nichts ausgeführt',
    'SY-037': DEMO_UPLOADS_OFF,
    'SY-038': 'Wachen anlegen und entfernen ist in der Demo abgeschaltet, es wurde nichts geändert',
    'SY-039': 'Rollen ändern ist in der Demo abgeschaltet, es wurde nichts geändert',
    'SY-040': 'Einen Sicherheitsschlüssel einrichten ist in der Demo abgeschaltet, es wurde nichts gespeichert',
    'SY-041': 'Einladungen annehmen ist in der Demo abgeschaltet, es wurde nichts ausgeführt',
    'SY-042': 'Die Anmeldung auf einer Warteliste ist in der Demo abgeschaltet, es wurde nichts gespeichert',
    'SY-043': DEMO_UPLOADS_OFF,
    'SY-044': DEMO_UPLOADS_OFF,
    'SY-045': 'Andere Instanzen erreichen ist in der Demo abgeschaltet, es wurde nichts ausgeführt',
    'SY-046': 'Ausleihen bei anderen Wachen ist in der Demo abgeschaltet, es wurde nichts gespeichert',
    'SY-047': 'Anfragen an KI-Anbieter sind in der Demo abgeschaltet, es wurde nichts ausgeführt',
    'SY-048': TEST_MAIL_RECIPIENT_NOT_AN_ADDRESS,
    'SY-049': 'Das Bild konnte nicht abgelegt werden, die Meldung wurde nicht gesendet',
    'SY-050': 'Das Bild ließ sich nicht lesen, die Meldung wurde nicht gesendet',
    'SY-051': 'Das Bild ist leer, die Meldung wurde nicht gesendet',
    'SY-052': 'Das Bild ist zu groß, die Meldung wurde nicht gesendet',
    'SY-053': 'Eine Meldung nimmt nur Bilder als PNG oder WebP an, die Meldung wurde nicht gesendet',
    'SY-054': TRACKED_TABLE_NOT_HERE,
    'SY-055': TRACKED_TABLE_NOT_HERE,
    'SY-056': STATION_NOT_HERE,
    'SY-057': 'Das Tageslimit einer Wache ist mindestens eine Mail, es wurde nichts geändert',
    'SY-058': 'Der Anteil der Wachen ist ein Prozentwert von 0 bis 100, es wurde nichts gespeichert',
}
