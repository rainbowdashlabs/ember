/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    DAY_NOT_A_DATE,
    COMMENT_NOT_HERE,
    COMMENT_NEEDS_TEXT,
} from './shared'

/** What the refusals of comments and notes say in German, keyed by their `CM-` code. */
export default {
    'CM-001': DAY_NOT_A_DATE,
    'CM-002': COMMENT_NEEDS_TEXT,
    'CM-003': COMMENT_NOT_HERE,
    'CM-004': 'Du kannst nur deine eigenen Kommentare ändern',
    'CM-005': COMMENT_NEEDS_TEXT,
    'CM-006': COMMENT_NOT_HERE,
    'CM-007': COMMENT_NOT_HERE,
    'CM-008': 'Du kannst nur deine eigenen Kommentare löschen',
    'CM-009': COMMENT_NOT_HERE,
    'CM-010': 'Du darfst Notizen dieser Art weder lesen noch schreiben',
    'CM-011': 'Eine Notiz braucht einen Text',
    'CM-012': 'Hier ist noch nichts notiert',
    'CM-013': COMMENT_NOT_HERE,
}
