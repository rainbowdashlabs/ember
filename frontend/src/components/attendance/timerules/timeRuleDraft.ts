/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {TimeRule, TimeRuleInput} from '@/api/generated/schema'
import type {RestrictionSelection} from '@/api/types'
import {namesSomebody} from '@/components/input/restriction'

/**
 * One preparation and follow-up rule as the editor holds it while somebody changes it.
 *
 * <p>`key` keeps a row's identity across reordering and saving, which the rule's own id cannot: a
 * new rule has none, and a saved list comes back with new ones. `intact` is false for a rule that
 * lost a group or tag it named; it reads back naming nobody and has to be given its people again.
 */
export interface TimeRuleDraft {
    key: string
    label: string
    preparationMinutes: number
    followUpMinutes: number
    audience: RestrictionSelection
    intact: boolean
}

/** The most minutes a rule credits before or after an appointment: one day. */
export const MAX_RULE_MINUTES = 1440

/**
 * A key for one row of the editor.
 *
 * <p>Drawn rather than counted, because a counter at module level is shared by every request the server
 * renders, and `crypto.randomUUID` is missing on a station served over plain HTTP.
 */
function keyOf(): string {
    return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`
}

/** A rule as read from a template or a sheet, ready to be edited. */
export function draftOf(rule: TimeRule): TimeRuleDraft {
    return {
        key: keyOf(),
        label: rule.label ?? '',
        preparationMinutes: rule.preparationMinutes,
        followUpMinutes: rule.followUpMinutes,
        audience: {
            userTypes: [...rule.audience.userTypes],
            groupIds: [...rule.audience.groupIds],
            tagIds: [...rule.audience.tagIds],
            memberIds: [...rule.audience.memberIds],
            mode: rule.audience.mode,
        },
        intact: rule.intact,
    }
}

/** A new rule that names nobody yet, which the editor asks to be filled in before it saves. */
export function emptyDraft(): TimeRuleDraft {
    return {
        key: keyOf(),
        label: '',
        preparationMinutes: 0,
        followUpMinutes: 0,
        audience: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'OR'},
        intact: true,
    }
}

/** Whether a number of minutes is one a rule may carry. */
export function minutesFit(minutes: number | undefined): boolean {
    return minutes !== undefined && Number.isInteger(minutes) && minutes >= 0 && minutes <= MAX_RULE_MINUTES
}

/** Whether every rule of a list may be saved as it stands. */
export function savable(drafts: readonly TimeRuleDraft[]): boolean {
    return drafts.every(draft => namesSomebody(draft.audience)
        && minutesFit(draft.preparationMinutes)
        && minutesFit(draft.followUpMinutes))
}

/** A rule as the server takes it, in the order it stands in the list. */
export function inputOf(draft: TimeRuleDraft): TimeRuleInput {
    return {
        label: draft.label.trim() || null,
        preparationMinutes: draft.preparationMinutes,
        followUpMinutes: draft.followUpMinutes,
        audience: {...draft.audience},
    }
}
