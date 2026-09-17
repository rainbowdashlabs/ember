/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders} from './fixtures/auth'
import {ownMember} from './fixtures/ownMember'

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
     * Whoever keeps the station's members may put a name right, including one they did not write.
     *
     * A name somebody is unhappy to have been given has to be correctable by the people who run the
     * place, and refusing them would leave the member with only the person who named them to ask.
     * What keeps this from becoming a way to label people is not a refusal but a record: who wrote
     * the name is kept on the row, which is what `nickname_set_by` is for. That record is not read
     * back by any screen yet, so this story stops where the product does, at the name itself.
     */
    test('a manager may name a member they do not look after', async ({managerPage: page}) => {
        const headers = await apiHeaders(page)
        // Somebody this story made. A name is read by whoever is asserting one elsewhere, and
        // putting it back afterwards does not help: the window between is where the other worker
        // looks, and a story that fails never reaches the putting back at all.
        const somebodyElse = await ownMember(page, 'Spitzname')

        const named = await page.request.put(`/api/v1/station-members/${somebodyElse.memberId}/nickname`, {
            headers,
            data: {nickname: 'Sprosse'},
        })
        expect(named.status(), await named.text()).toBe(204)

        const after = (await (await page.request.get('/api/v1/station-members/rich', {headers})).json())
            .find((m: {id: number}) => m.id === somebodyElse.memberId)
        expect(after.nickname, 'the name the station now calls them by').toBe('Sprosse')
    })
})
