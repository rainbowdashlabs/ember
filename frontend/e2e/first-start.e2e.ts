/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from '@playwright/test'

/**
 * The step that stands between an administrator nobody can write to and the application.
 *
 * The seeded administrator carries a made-up address, which is what a first start used to hand out,
 * so it is the one account on the instance that owes a real one. The story signs in with the form
 * rather than with the one-click login beside it: the one-click login is a demo convenience that
 * skips the sign-in entirely, and the step lives in the sign-in.
 *
 * It stops short of actually setting the address. Setting it ends every session of that account,
 * and every other story here holds one.
 */
test.describe('First start', () => {
    const MADE_UP = 'admin@ember.local'
    const PASSWORD = 'demo'

    async function signIn(page: import('@playwright/test').Page, identifier: string) {
        await page.goto('/login')
        await page.getByRole('button', {name: 'Zustimmen'}).click()
        await page.getByPlaceholder('E-Mail oder Benutzername').fill(identifier)
        await page.getByPlaceholder('Passwort', {exact: true}).fill(PASSWORD)
        await page.getByRole('button', {name: 'Anmelden', exact: true}).click()
    }

    test('an administrator with a made-up address is sent to give a real one', async ({page}) => {
        await signIn(page, MADE_UP)

        await expect(page).toHaveURL(/\/set-address/)
        await expect(page.getByText('E-Mail-Adresse hinterlegen').first()).toBeVisible()
    })

    test('there is no session until the address is given', async ({page}) => {
        await signIn(page, MADE_UP)
        await expect(page).toHaveURL(/\/set-address/)

        await page.goto('/station/dashboard/overview')

        await expect(page).toHaveURL(/\/login/)
    })

    /**
     * This instance has no mail provider, which is the state a freshly installed one is in. A
     * confirmation asked for by mail could never arrive there, so it counts as given: the account is
     * verified as it is made, and signs in straight away instead of waiting for a link forever.
     */
    test('an instance that cannot send asks for no verification', async ({request}) => {
        const email = `no-mail-${Date.now()}@example.org`
        const password = 'EinLangesPasswort2026!'

        const registered = await request.post('/api/v1/auth/register', {
            data: {email, firstName: 'Ohne', lastName: 'Post', password},
        })
        expect(registered.status()).toBe(201)
        expect((await registered.json()).emailVerified).toBe(true)

        const login = await request.post('/api/v1/auth/login', {data: {identifier: email, password}})

        expect(login.status()).toBe(200)
        expect((await login.json()).token).toBeTruthy()
    })

    test('another made-up address is refused at the step', async ({page}) => {
        await signIn(page, MADE_UP)
        await expect(page).toHaveURL(/\/set-address/)

        await page.getByTestId('set-address-email').fill('somebody@else.local')
        await page.getByRole('button', {name: 'Adresse hinterlegen und anmelden'}).click()

        await expect(page.getByText('An diese Adresse kann nichts zugestellt werden.')).toBeVisible()
        await expect(page).toHaveURL(/\/set-address/)
    })
})
