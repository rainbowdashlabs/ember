/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {BatchRow} from '@/api/generated/schema'
import {localInputToInstant} from '@/util/format'

/**
 * One appointment of a batch as it is written down, before it is placed on the clock.
 *
 * @param date        the day, `YYYY-MM-DD`, or empty where none is given yet
 * @param startTime   the time of day it starts, `HH:mm`
 * @param endTime     the time of day it ends, `HH:mm`
 * @param name        its own title, or empty for the title the batch gives every appointment
 * @param fieldValues the answers to the fields of the batch it brings along
 */
export interface DraftRow {
    date: string
    startTime: string
    endTime: string
    name: string
    fieldValues: Record<string, string>
}

/** What keeps a row from becoming an appointment; `order` is an end at or before the start. */
export type DraftProblemReason = 'date' | 'startTime' | 'endTime' | 'order'

/** A day and a time of day as the moment they name on the reader's clock, or empty where they name none. */
export function momentOn(date: string, time: string): string {
    return date && time ? localInputToInstant(`${date}T${time}`) : ''
}

/**
 * What is wrong with one row, in the order the reader fills it in. Every way a batch takes in its
 * dates judges a row by this one rule, so an appointment that ends at or before its start is refused
 * wherever it comes from.
 */
export function draftProblems(draft: DraftRow): DraftProblemReason[] {
    const start = momentOn(draft.date, draft.startTime)
    const end = momentOn(draft.date, draft.endTime)
    const reasons: DraftProblemReason[] = []
    if (!draft.date || Number.isNaN(new Date(draft.date).getTime())) reasons.push('date')
    else {
        if (!start) reasons.push('startTime')
        if (!end) reasons.push('endTime')
        if (start && end && end <= start) reasons.push('order')
    }
    return reasons
}

/** A written row as the appointment the batch creates from it. Only call it for a row without problems. */
export function draftToBatchRow(draft: DraftRow): BatchRow {
    return {
        name: draft.name.trim() || null,
        startTime: momentOn(draft.date, draft.startTime),
        endTime: momentOn(draft.date, draft.endTime),
        fieldValues: {...draft.fieldValues},
    }
}
