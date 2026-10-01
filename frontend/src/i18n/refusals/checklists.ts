/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    APPOINTMENT_NOT_HERE,
    DAY_NOT_A_DATE,
} from './shared'

const CHECKLIST_NEEDS_A_NAME = 'Eine Checkliste braucht einen Namen, es wurde nichts gespeichert'
const CHECKLIST_PDF_NOT_MADE = 'Diese Checkliste konnte nicht als PDF erstellt werden. '
    + 'Ein erneuter Versuch kann klappen'

/** What the refusals of checklists say in German, keyed by their `CL-` code. */
export default {
    'CL-001': CHECKLIST_NEEDS_A_NAME,
    'CL-002': 'Eine Checkliste braucht mindestens eine Spalte, es wurde nichts gespeichert',
    'CL-003': CHECKLIST_NEEDS_A_NAME,
    'CL-004': 'Eine Checkliste folgt entweder einem Filter oder einem Termin, nie beidem, '
        + 'es wurde nichts geändert',
    'CL-005': 'Nenne die Spalten in der Reihenfolge, in der sie stehen sollen, es wurde nichts geändert',
    'CL-006': 'Eine neue Reihenfolge muss jede Spalte dieser Checkliste genau einmal nennen, '
        + 'es wurde nichts geändert',
    'CL-007': 'Nenne die Mitglieder, die auf die Checkliste sollen, es wurde nichts gespeichert',
    'CL-008': 'Nenne die Zeilen, auf denen die Spalte gesetzt werden soll, es wurde nichts geändert',
    'CL-009': CHECKLIST_PDF_NOT_MADE,
    'CL-010': CHECKLIST_PDF_NOT_MADE,
    'CL-011': 'Diese Spalte gibt es nicht mehr',
    'CL-012': 'Diese Spalte gehört zu einer anderen Checkliste',
    'CL-013': 'Diese Zeile gibt es nicht mehr',
    'CL-014': 'Diese Zeile gehört zu einer anderen Checkliste',
    'CL-015': 'Nenne den Tag des Termins, dem die Checkliste folgen soll, es wurde nichts gespeichert',
    'CL-016': APPOINTMENT_NOT_HERE,
    'CL-017': 'Du kannst diesen Termin nicht sehen, eine Checkliste kann ihm daher nicht folgen',
    'CL-018': DAY_NOT_A_DATE,
    'CL-020': 'Eine Spalte braucht eine Beschriftung, es wurde nichts gespeichert',
}
