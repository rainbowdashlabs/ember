/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

test.describe('Two-factor', () => {
    test('a member reaches their own security settings', async ({memberPage: page}) => {
        await page.goto('/account/security')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        expect(page.url()).toContain('/account/security')
    })

    test('a member reaches the sessions they hold', async ({memberPage: page}) => {
        await page.goto('/account/sessions')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    /**
     * The overview exists to find whoever is not protected yet, which only means something once
     * somebody is: the seeder switches two-factor on for one team member, so the list has both kinds
     * in it and the count is not zero.
     *
     * Read on the station's own security page rather than under the operator's two-factor entry -
     * that one resets a single account and keeps the audit log.
     */
    test('the station sees who has two-factor and who has not', async ({managerPage: page}) => {
        await page.goto('/station/manage/security')

        await expect(page.getByText(/\d+ von \d+ eingerichtet/)).toBeVisible()
        await expect(page.getByText('Eingerichtet').first()).toBeVisible()
        await expect(page.getByText('Optional').first()).toBeVisible()
    })

    /** The operator's own entry: a single account's two-factor is reset from there. */
    test('the operator can reset the two-factor of an account', async ({adminPage: page}) => {
        await page.goto('/admin/2fa')

        await expect(page.getByText('Konto-2FA zurücksetzen')).toBeVisible()
        await expect(page.getByRole('button', {name: '2FA zurücksetzen'})).toBeVisible()
    })
})
