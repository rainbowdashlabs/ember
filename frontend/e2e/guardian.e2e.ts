/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {APIRequestContext, Page} from '@playwright/test'
import {test, expect, accountWith, apiHeaders, pageAsThrowaway, stationPeers} from './fixtures/auth'
import {unique} from './fixtures/unique'

/**
 * What a guardian may do for the members in their care, and what they may not do for anybody else.
 *
 * A guardian speaks for a child: they hand it an address and decide whether it signs in at all.
 * That is a narrow permission and the stories here hold it to its edges - the panel does what it
 * promises, and the endpoints behind it refuse a member the guardian does not manage.
 *
 * The stories that ask an endpoint rather than a screen send the session along themselves: a request
 * made straight from the page carries nothing of what the application keeps in the browser, and the
 * server answers it as if nobody had signed in - which would read as the refusal these stories are
 * looking for.
 */
async function guardianPage(browser: Parameters<typeof pageAsThrowaway>[0], request: APIRequestContext): Promise<Page> {
    const guardian = await accountWith(request, 'MEMBER_GUARDIAN')
    return pageAsThrowaway(browser, request, [], guardian)
}

/** Somebody the signed-in guardian looks after. */
interface ManagedMember {
    id: number
    name: string
    email: string
}

/**
 * The members the signed-in guardian manages, asked of the application rather than assumed: which
 * child belongs to which parent is the seeder's business, not the story's.
 */
async function managedMembers(page: Page): Promise<ManagedMember[]> {
    const response = await page.request.get('/api/v1/managed-members', {headers: await apiHeaders(page)})
    expect(response.ok(), 'the guardian can list the members they manage').toBeTruthy()
    const managed = await response.json()
    expect(managed.length, 'the seeded guardian looks after somebody').toBeGreaterThan(0)
    return managed
}

/**
 * One of them to act on - never the account the rest of the suite is signed in as.
 *
 * Giving somebody a new address ends the sessions they have open, which is right: the address is how
 * they sign in. The seeded guardian happens to look after the very member every other story acts as,
 * so a story writing an address for the first of their children would sign the suite out of half of
 * itself.
 */
async function managedMemberToActOn(page: Page, request: APIRequestContext): Promise<ManagedMember> {
    const {member} = await stationPeers(request)
    const managed = await managedMembers(page)
    const spare = managed.find(candidate => candidate.email !== member.email)
    if (!spare) throw new Error('The guardian looks after nobody besides the member the suite signs in as')
    return spare
}

/**
 * Serial, because the stories share the one member the seeded guardian looks after and may be written
 * to. Two of them give that member an address, and run at once they read each other's: the address one
 * of them saves is the address the other is looking at.
 */
test.describe.configure({mode: 'serial'})

test.describe('Guardian', () => {
    /**
     * The panel is where a parent does this, so the story goes through it rather than through the
     * endpoint: filling the address in and saving it has to leave the address standing afterwards.
     */
    test('a guardian gives a managed member an address', async ({browser, request}) => {
        const page = await guardianPage(browser, request)
        const target = await managedMemberToActOn(page, request)
        const address = `${unique('kind').toLowerCase()}@example.test`

        // The words above the picker are text rather than a label an input is tied to, so the picker
        // is the select that offers them as its first, unpickable option - and the child is picked by
        // name, because which of them the story may write to is not a matter of position.
        const picker = page.locator('select:has(option:text-is("Mitglied auswählen"))')

        await page.goto('/station/profile/managed')
        await picker.selectOption({label: target.name})

        await expect(page.getByText('Zugang')).toBeVisible()
        await page.getByPlaceholder('name@example.org').fill(address)
        // The access panel comes before the profile fields, and both end in a save of their own.
        await page.getByRole('button', {name: 'Speichern'}).first().click()

        await expect(page.getByText('Adresse gespeichert.')).toBeVisible()
        await page.reload()
        await picker.selectOption({label: target.name})
        await expect(page.getByPlaceholder('name@example.org')).toHaveValue(address)

        await page.context().close()
    })

    /**
     * Signing in is the second half, and it hangs on the first: without an address there is nowhere
     * to send the invitation, so the switch stays out of reach until one is set.
     */
    test('signing in can be allowed once there is an address', async ({browser, request}) => {
        const page = await guardianPage(browser, request)
        const memberId = (await managedMemberToActOn(page, request)).id
        const headers = await apiHeaders(page)

        const cleared = await page.request.put(`/api/v1/managed-members/${memberId}/email`, {
            headers,
            data: {email: `${unique('kind').toLowerCase()}@example.test`},
        })
        expect(cleared.ok()).toBeTruthy()

        const allowed = await page.request.put(
            `/api/v1/managed-members/${memberId}/login`, {headers, data: {enabled: true}})
        expect(allowed.ok()).toBeTruthy()
        expect((await allowed.json()).loginEnabled).toBe(true)

        const refused = await page.request.put(
            `/api/v1/managed-members/${memberId}/login`, {headers, data: {enabled: false}})
        expect(refused.ok()).toBeTruthy()
        expect((await refused.json()).loginEnabled).toBe(false)

        await page.context().close()
    })

    /**
     * A name of their own is the other way in, and the one that makes a child with no address
     * reachable at all. The story sets one and then signs in with it, because a name that is stored
     * but does not sign anybody in would prove nothing.
     */
    test('a member signs in with the name their guardian gave them', async ({browser, request}) => {
        const page = await guardianPage(browser, request)
        const memberId = (await managedMemberToActOn(page, request)).id
        const headers = await apiHeaders(page)
        const name = unique('kind').toLowerCase()

        const named = await page.request.put(
            `/api/v1/managed-members/${memberId}/username`, {headers, data: {username: name}})
        expect(named.ok()).toBeTruthy()
        expect((await named.json()).username).toBe(name)
        expect((await named.json()).canSignIn).toBe(true)

        const wrongPassword = await page.request.post(
            '/api/v1/auth/login', {data: {identifier: name, password: 'definitely-not-the-password'}})
        expect(wrongPassword.status(), 'the name reaches the login, the password still decides').toBe(401)

        const taken = await page.request.put(
            `/api/v1/managed-members/${memberId}/username`, {headers, data: {username: 'a'}})
        expect(taken.status(), 'a name too short to be one is refused').toBe(400)

        await page.context().close()
    })

    /**
     * The part that matters most: everything above is scoped to the members in this guardian's
     * care. A member they do not manage is refused, whichever of the three endpoints is asked.
     */
    test('a member the guardian does not manage is refused', async ({browser, request, managerPage}) => {
        const page = await guardianPage(browser, request)
        const mine = (await managedMembers(page)).map(member => member.id)
        const headers = await apiHeaders(page)

        await managerPage.goto('/station/members/list')
        const rows = managerPage.getByTestId('member-row')
        await expect(rows.first()).toBeVisible()

        // Somebody outside their care: not one of the children they look after, whichever of them the
        // seeder gave this guardian.
        const strangerId = await managerPage.evaluate(async managedIds => {
            const response = await fetch('/api/v1/station-members', {
                headers: {
                    Authorization: `Bearer ${window.localStorage.getItem('session_token')}`,
                    'X-Station-Id': window.localStorage.getItem('station_id') ?? '',
                },
            })
            const members = await response.json()
            const stranger = (Array.isArray(members) ? members : members.content ?? [])
                .find((member: {id: number}) => !managedIds.includes(member.id))
            return stranger?.id ?? null
        }, mine)
        expect(strangerId, 'the station holds somebody besides the managed member').not.toBeNull()

        for (const call of [
            page.request.get(`/api/v1/managed-members/${strangerId}/access`, {headers}),
            page.request.put(
                `/api/v1/managed-members/${strangerId}/email`, {headers, data: {email: 'fremd@example.test'}}),
            page.request.put(`/api/v1/managed-members/${strangerId}/login`, {headers, data: {enabled: true}}),
        ]) {
            const response = await call
            expect(response.status(), 'a member outside the guardian\'s care is refused').toBe(403)
        }

        await page.context().close()
    })

    /**
     * The profile changes of the station are not a guardian's to read. They see what happened to
     * the members they look after, and the list stops there.
     */
    test('the profile changes a guardian sees stay within their own members', async ({browser, request}) => {
        const page = await guardianPage(browser, request)
        // All of them: a guardian may look after more than one, and every change has to belong to
        // one of those rather than to the first of them.
        const managed = (await managedMembers(page)).map(member => member.id)

        const response = await page.request.get(
            '/api/v1/profile-changes/all?limit=50', {headers: await apiHeaders(page)})
        expect(response.ok()).toBeTruthy()
        const {changes} = await response.json()

        for (const entry of changes) {
            expect(managed, 'only the members in their care appear').toContain(entry.change.memberId)
        }

        await page.context().close()
    })

    /**
     * A guardian answering for a household says it once, and can take one place back without taking
     * the others with it.
     *
     * <p>The dialog appears only where there is more than one person to answer for, ticks everyone by
     * default, and lets one be left out: giving up the place of one child and not the other is the
     * whole reason it exists.
     *
     * <p>Giving a place up, not refusing one. An event that has to be signed up for takes one answer,
     * and not signing up is already the no, so there is nothing to refuse until a place has been taken.
     */
    test('a guardian gives up the place of part of the household in one dialog',
        async ({browser, request, managerPage}) => {
            const page = await guardianPage(browser, request)
            const managed = await managedMembers(page)
            const managerHeaders = await apiHeaders(managerPage)
            const name = `Haushalt ${test.info().workerIndex}-${Date.now()}`

            const created = await managerPage.request.post('/api/v1/events', {
                headers: managerHeaders,
                data: {
                    name,
                    description: 'Haushaltsprobe',
                    eventType: 'ONE_TIME',
                    startTime: new Date(Date.now() + 8 * 86400000).toISOString(),
                    endTime: new Date(Date.now() + 8 * 86400000 + 3600000).toISOString(),
                    requiresRegistration: true,
                    registrationDeadline: new Date(Date.now() + 2 * 86400000).toISOString(),
                },
            })
            expect(created.ok(), `the organiser made an event (${await created.text()})`).toBeTruthy()
            const eventId = (await created.json()).id

            await page.goto(`/station/events/${eventId}`)
            await page.getByRole('button', {name: 'Anmeldungen'}).click()

            const gives = managed[0]!.id

            // Nobody has a place yet, so there is nothing to give up
            await expect(page.getByTestId('withdraw-household')).toHaveCount(0)

            await page.getByTestId('answer-household').click()
            const confirm = page.getByTestId('answer-confirm')
            await expect(confirm).toBeVisible({timeout: 15000})
            await confirm.click()
            await expect(page.getByTestId(`my-answer-${gives}`)).toHaveText(/Bestätigt|Ausstehend/, {timeout: 15000})

            // One of them gives their place back, which deletes it rather than refusing the event.
            // The row is picked by the event, because the household answers several at once.
            await page.goto('/station/events/upcoming')
            const row = page.locator(`[data-testid="upcoming-event"][data-event="${eventId}"]`)
            await expect(row).toHaveCount(1, {timeout: 15000})
            await row.getByTestId(`undo-answer-${gives}`).click()

            await page.goto(`/station/events/${eventId}`)
            await page.getByRole('button', {name: 'Anmeldungen'}).click()
            await expect(page.getByTestId(`my-answer-${gives}`), 'the place is gone, not turned into a refusal')
                .toHaveText('Noch keine Antwort', {timeout: 15000})

            await managerPage.request.delete(`/api/v1/events/${eventId}`, {headers: managerHeaders})
            await page.context().close()
        })
})
