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
    'R-030': 'Wer den Ablauf betreut, nennt eine Gruppe, einen Tag oder ein Mitglied einer anderen Wache, es wurde nichts gespeichert',
    'R-031': 'Gib an, wer den Ablauf betreut, es wurde nichts gespeichert',
    'R-032': 'Diese Ablauf-Vorlage gibt es hier nicht, es wurde kein Ablauf angelegt',
    'R-033': 'Dieses Dokument kann kein Schritt sein: Es muss in Gebrauch sein, darf nicht für Termine gedacht sein '
        + 'und muss das Mitglied oder eine erziehungsberechtigte Person unterschreiben lassen, es wurde nichts gespeichert',
    'R-034': 'Wähle das Dokument, das der Schritt unterschreiben lässt, es wurde nichts gespeichert',
    'R-035': 'Dieser Schritt hat sein Dokument schon erstellt, was er verlangt, bleibt deshalb so',
    'R-036': 'Nur wer Dokumente für Mitglieder erstellen darf, kann einen Schritt ein Dokument unterschreiben lassen, es wurde nichts gespeichert',
    'R-037': 'Diese Person ist hier kein Mitglied, der Ablauf ist deshalb nicht über sie',
    'R-038': 'Wähle zuerst, um wen es im Ablauf geht, bevor ein Schritt ein Dokument unterschreiben lässt, es wurde nichts gespeichert',
    'R-039': 'Wähle, um wen es im Ablauf geht, es wurde nichts geändert',

    'R-001': PROCEDURE_NOT_YOURS,
    'R-002': PROCEDURE_NOT_YOURS,
}
