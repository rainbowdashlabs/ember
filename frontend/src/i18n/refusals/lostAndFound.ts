/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    PICTURE_NOT_HERE,
    UPLOAD_WITHOUT_FILE,
    UPLOAD_NOT_PROCESSED,
    KB_PICTURE_KIND_NOT_TAKEN,
} from './shared'

const LOST_ITEM_NOT_CLAIMED = 'Diese Fundsache ist nicht beansprucht, es wurde nichts geändert'

/** What the refusals of lost and found say in German, keyed by their `LF-` code. */
export default {
    'LF-001': PICTURE_NOT_HERE,
    'LF-002': UPLOAD_WITHOUT_FILE,
    'LF-003': KB_PICTURE_KIND_NOT_TAKEN,
    'LF-004': 'Dieses Bild ließ sich nicht behalten, es wurde nichts gespeichert',
    'LF-005': UPLOAD_NOT_PROCESSED,
    'LF-006': 'Du betreust dieses Mitglied nicht, es wurde nichts beansprucht',
    'LF-007': 'Diese Fundsache ist bereits beansprucht, oder es gibt sie nicht mehr',
    'LF-008': LOST_ITEM_NOT_CLAIMED,
    'LF-009': 'Diesen Anspruch darfst du nicht zurücknehmen',
    'LF-010': LOST_ITEM_NOT_CLAIMED,
    'LF-011': LOST_ITEM_NOT_CLAIMED,
    'LF-012': 'Der Tag des Fundes ist kein Datum',
}
