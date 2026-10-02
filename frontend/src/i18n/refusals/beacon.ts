/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    PICTURE_NOT_HERE,
} from './shared'

const BEACON_NOT_RECEIVING = 'Diese Instanz ist kein Beacon'
const BEACON_REPORT_NOT_HERE = 'Diese Meldung gibt es nicht mehr'
const BEACON_ID_NOT_A_NUMBER = 'Das benennt nichts, was ein Beacon hält'
const PROBLEM_LOG_NOT_RUNNING = 'Hier wird nichts über Probleme mitgeschrieben'
const BEACON_PROTOCOL_TOO_NEW = 'Dieses Beacon spricht diese Version des Protokolls noch nicht'

/** What the refusals of the beacon say in German, keyed by their `BC-` code. */
export default {
    'BC-001': BEACON_NOT_RECEIVING,
    'BC-002': 'Diese Störung gibt es nicht mehr, es wurde nichts geändert',
    'BC-003': 'Diese Meldung gibt es nicht mehr, es wurde nichts geändert',
    'BC-004': BEACON_REPORT_NOT_HERE,
    'BC-005': 'Zu dieser Meldung gibt es kein Bild',
    'BC-006': PICTURE_NOT_HERE,
    'BC-007': BEACON_ID_NOT_A_NUMBER,
    'BC-008': BEACON_ID_NOT_A_NUMBER,
    'BC-009': PROBLEM_LOG_NOT_RUNNING,
    'BC-010': 'Dieses Problem gibt es nicht mehr',
    'BC-011': 'Wähle mindestens ein Problem zum Senden aus, es wurde nichts gesendet',
    'BC-012': PROBLEM_LOG_NOT_RUNNING,
    'BC-013': BEACON_REPORT_NOT_HERE,
    'BC-014': 'Diese Instanz meldet an kein Beacon, es wurde nichts gesendet',
    'BC-015': BEACON_NOT_RECEIVING,
    'BC-016': 'Zu viele Sendungen von dieser Adresse',
    'BC-017': 'Diese Sendung ist größer, als ein Beacon liest',
    'BC-018': 'Eine Sendung an ein Beacon muss signiert sein',
    'BC-019': 'Diese Signatur passt nicht zur Sendung',
    'BC-020': 'Das ist kein Schlüssel',
    'BC-021': 'Eine Bildsendung kam ohne Bild an',
    'BC-022': BEACON_PROTOCOL_TOO_NEW,
    'BC-023': 'Eine Sendung muss sagen, wann sie verschickt wurde, und eine Einmalnummer tragen',
    'BC-024': BEACON_PROTOCOL_TOO_NEW,
    'BC-025': 'Diese Sendung wurde zu weit vor oder nach jetzt verschickt',
    'BC-026': 'Diese Sendung war an ein anderes Beacon gerichtet',
    'BC-027': 'Diese Sendung ist bereits einmal angekommen',
    'BC-028': 'Ein Fehler muss seinen Fingerabdruck mitbringen',
    'BC-029': 'Eine Meldung braucht einen Text',
    'BC-030': 'Eine Sendung mit Zahlen braucht mindestens einen Eintrag',
    'BC-031': 'Diese Sendung mit Zahlen enthält mehr Einträge, als ein Beacon annimmt',
    'BC-032': 'Eine Sendung mit Zahlen muss den Tag nennen, für den sie gilt',
    'BC-033': 'Eine Sendung mit Zahlen kann nicht für einen Tag gelten, der noch nicht war',
}
