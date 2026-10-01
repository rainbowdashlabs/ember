/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    NOTHING_TO_EXPORT,
    TEMPLATE_NOT_HERE,
    MEMBER_NOT_YOURS,
    DAY_NOT_A_DATE,
    MEMBER_NOT_HERE,
    QUESTION_DEFAULT_NOT_ACCEPTED,
} from './shared'

const ATTENDANCE_SHEET_NOT_HERE = 'Diese Anwesenheitsliste gibt es nicht mehr'
const ATTENDANCE_SHEET_NOT_HERE_ON_WRITE = 'Diese Anwesenheitsliste gibt es nicht mehr, es wurde nichts geändert'
const ATTENDANCE_ENTRY_NOT_HERE_ON_WRITE = 'Diesen Eintrag auf der Anwesenheitsliste gibt es nicht mehr, '
    + 'es wurde nichts geändert'
const ATTENDANCE_TEMPLATE_NEEDS_A_NAME = 'Eine Vorlage braucht einen Namen, es wurde nichts gespeichert'
const ATTENDANCE_TEMPLATE_NOT_HERE_ON_WRITE = 'Diese Vorlage gibt es nicht mehr, es wurde nichts geändert'
const ATTENDANCE_FIELD_DETAILS_MISSING = 'Ein Feld auf der Liste braucht einen Namen und eine Art, '
    + 'es wurde nichts gespeichert'
const ATTENDANCE_FIELD_NOT_HERE = 'Dieses Feld auf der Liste gibt es nicht mehr'
const ATTENDANCE_REPORT_PRESET_NOT_HERE = 'Diese gespeicherte Auswertung gibt es nicht mehr'
const ABSENCE_SPAN_MISSING = 'Gib den ersten und den letzten Tag der Abwesenheit an, es wurde nichts gespeichert'
const ABSENCE_ENDS_BEFORE_IT_STARTS = 'Die Abwesenheit kann nicht vor ihrem Beginn enden, es wurde nichts gespeichert'
const ABSENCE_NOT_HERE = 'Diese Abwesenheit gibt es nicht mehr'
const ABSENCE_NOT_HERE_ON_WRITE = 'Diese Abwesenheit gibt es nicht mehr, es wurde nichts geändert'

/** What the refusals of attendance say in German, keyed by their `AT-` code. */
export default {
    'AT-001': ATTENDANCE_SHEET_NOT_HERE,
    'AT-002': TEMPLATE_NOT_HERE,
    'AT-003': 'Diesen Eintrag auf der Anwesenheitsliste gibt es nicht mehr',
    'AT-004': MEMBER_NOT_HERE,
    'AT-005': ATTENDANCE_TEMPLATE_NEEDS_A_NAME,
    'AT-006': TEMPLATE_NOT_HERE,
    'AT-007': ATTENDANCE_TEMPLATE_NEEDS_A_NAME,
    'AT-008': ATTENDANCE_TEMPLATE_NOT_HERE_ON_WRITE,
    'AT-009': ATTENDANCE_TEMPLATE_NOT_HERE_ON_WRITE,
    'AT-010': ATTENDANCE_FIELD_DETAILS_MISSING,
    'AT-011': ATTENDANCE_FIELD_DETAILS_MISSING,
    'AT-012': ATTENDANCE_FIELD_NOT_HERE,
    'AT-013': ATTENDANCE_FIELD_NOT_HERE,
    'AT-014': DAY_NOT_A_DATE,
    'AT-015': ATTENDANCE_SHEET_NOT_HERE,
    'AT-016': ATTENDANCE_SHEET_NOT_HERE_ON_WRITE,
    'AT-017': ATTENDANCE_SHEET_NOT_HERE_ON_WRITE,
    'AT-018': ATTENDANCE_SHEET_NOT_HERE_ON_WRITE,
    'AT-019': ATTENDANCE_SHEET_NOT_HERE_ON_WRITE,
    'AT-020': 'Nenne das Mitglied, um das es bei diesem Eintrag geht, es wurde nichts gespeichert',
    'AT-021': ATTENDANCE_ENTRY_NOT_HERE_ON_WRITE,
    'AT-022': ATTENDANCE_ENTRY_NOT_HERE_ON_WRITE,
    'AT-023': ATTENDANCE_ENTRY_NOT_HERE_ON_WRITE,
    'AT-024': 'Sag, ob das Mitglied da war, gefehlt hat, abgesagt hat oder noch nicht bestätigt ist, '
        + 'es wurde nichts gespeichert',
    'AT-025': ATTENDANCE_ENTRY_NOT_HERE_ON_WRITE,
    'AT-026': ATTENDANCE_ENTRY_NOT_HERE_ON_WRITE,
    'AT-027': 'Diese Anwesenheitsliste konnte nicht als PDF ausgegeben werden',
    'AT-028': 'Gib den ersten und den letzten Tag an, den die Auswertung abdecken soll',
    'AT-029': 'Nenne mindestens eine Mitgliederart oder eine Gruppe für die Auswertung',
    'AT-030': NOTHING_TO_EXPORT,
    'AT-031': NOTHING_TO_EXPORT,
    'AT-032': 'Eine gespeicherte Auswertung braucht einen Namen, es wurde nichts gespeichert',
    'AT-033': ATTENDANCE_REPORT_PRESET_NOT_HERE,
    'AT-034': ATTENDANCE_REPORT_PRESET_NOT_HERE,
    'AT-035': 'Nenne das Mitglied, um das es bei dieser Abwesenheit geht, es wurde nichts gespeichert',
    'AT-036': ABSENCE_SPAN_MISSING,
    'AT-037': ABSENCE_ENDS_BEFORE_IT_STARTS,
    'AT-038': ABSENCE_NOT_HERE,
    'AT-039': ABSENCE_NOT_HERE_ON_WRITE,
    'AT-041': ABSENCE_SPAN_MISSING,
    'AT-042': ABSENCE_ENDS_BEFORE_IT_STARTS,
    'AT-043': MEMBER_NOT_YOURS,
    'AT-044': ABSENCE_NOT_HERE,
    'AT-045': 'Diese Abwesenheit kannst du nicht zurücknehmen',
    'AT-046': ABSENCE_NOT_HERE_ON_WRITE,
    'AT-047': 'Anwesenheitslisten bieten diesen Feldtyp nicht an, es wurde nichts gespeichert',
    'AT-048': QUESTION_DEFAULT_NOT_ACCEPTED,
    'AT-049': 'Die Liste muss nach ihrem Beginn enden, es wurde nichts gespeichert',
    'AT-050': 'Die Liste läuft länger, als eine Liste laufen darf, es wurde nichts gespeichert',
    'AT-051': 'Die Stunden, als die eine Liste zählt, können nicht negativ sein, es wurde nichts gespeichert',
    'AT-052': 'Die Stunden, als die eine Liste zählt, sind mehr, als sie zählen darf, es wurde nichts gespeichert',
    'AT-053': 'Diese Antwort passt nicht zu diesem Feld der Liste, es wurde nichts gespeichert',
    'AT-054': 'Diese Anwesenheitsliste ist geschlossen und muss erst wieder geöffnet werden, es wurde nichts geändert',
    'AT-055': 'Das Mitglied war an diesem Tag noch nicht in der Wache, es wurde nichts gespeichert',
    'AT-056': 'Eine gespeicherte Auswertung muss mindestens eine Mitgliedsart oder Gruppe nennen, es wurde nichts gespeichert',
    'AT-057': 'Eine gespeicherte Auswertung kann keinen leeren Eintrag nennen, es wurde nichts gespeichert',
}
