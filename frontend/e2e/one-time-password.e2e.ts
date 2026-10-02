/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Browser, Page} from '@playwright/test'
import {apiHeaders, expect, pageAsThrowaway, test} from './fixtures/auth'
import {cast} from './fixtures/cast'
import {remember} from './fixtures/createdMembers'
import {createIdentifiedMember} from './fixtures/member'
import {unique} from './fixtures/unique'

/**
 * One-time passwords on an instance that sends no mail, which is the state the suite's instance is
 * in: no setup link ever reaches a new member, so the station's administration hands over a
 * password instead, and the instance's administration can do the same for any account.
 *
 * <p>Each story ends with the person signing in for the first time, because that is the point of
 * the password: it opens the account once, asks for a password of their own, and only then lets
 * them in.
 */

const CHOSEN = 'Selbst-Gewaehlt-2026!'

/** The access details the dialog shows, read off it the way a person reads them off the screen. */
async function handedOver(page: Page): Promise<{loginName: string; password: string}> {
    const dialog = page.getByTestId('one-time-password-dialog')
    await expect(dialog).toBeVisible({timeout: 15_000})
    const values = dialog.locator('dd')
    const loginName = (await values.nth(0).textContent())?.trim() ?? ''
    const password = (await values.nth(1).textContent())?.trim() ?? ''
    expect(password, 'the password comes in four groups of four').toMatch(/^[a-z2-9]{4}(-[a-z2-9]{4}){3}$/)
    return {loginName, password}
}

/**
 * The first sign-in with a one-time password, in a browser that knows nobody: it is sent to choose a
 * password of its own and lands in the application once it has.
 */
async function firstSignIn(browser: Browser, loginName: string, password: string) {
    const context = await browser.newContext()
    const page = await context.newPage()
    await page.addInitScript(() => window.localStorage.setItem('storage_consent', 'accepted'))
    await page.goto('/login')
    await page.getByPlaceholder('E-Mail oder Benutzername').fill(loginName)
    await page.getByPlaceholder('Passwort', {exact: true}).fill(password)
    await page.getByRole('button', {name: 'Anmelden', exact: true}).click()

    await expect(page, 'the one-time password asks for a new one first').toHaveURL(/\/set-password/, {timeout: 15_000})
    await page.getByPlaceholder('Neues Passwort').fill(CHOSEN)
    await page.getByPlaceholder('Passwort bestätigen').fill(CHOSEN)
    await page.getByRole('button', {name: 'Passwort festlegen'}).click()

    await expect(page, 'a password of their own lets them in').not.toHaveURL(/\/set-password|\/login/, {timeout: 15_000})
    await context.close()
}

/** The one-time password a second time, after it was replaced: it opens nothing any more. */
async function refusedAgain(browser: Browser, loginName: string, password: string) {
    const context = await browser.newContext()
    const page = await context.newPage()
    await page.addInitScript(() => window.localStorage.setItem('storage_consent', 'accepted'))
    await page.goto('/login')
    await page.getByPlaceholder('E-Mail oder Benutzername').fill(loginName)
    await page.getByPlaceholder('Passwort', {exact: true}).fill(password)
    await page.getByRole('button', {name: 'Anmelden', exact: true}).click()
    await expect(page.getByText('Die Anmeldung hat nicht geklappt', {exact: false})).toBeVisible({timeout: 15_000})
    await context.close()
}

test.describe('One-time passwords', () => {
    /**
     * OTP-1 - A station without mail creates a member with a one-time password, the member signs in
     * with it, chooses a password of their own and is in. The one-time password works that one time.
     */
    test('a new member signs in with the one-time password from the wizard', async ({browser, request}) => {
        const page = await pageAsThrowaway(browser, request, [], (await cast()).administrator)
        const surname = unique('Einmal')

        await page.goto('/station/members/create')
        await expect(page.getByTestId('app-shell')).toBeVisible()
        await page.getByRole('button', {name: 'Weiter'}).first().click()

        await page.getByPlaceholder('Vorname').fill('Lena')
        await page.getByPlaceholder('Nachname').fill(surname)
        await page.getByPlaceholder('E-Mail-Adresse').fill(`${surname.toLowerCase()}@example.test`)
        await expect(page.getByTestId('setup-mail-impossible')).toBeVisible()
        await expect(page.getByTestId('one-time-password-choice'), 'the wizard offers the password instead')
            .toBeVisible()
        await page.getByRole('button', {name: 'Weiter'}).first().click()

        const done = page.getByText('Konto erstellt')
        for (let step = 0; step < 6; step += 1) {
            if (await done.isVisible().catch(() => false)) break
            const next = page.getByRole('button', {name: /^(Weiter|Konto erstellen|Erstellen)$/}).first()
            if (!await next.isVisible().catch(() => false)) break
            await next.click()
        }

        const {loginName, password} = await handedOver(page)
        expect(loginName).toBe(`${surname.toLowerCase()}@example.test`)

        const headers = await apiHeaders(page)
        const listed = await page.request.get('/api/v1/station-members/rich', {headers})
        const made = (await listed.json() as {id: number; lastName?: string}[])
            .find(member => (member.lastName ?? '') === surname)
        expect(made, 'the member the wizard made is on the roll').toBeDefined()
        await remember(page, headers, made!.id)
        await page.context().close()

        await firstSignIn(browser, loginName, password)
        await refusedAgain(browser, loginName, password)
    })

    /**
     * OTP-2 - The instance's administration finds an account in the account list and issues it a
     * one-time password, which the account then signs in with.
     */
    test('the account list issues a one-time password', async ({adminPage, managerPage, browser}) => {
        const {surname} = await createIdentifiedMember(managerPage)

        await adminPage.goto('/admin/accounts')
        await adminPage.getByTestId('account-search').fill(surname)
        const row = adminPage.getByTestId('account-row').filter({hasText: surname})
        await expect(row).toHaveCount(1, {timeout: 15_000})
        await row.click()

        const detail = adminPage.getByTestId('account-detail')
        await expect(detail).toBeVisible()
        await detail.getByTestId('one-time-password-action').click()
        await adminPage.getByTestId('one-time-password-confirm').click()

        const {loginName, password} = await handedOver(adminPage)
        expect(loginName).toBe(`${surname.toLowerCase()}@example.test`)

        await firstSignIn(browser, loginName, password)
    })
})
