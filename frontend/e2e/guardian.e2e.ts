/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {APIRequestContext, Page} from '@playwright/test'
import {test, expect, accountWith, pageAsThrowaway} from './fixtures/auth'
import {unique} from './fixtures/unique'

/**
 * What a guardian may do for the members in their care, and what they may not do for anybody else.
 *
 * A guardian speaks for a child: they hand it an address and decide whether it signs in at all.
 * That is a narrow permission and the stories here hold it to its edges — the panel does what it
 * promises, and the endpoints behind it refuse a member the guardian does not manage.
 */
async function guardianPage(browser: Parameters<typeof pageAsThrowaway>[0], request: APIRequestContext): Promise<Page> {
    const guardian = await accountWith(request, 'MEMBER_GUARDIAN')
    return pageAsThrowaway(browser, request, [], guardian)
}

/**
 * The identifier of a member the signed-in guardian manages, asked of the application rather than
 * assumed: which child belongs to which parent is the seeder's business, not the story's.
 */
async function managedMemberId(page: Page): Promise<number> {
    const response = await page.request.get('/api/v1/managed-members')
    expect(response.ok(), 'the guardian can list the members they manage').toBeTruthy()
    const managed = await response.json()
    expect(managed.length, 'the seeded guardian looks after somebody').toBeGreaterThan(0)
    return managed[0].id
}

test.describe('Guardian', () => {
    /**
     * The panel is where a parent does this, so the story goes through it rather than through the
     * endpoint: filling the address in and saving it has to leave the address standing afterwards.
     */
    test('a guardian gives a managed member an address', async ({browser, request}) => {
        const page = await guardianPage(browser, request)
        const address = `${unique('kind').toLowerCase()}@example.test`

        await page.goto('/station/profile/managed')
        await page.getByLabel('Mitglied auswählen').selectOption({index: 1})

        await expect(page.getByText('Zugang')).toBeVisible()
        await page.getByPlaceholder('name@example.org').fill(address)
        await page.getByRole('button', {name: 'Speichern'}).last().click()

        await expect(page.getByText('Adresse gespeichert.')).toBeVisible()
        await page.reload()
        await page.getByLabel('Mitglied auswählen').selectOption({index: 1})
        await expect(page.getByPlaceholder('name@example.org')).toHaveValue(address)

        await page.context().close()
    })

    /**
     * Signing in is the second half, and it hangs on the first: without an address there is nowhere
     * to send the invitation, so the switch stays out of reach until one is set.
     */
    test('signing in can be allowed once there is an address', async ({browser, request}) => {
        const page = await guardianPage(browser, request)
        const memberId = await managedMemberId(page)

        const cleared = await page.request.put(`/api/v1/managed-members/${memberId}/email`, {
            data: {email: `${unique('kind').toLowerCase()}@example.test`},
        })
        expect(cleared.ok()).toBeTruthy()

        const allowed = await page.request.put(`/api/v1/managed-members/${memberId}/login`, {data: {enabled: true}})
        expect(allowed.ok()).toBeTruthy()
        expect((await allowed.json()).loginEnabled).toBe(true)

        const refused = await page.request.put(`/api/v1/managed-members/${memberId}/login`, {data: {enabled: false}})
        expect(refused.ok()).toBeTruthy()
        expect((await refused.json()).loginEnabled).toBe(false)

        await page.context().close()
    })

    /**
     * The part that matters most: everything above is scoped to the members in this guardian's
     * care. A member they do not manage is refused, whichever of the three endpoints is asked.
     */
    test('a member the guardian does not manage is refused', async ({browser, request, managerPage}) => {
        const page = await guardianPage(browser, request)
        const managed = await managedMemberId(page)

        await managerPage.goto('/station/members/list')
        const rows = managerPage.getByTestId('member-row')
        await expect(rows.first()).toBeVisible()

        const strangerId = await managerPage.evaluate(async managedId => {
            const response = await fetch('/api/v1/station-members', {
                headers: {Authorization: `Bearer ${window.localStorage.getItem('session_token')}`},
            })
            const members = await response.json()
            const stranger = (Array.isArray(members) ? members : members.content ?? [])
                .find((member: {id: number}) => member.id !== managedId)
            return stranger?.id ?? null
        }, managed)
        expect(strangerId, 'the station holds somebody besides the managed member').not.toBeNull()

        for (const call of [
            page.request.get(`/api/v1/managed-members/${strangerId}/access`),
            page.request.put(`/api/v1/managed-members/${strangerId}/email`, {data: {email: 'fremd@example.test'}}),
            page.request.put(`/api/v1/managed-members/${strangerId}/login`, {data: {enabled: true}}),
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
        const managed = await managedMemberId(page)

        const response = await page.request.get('/api/v1/profile-changes/all?limit=50')
        expect(response.ok()).toBeTruthy()
        const {changes} = await response.json()

        for (const entry of changes) {
            expect(entry.change.memberId, 'only the members in their care appear').toBe(managed)
        }

        await page.context().close()
    })
})
