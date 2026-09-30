/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {getMemberNewsTeaser, getPublicNewsTeaser, type NewsTeaser} from '@/api/news'
import type {BlockAudience} from '@/api/pageManage'
import {sessionInfo} from '@/util/sessionState'

/** Where a news block found its entry, which decides where its link goes. */
export type EmbeddedNewsSource = {kind: 'PUBLIC', stationUid: string} | {kind: 'MEMBER'}

/** A news entry as a news block shows it, and where it was found. */
export interface FoundNews extends NewsTeaser {
    source: EmbeddedNewsSource
}

/**
 * Finds the entry a news block names by its public id, as far as every reader of its content may
 * read it.
 *
 * <p>In a news or wiki article (`MEMBERS`) a member signed in to the station that owns the entry asks
 * the station itself, which answers every entry every member may read, internal ones included. On a
 * public page, and for every other reader, only the public blog is asked, so a page never shows an
 * internal entry even to a member.
 *
 * @param apiBase    the API root from `apiUrl('')`, for the public blog during a server render
 * @param stationUid the station the block belongs to
 * @param newsUid    the public id the block names
 * @param audience   who reads the content the block sits in
 * @returns the entry, or null where the entry is not available to these readers; rejects on any
 *          other failure, which says nothing about the entry
 */
export async function findEmbeddedNews(
    apiBase: string,
    stationUid: string,
    newsUid: string,
    audience: BlockAudience,
): Promise<FoundNews | null> {
    const asMember = audience === 'MEMBERS' && sessionInfo.value?.stationId === stationUid
    try {
        if (asMember) return {...(await getMemberNewsTeaser(newsUid)), source: {kind: 'MEMBER'}}
        return {...(await getPublicNewsTeaser(apiBase, stationUid, newsUid)), source: {kind: 'PUBLIC', stationUid}}
    } catch (failure) {
        if (isNotFound(failure)) return null
        throw failure
    }
}

function isNotFound(failure: unknown): boolean {
    const answer = failure as {statusCode?: number, status?: number, response?: {status?: number}} | null
    return answer?.statusCode === 404 || answer?.status === 404 || answer?.response?.status === 404
}
