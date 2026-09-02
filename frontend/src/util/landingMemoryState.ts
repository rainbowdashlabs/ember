/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {getItem, removeItem, setItem} from '@/api/storage'
import type {AccountInfo} from '@/api/types'
import {sessionInfo} from '@/util/sessionState'

const LANDING_AREA_KEY = 'landing_area'

/** The three places somebody works in. Which page of one they were on is deliberately not kept. */
export type LandingArea = 'station' | 'cluster' | 'admin'

/**
 * The area somebody was last in, and whose it is.
 *
 * <p>The account is part of it because a browser is shared and a session is not. Two people at one
 * screen would otherwise hand each other their last whereabouts, and the second would be dropped
 * into the first one's station. What is stored is only honoured while the account it names is the
 * one signed in.
 */
export interface LandingMemory {
    area: LandingArea
    account: string
    stationId?: string
    clusterUid?: string
}

/**
 * How an account is named in the memory. The identifier is preferred, and the numeric key stands in
 * where a session answer carries none.
 *
 * @param account the account of a session, if it has one
 */
export function accountKey(account: AccountInfo | undefined): string | null {
    if (!account) return null
    return account.uid ?? String(account.id)
}

export function readLandingMemory(): LandingMemory | null {
    const raw = getItem(LANDING_AREA_KEY)
    if (!raw) return null
    try {
        const parsed = JSON.parse(raw) as LandingMemory
        if (parsed?.area !== 'station' && parsed?.area !== 'cluster' && parsed?.area !== 'admin') return null
        if (typeof parsed.account !== 'string' || parsed.account === '') return null
        return parsed
    } catch {
        return null
    }
}

export function forgetLandingMemory(): void {
    removeItem(LANDING_AREA_KEY)
}

/**
 * The area noted while the session was still being fetched, waiting for a name to be put to it.
 *
 * <p>A page opened straight at a station navigates before it knows who is navigating, which is the
 * ordinary case for a bookmark. Holding the note here rather than writing it unnamed keeps it out of
 * the reach of the next person: it lives no longer than the page that took it.
 */
let unclaimed: Omit<LandingMemory, 'account'> | null = null

function write(memory: LandingMemory): void {
    setItem(LANDING_AREA_KEY, JSON.stringify(memory))
}

function areaOf(path: string): Omit<LandingMemory, 'account'> | null {
    if (path === '/admin' || path.startsWith('/admin/')) return {area: 'admin'}
    if (path === '/cluster' || path.startsWith('/cluster/')) {
        const clusterUid = getItem('cluster_id')
        return {area: 'cluster', ...(clusterUid ? {clusterUid} : {})}
    }
    if (path === '/station' || path.startsWith('/station/')) {
        const stationId = getItem('station_id')
        return stationId ? {area: 'station', stationId} : null
    }
    return null
}

/**
 * Notes the area a navigation is going to, where it is one of the three worth returning to.
 *
 * @param path where the reader is going
 */
export function rememberVisitedArea(path: string): void {
    const area = areaOf(path)
    if (!area) return
    const account = accountKey(sessionInfo.value?.account)
    if (account) write({...area, account})
    else unclaimed = area
}

/** Puts the name of the session that has just answered to what was noted before it did. */
export function claimVisitedArea(): void {
    if (!unclaimed) return
    const account = accountKey(sessionInfo.value?.account)
    if (account) write({...unclaimed, account})
    unclaimed = null
}
