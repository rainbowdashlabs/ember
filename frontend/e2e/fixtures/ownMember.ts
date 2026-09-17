/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {APIRequestContext, Page} from '@playwright/test'
import {apiHeaders, expect} from './auth'
import {remember} from './createdMembers'
import {unique} from './unique'

/** Somebody a story made, and is therefore free to do anything to. */
export interface OwnMember {
    /** The membership at the station, which the station's own screens name. */
    memberId: number
    accountId: number
    email: string
    firstName: string
    lastName: string
    /** What the lists show, which is what a screen is searched by. */
    surname: string
}

/**
 * Makes a member of this story's own.
 *
 * <p>For every story that changes a person rather than reading one. The cast is for reading, and for
 * writing to what a story made itself: re-addressing somebody ends their sessions, re-onboarding
 * them ends them too, and renaming them is read by whoever is asserting a name elsewhere. A story
 * doing any of that to a seeded person does it to whoever else is acting as them, in another worker,
 * with nothing on screen to say why that story failed.
 *
 * <p>The station is the one the caller is acting for, so the new member is reachable from the same
 * screens the story is already on. They are noted down for removal when the story ends.
 *
 * @param page  a page signed in as somebody who may enter members
 * @param label what the person is for, which goes into the name so a leftover says who made it
 */
export async function ownMember(page: Page, label: string): Promise<OwnMember> {
    const headers = await apiHeaders(page)
    const surname = unique(label)
    const email = `${surname.toLowerCase()}@example.test`

    const invited = await page.request.post('/api/v1/members/invite', {
        headers,
        data: {firstName: 'Testperson', lastName: surname, email, sendSetupMail: false},
    })
    expect(invited.ok(), `the story makes a member of its own (${await invited.text()})`).toBeTruthy()
    // The invitation answers with the account id under the name `id`, which is not the member id.
    const account = await invited.json() as {id: number}

    const memberId = await memberIdOf(page.request, headers, account.id)
    remember(headers, memberId)
    return {
        memberId,
        accountId: account.id,
        email,
        firstName: 'Testperson',
        lastName: surname,
        surname,
    }
}

/**
 * The membership the invitation created, read back rather than assumed.
 *
 * <p>The invitation answers with the account, and every screen a story then uses names the
 * membership, which is a different number.
 */
async function memberIdOf(
    request: APIRequestContext,
    headers: Record<string, string>,
    accountId: number,
): Promise<number> {
    const listed = await request.get('/api/v1/station-members', {headers})
    expect(listed.ok(), `the station lists its members (${await listed.text()})`).toBeTruthy()
    const rows = await listed.json() as {id: number; accountId: number}[]
    const row = rows.find(candidate => candidate.accountId === accountId)
    if (!row) throw new Error(`The member invited for account ${accountId} is not in the station list`)
    return row.id
}
