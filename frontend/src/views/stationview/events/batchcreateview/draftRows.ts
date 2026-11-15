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

/** The ways the dates of a batch are entered: generated from a schedule, imported from a file or written by hand. */
export type ScheduleMode = 'schedule' | 'csv' | 'manual'

/** What keeps a row from becoming an appointment; `order` is an end at or before the start. */
export type DraftProblemReason = 'date' | 'startTime' | 'endTime' | 'order'

/**
 * A written row that cannot become an appointment.
 *
 * @param index  the position of the row in the list
 * @param reason what is wrong with it
 */
export interface DraftProblem {
    index: number
    reason: DraftProblemReason
}

/** What the written rows became: the appointments and the rows that could not become one. */
export interface DraftResult {
    rows: BatchRow[]
    problems: DraftProblem[]
}

/**
 * A row of the list written by hand, with a key of its own so removing one row does not hand its
 * inputs to the row below.
 */
export interface KeyedDraftRow extends DraftRow {
    key: number
}

/**
 * A new row to add to the list, taking the day and the times of the last row.
 *
 * <p>Several appointments on one day, or at the same time on several days, are the usual case, so
 * the next row starts from the last one rather than empty.
 */
export function nextDraftRow(rows: KeyedDraftRow[]): KeyedDraftRow {
    const previous = rows[rows.length - 1]
    return {
        key: rows.reduce((highest, row) => Math.max(highest, row.key), -1) + 1,
        date: previous?.date ?? '',
        startTime: previous?.startTime ?? '',
        endTime: previous?.endTime ?? '',
        name: '',
        fieldValues: {},
    }
}

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

/**
 * Turns the written rows into appointments, or names every row that cannot become one.
 *
 * <p>A row without a day, without a readable time or not ending after it starts never becomes an
 * appointment at some other moment; the whole list waits until each of them is put right.
 */
export function draftsToBatchRows(drafts: DraftRow[]): DraftResult {
    const problems = drafts.flatMap((draft, index) => draftProblems(draft).map(reason => ({index, reason})))
    return {rows: problems.length > 0 ? [] : drafts.map(draftToBatchRow), problems}
}
