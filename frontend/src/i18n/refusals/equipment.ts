/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    APPOINTMENT_NOT_HERE,
    DAY_NOT_A_DATE,
} from './shared'

const EQUIPMENT_DATE_MISSING = 'Nenne den Tag, um den es geht'
const EQUIPMENT_PIECE_MISSING = 'Nenne den Gegenstand, um den es geht'

/** What the refusals of equipment say in German, keyed by their `EQ-` code. */
export default {
    'EQ-001': APPOINTMENT_NOT_HERE,
    'EQ-002': 'Diese Zeile des Bedarfs gibt es nicht mehr',
    'EQ-003': 'Der Bedarf des Termins ließ sich nicht gegen den Bestand rechnen',
    'EQ-004': 'Diese Zeile ließ sich nicht zum Termin schreiben, es wurde nichts gespeichert',
    'EQ-005': 'Diese Zeile ließ sich nicht ändern, es wurde nichts gespeichert',
    'EQ-006': 'Nenne mindestens einen Gegenstand, der herausgegeben wurde, es wurde nichts gespeichert',
    'EQ-007': 'Dieser Gegenstand ließ sich nicht als herausgegeben festhalten, es wurde nichts gespeichert',
    'EQ-008': 'Diese Herausgabe gibt es nicht mehr, es wurde nichts geändert',
    'EQ-009': EQUIPMENT_DATE_MISSING,
    'EQ-010': DAY_NOT_A_DATE,
    'EQ-011': EQUIPMENT_PIECE_MISSING,
    'EQ-012': EQUIPMENT_PIECE_MISSING,
    'EQ-013': 'Gib den ersten Tag an, für den die gesammelte Liste gelten soll',
}
