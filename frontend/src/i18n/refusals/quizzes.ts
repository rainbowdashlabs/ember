/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    CHANGE_SAVED_BUT_NOT_READ_BACK,
    NOT_HERE_OR_NOT_YOURS,
    UPLOAD_WITHOUT_FILE,
    UPLOAD_NOT_PROCESSED,
    KB_PICTURE_KIND_NOT_TAKEN,
    CATEGORY_NOT_HERE,
} from './shared'

const ATTEMPT_NOT_HERE = 'Diesen Versuch gibt es nicht mehr'
const CATALOG_NOT_HERE = 'Diesen Katalog gibt es nicht mehr'
const PUBLIC_STATION_NOT_REACHED = 'Dieser Link führt zu keiner Wache'
const PUBLIC_CATALOGS_NOT_NAMED = 'Dieser Link nennt keinen Katalog, aus dem eine Frage gezogen werden kann'
const QUIZ_NOTE_NOT_HERE = 'Diese Meldung zu einer Frage gibt es nicht mehr'
const QUESTION_NOT_HERE_ON_WRITE = 'Diese Frage gibt es nicht mehr, es wurde nichts geändert'
const TEST_NOT_HERE = 'Diesen Test gibt es nicht mehr'
const QUIZ_CATALOG_EXAMPLE_NOT_READ = 'Die Beispieltabelle konnte nicht gelesen werden. Das ist ein Fehler in Ember, '
    + 'bitte melde ihn'
const QUIZ_CATALOG_FILE_NOT_ONE = 'Diese Datei ist kein Katalogexport, es wurde nichts importiert'

/** What the refusals of quizzes say in German, keyed by their `Q-` code. */
export default {
    'Q-001': 'Diese Arbeit wurde bereits abgegeben, die Antwort wurde nicht gespeichert. '
        + 'Lade die Seite neu, um den aktuellen Stand zu sehen',
    'Q-002': 'Diese Arbeit konnte nicht abgegeben werden: sie ist bereits abgegeben, '
        + 'oder die Zeit dafür ist abgelaufen. Lade die Seite neu, um den aktuellen Stand zu sehen',
    'Q-003': ATTEMPT_NOT_HERE,
    'Q-004': NOT_HERE_OR_NOT_YOURS,
    'Q-005': ATTEMPT_NOT_HERE,
    'Q-006': ATTEMPT_NOT_HERE,
    'Q-007': 'Dieser Versuch gehört jemand anderem',
    'Q-008': CATALOG_NOT_HERE,
    'Q-009': CATALOG_NOT_HERE,
    'Q-010': CATALOG_NOT_HERE,
    'Q-011': CATALOG_NOT_HERE,
    'Q-012': CATEGORY_NOT_HERE,
    'Q-013': CATEGORY_NOT_HERE,
    'Q-014': 'In diesem Format gibt es keine Beispieldatei',
    'Q-015': TEST_NOT_HERE,
    'Q-016': TEST_NOT_HERE,
    'Q-017': TEST_NOT_HERE,
    'Q-018': TEST_NOT_HERE,
    'Q-019': TEST_NOT_HERE,
    'Q-020': TEST_NOT_HERE,
    'Q-021': 'Die Kataloge in diesem Link müssen jeweils mit einer Nummer benannt sein',
    'Q-022': PUBLIC_STATION_NOT_REACHED,
    'Q-023': PUBLIC_STATION_NOT_REACHED,
    'Q-024': PUBLIC_CATALOGS_NOT_NAMED,
    'Q-025': PUBLIC_CATALOGS_NOT_NAMED,
    'Q-026': 'Aus diesen Katalogen gibt es hier keine Frage zu zeigen',
    'Q-027': 'Dieser Test läuft gerade, es wurde nichts geändert',
    'Q-029': 'Ein Katalog braucht einen Namen, es wurde nichts gespeichert',
    'Q-030': 'Eine Kategorie braucht einen Namen, es wurde nichts gespeichert',
    'Q-031': 'Dieser Katalog ist nicht zum Üben freigegeben',
    'Q-032': 'Die Tabelle kam leer an, es wurde nichts daraus gelesen',
    'Q-033': 'Sag, welche Spalte was enthält, es wurde nichts daraus gelesen',
    'Q-034': QUIZ_NOTE_NOT_HERE,
    'Q-035': QUIZ_NOTE_NOT_HERE,
    'Q-036': 'Eine Frage braucht einen Titel, es wurde nichts gespeichert',
    'Q-037': 'Eine Frage braucht eine Art, es wurde nichts gespeichert',
    'Q-038': QUESTION_NOT_HERE_ON_WRITE,
    'Q-039': 'Die Änderung an dieser Frage wurde gespeichert, sie konnte aber nicht zurückgelesen werden. '
        + 'Lade die Seite neu, um den aktuellen Stand zu sehen',
    'Q-040': QUESTION_NOT_HERE_ON_WRITE,
    'Q-041': 'Zu dieser Frage gibt es kein Bild',
    'Q-042': UPLOAD_WITHOUT_FILE,
    'Q-043': KB_PICTURE_KIND_NOT_TAKEN,
    'Q-044': 'Dieses Bild konnte nicht gespeichert werden, die Frage behält das bisherige',
    'Q-045': UPLOAD_NOT_PROCESSED,
    'Q-047': 'Dieser Test ist für dich gerade nicht offen, es wurde kein Versuch begonnen',
    'Q-049': 'Diese Antwort gibt es nicht mehr',
    'Q-050': 'Gib die Punkte für diese Antwort an, es wurde keine Bewertung gespeichert',
    'Q-052': 'Die Bewertung wurde gespeichert, der Versuch konnte aber nicht zurückgelesen werden. '
        + 'Lade die Seite neu, um den aktuellen Stand zu sehen',
    'Q-054': 'Ein Test braucht einen Titel, es wurde nichts gespeichert',
    'Q-055': 'Dieser Test wurde bereits gestartet, es wurde nichts geändert',
    'Q-056': 'Dieser Test läuft gerade, seine Fragen lassen sich nicht neu ziehen',
    'Q-057': 'Nenne das Mitglied, für das dieser Test geöffnet werden soll, es wurde nichts gespeichert',
    'Q-058': 'Dieser Test konnte nicht als PDF erstellt werden. Versuch es noch einmal',
    'Q-059': 'Der Lösungsbogen konnte nicht als PDF erstellt werden. Versuch es noch einmal',
    'Q-060': 'Gib den Schlüssel für diesen Anbieter an, es wurde nichts gespeichert',
    'Q-061': 'Die Modelle konnten nicht aufgelistet werden: prüfe den Schlüssel und den Anbieter',
    'Q-062': 'Die Modelle konnten nicht aufgelistet werden. Versuch es noch einmal',
    'Q-063': 'Gib die Frage an, mit der gearbeitet werden soll, es wurde nichts generiert',
    'Q-064': 'Gib die richtige Antwort an, mit der gearbeitet werden soll, es wurde nichts generiert',
    'Q-065': 'Daraus konnte nichts generiert werden: prüfe den Schlüssel, das Modell und das Gewünschte',
    'Q-066': 'Diesmal konnte nichts generiert werden. Versuch es noch einmal',
    'Q-067': 'Sag, was und wie viel generiert werden soll, es wurde nichts generiert',
    'Q-068': 'Diese Generierung gibt es nicht mehr, ihre Fragen lassen sich nicht mehr abholen',
    'Q-069': 'Unter dieser Nummer ist hier kein Katalog mit dir geteilt',
    'Q-070': 'Diesen KI-Anbieter kann diese Instanz nicht verwenden',
    'Q-071': 'Gib den Schlüssel für diesen Anbieter ein, es wurde nichts gespeichert',
    'Q-072': CHANGE_SAVED_BUT_NOT_READ_BACK,
    'Q-073': CATALOG_NOT_HERE,
    'Q-074': QUIZ_CATALOG_EXAMPLE_NOT_READ,
    'Q-075': QUIZ_CATALOG_EXAMPLE_NOT_READ,
    'Q-076': QUIZ_CATALOG_FILE_NOT_ONE,
    'Q-077': QUIZ_CATALOG_FILE_NOT_ONE,
    'Q-078': QUIZ_CATALOG_FILE_NOT_ONE,
    'Q-079': 'Sag, was an der Frage nicht stimmt, es wurde nichts gesendet',
    'Q-080': 'Die Tabelle ließ sich nicht lesen, es wurde nichts importiert',
    'Q-081': 'Eine Zeile nennt eine Fragenart, die Ember nicht kennt, es wurde nichts importiert',
    'Q-082': 'Die Tabelle hat keine Spalte mit dem Namen, der für die Fragen gewählt wurde, es wurde nichts importiert',
}
