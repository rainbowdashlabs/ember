/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    STATION_NOT_HERE,
    APPOINTMENT_NOT_HERE,
    GROUP_NOT_HERE_NOTHING_SAVED,
    TOO_MANY_ATTEMPTS,
} from './shared'

const QUESTION_NOT_ON_LIST = 'Diese Frage steht nicht mehr auf dieser Warteliste'
const NO_LIST_FOR_INVITE = 'Diese Einladung führt zu keiner Warteliste'
const ENTRY_NOT_HERE = 'Diesen Eintrag gibt es nicht mehr'
const WAITING_LIST_NOT_HERE = 'Diese Warteliste gibt es nicht mehr'
const WAITING_LIST_HAS_A_BIRTH_DATE = 'Diese Warteliste hat schon ein Feld für das Geburtsdatum, es wurde nichts gespeichert'
const WAITING_LIST_ANSWER_NOT_ACCEPTED = 'Eine Antwort passt nicht zu ihrer Frage auf dieser Warteliste, '
    + 'es wurde nichts gespeichert'

/** What the refusals of waiting lists say in German, keyed by their `W-` code. */
export default {
    'W-022': 'Diese Einladung lässt sich nicht mehr verwenden',
    'W-023': 'Eine Einladung und ein Vorname werden gebraucht, es wurde nichts gespeichert',
    'W-024': 'Du konntest nicht auf die Warteliste gesetzt werden, es wurde nichts gespeichert',
    'W-025': 'Für diese Warteliste ist eine Anmeldung so nicht möglich, es wurde nichts gespeichert',
    'W-026': 'Sag, wie deine Antwort lautet',
    'W-027': 'Das ist keine Antwort, die diese Einladung annimmt',
    'W-028': ENTRY_NOT_HERE,
    'W-029': 'Dieser Eintrag lässt sich so nicht einladen, es wurde nichts geändert',
    'W-030': ENTRY_NOT_HERE,
    'W-031': 'Dieser Eintrag lässt sich so nicht zurück auf die Warteliste setzen, es wurde nichts geändert',
    'W-032': APPOINTMENT_NOT_HERE,
    'W-033': 'Du kannst diesen Termin nicht sehen, es wurde nichts gespeichert',
    'W-034': 'Eine Einladung braucht das Datum des Termins',
    'W-035': 'Das ist kein Datum',
    'W-036': 'Das ist keine Uhrzeit',
    'W-037': ENTRY_NOT_HERE,
    'W-038': 'Für diesen Eintrag lässt sich so keine Probezeit starten, es wurde nichts geändert',
    'W-039': ENTRY_NOT_HERE,
    'W-040': 'Dieser Eintrag lässt sich so nicht als beigetreten markieren, es wurde nichts geändert',
    'W-041': ENTRY_NOT_HERE,
    'W-042': 'Dieser Eintrag lässt sich so nicht zurückziehen, es wurde nichts geändert',
    'W-043': 'Die Formel für die Punkte konnte nicht gelesen werden, es wurde nichts gespeichert',
    'W-044': 'Ein Vorname wird gebraucht, es wurde nichts gespeichert',
    'W-045': 'Eine E-Mail-Adresse wird gebraucht, es wurde nichts gespeichert',
    'W-046': 'Dieser Bestätigungslink ist nicht mehr gültig',
    'W-047': 'Die Gruppe für die Probezeit nimmt niemanden auf Probe auf, es wurde nichts gespeichert',
    'W-048': 'Die Gruppe für den Beitritt nimmt keine Mitglieder auf, es wurde nichts gespeichert',
    'W-049': GROUP_NOT_HERE_NOTHING_SAVED,
    'W-050': GROUP_NOT_HERE_NOTHING_SAVED,
    'W-051': 'Wartelisten bieten diesen Fragetyp nicht an, es wurde nichts gespeichert',

    'W-001': QUESTION_NOT_ON_LIST,
    'W-002': NO_LIST_FOR_INVITE,
    'W-003': ENTRY_NOT_HERE,
    'W-004': NO_LIST_FOR_INVITE,
    'W-005': WAITING_LIST_NOT_HERE,
    'W-006': ENTRY_NOT_HERE,
    'W-007': WAITING_LIST_NOT_HERE,
    'W-008': STATION_NOT_HERE,
    'W-009': ENTRY_NOT_HERE,
    'W-010': TOO_MANY_ATTEMPTS,
    'W-011': WAITING_LIST_NOT_HERE,
    'W-012': WAITING_LIST_NOT_HERE,
    'W-013': WAITING_LIST_NOT_HERE,
    'W-014': QUESTION_NOT_ON_LIST,
    'W-015': WAITING_LIST_NOT_HERE,
    'W-016': ENTRY_NOT_HERE,
    'W-017': ENTRY_NOT_HERE,
    'W-018': STATION_NOT_HERE,
    'W-019': WAITING_LIST_NOT_HERE,
    'W-020': WAITING_LIST_NOT_HERE,
    'W-021': WAITING_LIST_NOT_HERE,
    'W-052': WAITING_LIST_HAS_A_BIRTH_DATE,
    'W-053': WAITING_LIST_HAS_A_BIRTH_DATE,
    'W-054': 'Dieser Eintrag lässt sich nicht mehr von der Liste nehmen, es wurde nichts geändert',
    'W-055': WAITING_LIST_ANSWER_NOT_ACCEPTED,
    'W-056': WAITING_LIST_ANSWER_NOT_ACCEPTED,
    'W-057': WAITING_LIST_ANSWER_NOT_ACCEPTED,
    'W-058': 'Diese Einladung lässt sich nicht mehr beantworten, es wurde nichts gespeichert',
    'W-059': 'Diese Antwort betrifft einen anderen Termin als die Einladung, es wurde nichts gespeichert',
    'W-060': 'Diese Warteliste nimmt in diesem Alter noch keine Anmeldungen an, es wurde nichts gespeichert',
}
