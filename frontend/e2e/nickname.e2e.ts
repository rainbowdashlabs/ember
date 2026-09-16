/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders} from './fixtures/auth'

/**
 * The name a station calls somebody by, and the name on its documents.
 *
 * The rule is only worth anything if both halves hold at once: the screens have to change and the
 * register has to stay. Every form can be built correctly in isolation and a screen still ask for
 * the wrong one, which is exactly what happened to the member list, so what is asserted here is
 * what each screen actually reads rather than what the resolver can produce.
 */
test.describe('A name to be called by', () => {

    /** Clears whatever a test left behind, so a rerun starts from the register name. */
    async function clearNickname(page: import('@playwright/test').Page, memberId: number) {
        await page.request.put(`/api/v1/station-members/${memberId}/nickname`, {
            headers: await apiHeaders(page),
            data: {nickname: null},
        })
    }

    test('a member sets what the station calls them, and the screens follow', async ({memberPage: page}) => {
        const headers = await apiHeaders(page)
        const before = await page.request.get('/api/v1/session', {headers})
        expect(before.ok(), await before.text()).toBeTruthy()
        const session = await before.json()
        const memberId = session.member.id
        const registerName = `${session.account.firstName} ${session.account.lastName}`

        const set = await page.request.put(`/api/v1/station-members/${memberId}/nickname`, {
            headers,
            data: {nickname: 'Pepe'},
        })
        expect(set.ok(), await set.text()).toBeTruthy()

        const after = await (await page.request.get('/api/v1/session', {headers})).json()
        expect(after.member.calledName).toBe(`Pepe ${session.account.lastName}`)
        expect(after.member.nickname).toBe('Pepe')
        expect(`${after.account.firstName} ${after.account.lastName}`).toBe(registerName)

        await clearNickname(page, memberId)
        const cleared = await (await page.request.get('/api/v1/session', {headers})).json()
        expect(cleared.member.calledName, 'clearing it gives the register name back').toBe(registerName)
    })

    /**
     * A member list says who somebody is as well as what they are called, because it is the one
     * screen that has to do both jobs at once.
     */
    test('a member list says who somebody is and what they are called', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const session = await (await page.request.get('/api/v1/session', {headers})).json()
        const memberId = session.member.id

        const set = await page.request.put(`/api/v1/station-members/${memberId}/nickname`, {
            headers,
            data: {nickname: 'Pepe'},
        })
        expect(set.ok(), await set.text()).toBeTruthy()

        const listed = (await (await page.request.get('/api/v1/station-members/rich', {headers})).json())
            .find((m: {id: number}) => m.id === memberId)
        expect(listed.name).toBe(`${session.account.firstName} "Pepe" ${session.account.lastName}`)
        expect(listed.firstName, 'the halves are still the register\'s').toBe(session.account.firstName)

        await clearNickname(page, memberId)
        const back = (await (await page.request.get('/api/v1/station-members/rich', {headers})).json())
            .find((m: {id: number}) => m.id === memberId)
        expect(back.name).toBe(`${session.account.firstName} ${session.account.lastName}`)
    })

    /**
     * Somebody who may edit every member still may not decide what one of them is called.
     *
     * A nickname another person can impose is how this feature would turn into a way to label
     * people, so the refusal does not depend on what rights the person holds over members.
     */
    test('a manager cannot name a member they do not look after', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        const session = await (await page.request.get('/api/v1/session', {headers})).json()
        const members = await (await page.request.get('/api/v1/station-members/rich', {headers})).json()
        const somebodyElse = members.find((m: {id: number}) => m.id !== session.member.id)
        expect(somebodyElse, 'the station has another member').toBeTruthy()

        const refused = await page.request.put(`/api/v1/station-members/${somebodyElse.id}/nickname`, {
            headers,
            data: {nickname: 'Zwerg'},
        })
        expect(refused.status(), await refused.text()).toBe(403)
    })
})
