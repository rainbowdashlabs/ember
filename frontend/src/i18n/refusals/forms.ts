/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    CHANGE_SAVED_BUT_NOT_READ_BACK,
    STATION_NOT_HERE,
    MEMBER_NOT_YOURS,
    NOT_HERE_OR_NOT_YOURS,
} from './shared'

const FORM_NOT_HERE = 'Dieses Formular gibt es nicht mehr, es wurde nichts geändert'
const FORM_NOT_FROM_OUTSIDE = 'Dieses Formular lässt sich nicht von außerhalb der Wache beantworten'
const FORM_KIND_UNKNOWN = 'Das ist keine Art von Formular'
const FORM_TAKES_NO_ANSWERS = 'Dieses Formular nimmt keine Antworten an, es wurde nichts gespeichert'
const FORM_NOT_YOURS_TO_ANSWER = 'Dieses Formular darfst du nicht beantworten'
const FORM_ANSWERS_NOT_SAVED = 'Die Antworten konnten nicht gespeichert werden'
const FORM_ANSWER_NOT_CHANGEABLE = 'Bei diesem Formular lässt sich eine einmal gegebene Antwort nicht mehr ändern'
const INTERNAL_FORM_HAS_NO_LINK = 'Ein Formular für die eigenen Mitglieder der Wache wird nicht per Link verschickt'

/** What the refusals of forms and answers say in German, keyed by their `F-` code. */
export default {
    'F-001': FORM_NOT_HERE,
    'F-002': FORM_NOT_HERE,
    'F-003': FORM_NOT_HERE,
    'F-004': FORM_NOT_HERE,
    'F-005': FORM_NOT_HERE,
    'F-006': FORM_NOT_HERE,
    'F-007': NOT_HERE_OR_NOT_YOURS,
    'F-008': STATION_NOT_HERE,
    'F-009': 'Dieser Link führt zu keinem Formular',
    'F-010': 'Dieses Formular nimmt keine Antworten an',
    'F-011': 'Dieses Formular wurde von hier aus zu oft beantwortet. Versuche es gleich noch einmal',
    'F-012': 'Du hast dieses Formular bereits beantwortet',
    'F-013': 'Die Antworten konnten nicht gespeichert werden',
    'F-014': 'Die Antworten waren nicht lesbar, es wurde nichts gespeichert. '
        + 'Fülle das Formular noch einmal aus und sende es erneut',
    'F-015': STATION_NOT_HERE,
    'F-016': FORM_NOT_HERE,
    'F-017': FORM_NOT_FROM_OUTSIDE,
    'F-018': FORM_NOT_FROM_OUTSIDE,
    'F-019': 'Das Formular, das dieser Abschnitt der Seite zeigt, gibt es nicht mehr',
    'F-020': 'Diese Antwort gibt es nicht mehr',
    'F-021': FORM_KIND_UNKNOWN,
    'F-022': 'Sag, nach welcher Art von Formular gesucht werden soll',
    'F-023': FORM_KIND_UNKNOWN,
    'F-024': 'Ein Formular braucht einen Titel, es wurde nichts gespeichert',
    'F-026': 'Nur ein Formular, das noch ein Entwurf ist, lässt sich veröffentlichen',
    'F-027': 'Sag, wie weit das Formular reichen soll',
    'F-028': INTERNAL_FORM_HAS_NO_LINK,
    'F-029': 'Dieses Formular hat seit deinem letzten Blick einen anderen Link bekommen',
    'F-030': 'Einige dieser Fragen lassen sich in einem Formular dieser Art nicht stellen, '
        + 'es wurde nichts gespeichert',
    'F-033': FORM_TAKES_NO_ANSWERS,
    'F-034': FORM_NOT_YOURS_TO_ANSWER,
    'F-035': FORM_ANSWERS_NOT_SAVED,
    'F-037': FORM_ANSWER_NOT_CHANGEABLE,
    'F-038': FORM_NOT_YOURS_TO_ANSWER,
    'F-039': FORM_ANSWERS_NOT_SAVED,
    'F-041': FORM_TAKES_NO_ANSWERS,
    'F-042': FORM_ANSWER_NOT_CHANGEABLE,
    'F-043': 'Dieses Formular wurde dem Mitglied, für das du antwortest, nicht gestellt',
    'F-044': FORM_ANSWERS_NOT_SAVED,
    'F-045': MEMBER_NOT_YOURS,
    'F-046': 'Nur die Ergebnisse eines internen Formulars lassen sich danach gruppieren, wer geantwortet hat',
    'F-047': 'Die Antworten konnten nicht in eine Datei geschrieben werden',
    'F-048': CHANGE_SAVED_BUT_NOT_READ_BACK,
    'F-049': 'Einige dieser Fragen gehören nicht zu diesem Formular, es wurde nichts gespeichert',
    'F-050': 'Eine Frage, die es schon gibt, behält ihre Art, es wurde nichts gespeichert',
    'F-051': 'Dieses Formular wurde bereits beantwortet, es wurde nichts gespeichert',
    'F-053': 'Jede Option einer Frage braucht einen eigenen Schlüssel, es wurde nichts gespeichert',
    'F-054': 'Ein Formular braucht mindestens eine Seite und jede Seite einen eigenen Schlüssel, es wurde nichts gespeichert',
    'F-055': 'Eine Seite kann nur zu einer Seite weiter unten führen, es wurde nichts gespeichert',
    'F-056': 'Einige dieser Fragen stehen auf einer Seite, die das Formular nicht hat, es wurde nichts gespeichert',
    'F-057': 'Nur eine Frage mit einer Antwort aus ihren eigenen Optionen kann die nächste Seite bestimmen, es wurde nichts gespeichert',
    'F-058': 'Nur eine Frage pro Seite kann die nächste Seite bestimmen, es wurde nichts gespeichert',
    'F-059': 'Diese Frage muss beantwortet werden',
    'F-060': 'Diese Antwort passt nicht zur Frage',
    'F-061': 'Diese Antwort gehört zu einer Frage, die das Formular nicht hat',
    'F-062': 'Eine Kopie braucht einen Titel, es wurde nichts kopiert',
    'F-064': FORM_NOT_HERE,
    'F-065': 'Der Link nach dem Absenden muss mit https://, http:// oder / beginnen, es wurde nichts gespeichert',
    'F-067': FORM_TAKES_NO_ANSWERS,
    'F-068': FORM_NOT_YOURS_TO_ANSWER,
    'F-069': 'Eine nicht abgesendete Antwort sehen und speichern nur das Mitglied selbst und wer es betreut',
    'F-070': INTERNAL_FORM_HAS_NO_LINK,
    'F-071': 'Ein Formular für die eigenen Mitglieder der Wache ist von außen gar nicht erreichbar, es wurde nichts gespeichert',
    'F-072': FORM_NOT_HERE,
    'F-073': 'Ein Formular, das von außerhalb der Wache beantwortet wird, lässt sich auf niemanden eingrenzen, es wurde nichts gespeichert',
    'F-074': 'Sag, wonach die Ergebnisse gruppiert werden sollen',
    'F-075': 'Nach diesem Profilfeld lassen sich die Ergebnisse nicht gruppieren',
    'F-076': 'Dieses Formular gibt es nicht mehr',
    'F-077': 'Diese Antwort auf das Formular gibt es nicht mehr',
}
