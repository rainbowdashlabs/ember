/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {KbRefusalReason, type BulkOutcome, type KbRefusalReasonName} from '@/api/knowledgeBase'

type Translate = (key: string, named?: Record<string, unknown>) => string

const REASON_KEYS: Record<KbRefusalReasonName, string> = {
    [KbRefusalReason.NO_PERMISSION]: 'kb.refusalNoPermission',
    [KbRefusalReason.NAME_TAKEN]: 'kb.refusalNameTaken',
    [KbRefusalReason.TARGET_INSIDE]: 'kb.refusalTargetInside',
    [KbRefusalReason.SHARE_TOO_WIDE]: 'kb.refusalShareTooWide',
    [KbRefusalReason.NOT_FOUND]: 'kb.refusalNotFound',
}

/**
 * One refused entry as a sentence: the entry by name and why it stayed where it was.
 *
 * The server sends a reason from a closed set rather than a sentence, because there is one language
 * file and free text from the server could not be written in it.
 */
export function kbRefusalMessage(t: Translate, reason: KbRefusalReasonName | null, name: string): string {
    const key = reason ? REASON_KEYS[reason] : 'kb.refusalNotFound'
    return t(key, {name})
}

/**
 * The result of a bulk action as one line: how much went through, and which entries did not.
 *
 * Entries are named rather than counted, because a count sends the reader back to walk their own
 * selection looking for the one that stayed. Where a selection had more refusals than a message can
 * carry, the named ones are followed by how many more there were.
 */
export function kbBulkMessage(t: Translate, outcome: BulkOutcome, doneKey: string): string {
    const done = outcome.doneFolderIds.length + outcome.doneFileIds.length
    const line = t(doneKey, {count: done})
    if (outcome.refusedTotal === 0) return line
    const named = outcome.refused
        .map(entry => kbRefusalMessage(t, entry.reason, entry.name ?? t('kb.refusalUnnamed')))
        .join(' ')
    const rest = outcome.refusedTotal - outcome.refused.length
    const more = rest > 0 ? ' ' + t('kb.refusalMore', {count: rest}) : ''
    return `${line} ${named}${more}`
}
