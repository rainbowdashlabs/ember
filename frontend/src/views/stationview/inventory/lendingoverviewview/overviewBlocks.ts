/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {LendingStatus, type BorrowedItemResponse, type LendingRequestResponse} from '@/api/generated/schema'
import {shiftIsoDate} from '@/util/format'

/** How far ahead "soon" reaches: a week. */
export const SOON_DAYS = 7

/** The most rows a block shows before it leads to the table. */
export const BLOCK_ROWS = 5

/** What the station lends: requests for its own gear. */
export interface LendBlocks {
    toAnswer: LendingRequestResponse[]
    handOutSoon: LendingRequestResponse[]
    outNow: LendingRequestResponse[]
    overdue: LendingRequestResponse[]
}

/** What the station borrows: its requests for partner gear. */
export interface BorrowBlocks {
    waiting: LendingRequestResponse[]
    pickUpSoon: LendingRequestResponse[]
    giveBackSoon: LendingRequestResponse[]
}

/** Partner pieces at the station, together per request. */
export interface BorrowedGroup {
    requestId: number
    stationName: string
    dueOn: string | null
    pieces: {name: string; internalId: string | null}[]
}

function lastDay(entry: LendingRequestResponse): string {
    return entry.request.requestedDateTo ?? entry.request.requestedDateFrom
}

function byCreated(a: LendingRequestResponse, b: LendingRequestResponse): number {
    return a.request.createdAt.localeCompare(b.request.createdAt)
}

function byFirstDay(a: LendingRequestResponse, b: LendingRequestResponse): number {
    return a.request.requestedDateFrom.localeCompare(b.request.requestedDateFrom)
}

function byLastDay(a: LendingRequestResponse, b: LendingRequestResponse): number {
    return lastDay(a).localeCompare(lastDay(b))
}

function inStatus(entries: LendingRequestResponse[], status: LendingStatus): LendingRequestResponse[] {
    return entries.filter(entry => entry.request.status === status)
}

/**
 * The blocks of the lending side: what waits for an answer (oldest first), what is handed out
 * within a week, what is out and what is overdue.
 */
export function lendBlocks(requests: readonly LendingRequestResponse[], today: string): LendBlocks {
    const own = requests.filter(entry => entry.isOwner)
    const soon = shiftIsoDate(today, SOON_DAYS)
    const lent = inStatus(own, LendingStatus.LENT)
    return {
        toAnswer: inStatus(own, LendingStatus.REQUESTED).sort(byCreated),
        handOutSoon: inStatus(own, LendingStatus.APPROVED)
            .filter(entry => entry.request.requestedDateFrom <= soon)
            .sort(byFirstDay),
        outNow: lent.filter(entry => !entry.overdue).sort(byLastDay),
        overdue: lent.filter(entry => entry.overdue).sort(byLastDay),
    }
}

/**
 * The blocks of the borrowing side: what waits for the partner's answer, what is picked up within a
 * week, and what goes back within a week or is overdue.
 */
export function borrowBlocks(requests: readonly LendingRequestResponse[], today: string): BorrowBlocks {
    const asked = requests.filter(entry => !entry.isOwner)
    const soon = shiftIsoDate(today, SOON_DAYS)
    return {
        waiting: inStatus(asked, LendingStatus.REQUESTED).sort(byCreated),
        pickUpSoon: inStatus(asked, LendingStatus.APPROVED)
            .filter(entry => entry.request.requestedDateFrom <= soon)
            .sort(byFirstDay),
        giveBackSoon: inStatus(asked, LendingStatus.LENT)
            .filter(entry => entry.overdue || lastDay(entry) <= soon)
            .sort(byLastDay),
    }
}

/** The partner pieces at the station, one group per request, the one due first leading. */
export function borrowedGroups(items: readonly BorrowedItemResponse[]): BorrowedGroup[] {
    const groups = new Map<number, BorrowedGroup>()
    for (const row of items) {
        const group = groups.get(row.loanRequestId)
            ?? {requestId: row.loanRequestId, stationName: row.ownerStationName, dueOn: row.dueOn, pieces: []}
        group.pieces.push({name: row.item.name, internalId: row.item.internalId})
        groups.set(row.loanRequestId, group)
    }
    return [...groups.values()].sort((a, b) => (a.dueOn ?? '9999').localeCompare(b.dueOn ?? '9999'))
}
