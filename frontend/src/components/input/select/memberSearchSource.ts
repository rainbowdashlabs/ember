/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {getMemberPickerByUid, searchMembers} from '@/api/members'
import {fromSearchResult, type MemberOption} from './memberOption'

/** As many rows as anybody reads before typing another letter. */
const LIMIT = 20

/**
 * A member menu that asks the server rather than holding the list.
 *
 * <p>For the screens that search wider than one page can hold: the page editor names anybody in the
 * station, and loading every member to filter three of them in the browser is the round trip this avoids.
 */
export async function searchMemberOptions(query: string): Promise<MemberOption[]> {
    const results = await searchMembers(query, LIMIT)
    return results.map(fromSearchResult)
}

/**
 * Puts a name to somebody chosen before the menu was drawn.
 *
 * <p>A fetched list holds whatever answered the last query, which is rarely the person already stored, so
 * without this a saved choice reads as its own UUID.
 */
export async function resolveMemberOption(memberUid: string): Promise<MemberOption | null> {
    const found = await getMemberPickerByUid(memberUid)
    return found ? fromSearchResult(found) : null
}
