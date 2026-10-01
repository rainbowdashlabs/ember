/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    CHANGE_SAVED_BUT_NOT_READ_BACK,
    DAY_NOT_A_DATE,
} from './shared'

const PROCEDURE_TEMPLATE_NEEDS_A_NAME = 'Eine Vorgangsvorlage braucht einen Namen, es wurde nichts gespeichert'
const PROCEDURE_STEP_NEEDS_A_TITLE = 'Ein Schritt braucht einen Titel, es wurde nichts gespeichert'
const PROCEDURE_STEP_NOT_HERE_ON_WRITE = 'Diesen Schritt des Vorgangs gibt es nicht mehr, '
    + 'es wurde nichts geändert'
const PROCEDURE_NOT_YOURS = 'Dieser Vorgang wurde dir nicht übertragen'

/** What the refusals of procedures say in German, keyed by their `R-` code. */
export default {
    'R-003': PROCEDURE_TEMPLATE_NEEDS_A_NAME,
    'R-004': PROCEDURE_TEMPLATE_NEEDS_A_NAME,
    'R-005': 'Diese Vorgangsvorlage gibt es nicht mehr, es wurde nichts geändert',
    'R-006': PROCEDURE_STEP_NEEDS_A_TITLE,
    'R-007': PROCEDURE_STEP_NOT_HERE_ON_WRITE,
    'R-008': PROCEDURE_STEP_NOT_HERE_ON_WRITE,
    'R-009': 'Nenne den Tag des Termins, für den die Vorgänge vorbereitet wurden',
    'R-010': DAY_NOT_A_DATE,
    'R-011': 'Ein Vorgang braucht einen Namen, es wurde nichts gespeichert',
    'R-012': 'Diesen Vorgang gibt es nicht mehr, es wurde nichts geändert',
    'R-013': 'Dieser Vorgang ist bereits erledigt, es wurde nichts geändert',
    'R-014': 'Dieser Vorgang ist bereits offen, es wurde nichts geändert',
    'R-015': 'Nenne mindestens ein Mitglied, dem der Vorgang übertragen wird, es wurde nichts gespeichert',
    'R-016': PROCEDURE_STEP_NEEDS_A_TITLE,
    'R-017': PROCEDURE_STEP_NOT_HERE_ON_WRITE,
    'R-018': PROCEDURE_STEP_NOT_HERE_ON_WRITE,
    'R-020': 'Diesen Schritt hakt ab, wer den Vorgang führt',
    'R-021': PROCEDURE_NOT_YOURS,
    'R-022': 'Dieser Schritt lässt sich noch nicht abhaken: ein Schritt davor ist noch offen, '
        + 'oder er ist bereits abgehakt',
    'R-023': 'Nur wer den Vorgang führt, kann ein Häkchen zurücknehmen',
    'R-024': CHANGE_SAVED_BUT_NOT_READ_BACK,
    'R-025': CHANGE_SAVED_BUT_NOT_READ_BACK,
    'R-026': CHANGE_SAVED_BUT_NOT_READ_BACK,
    'R-027': PROCEDURE_STEP_NOT_HERE_ON_WRITE,
    'R-028': PROCEDURE_STEP_NOT_HERE_ON_WRITE,
    'R-029': 'Nur wer den Vorgang führt, kann eine Notiz an einen Schritt schreiben',

    'R-001': PROCEDURE_NOT_YOURS,
    'R-002': PROCEDURE_NOT_YOURS,
}
