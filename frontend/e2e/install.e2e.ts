/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from '@playwright/test'

/**
 * Putting an installation together before there is anything to log in to.
 *
 * The page and both ends behind it are public on purpose: whoever is installing Ember has no
 * account yet, and the whole point is that it runs on a machine where nothing exists.
 */
test.describe('Install page', () => {
    test('a stranger can put an installation together and gets a code', async ({page}) => {
        await page.goto('/install')

        await expect(page.getByText('Erreichbarkeit')).toBeVisible()

        await page.getByRole('button', {name: 'Code erzeugen'}).click()

        // Six characters from an alphabet that leaves out anything easy to misread.
        await expect(page.getByText(/^[23456789BCDFGHJKLMNPQRSTVWXZ]{6}$/)).toBeVisible()
        await expect(page.getByText(/curl .*install\.sh \| bash -s/)).toBeVisible()
    })

    /** Traefik has nothing to route without a hostname, so the code is not offered until there is one. */
    test('the traefik arrangement asks for a hostname first', async ({page}) => {
        await page.goto('/install')

        await page.getByRole('combobox').first().selectOption('traefik')

        await expect(page.getByRole('button', {name: 'Code erzeugen'})).toBeDisabled()

        await page.getByPlaceholder('ember.example.org').fill('ember.example.org')

        await expect(page.getByRole('button', {name: 'Code erzeugen'})).toBeEnabled()
    })

    /**
     * The script reads what comes back straight into its own environment, so what may be stored is
     * the whole of what a stranger could put into somebody else's shell.
     */
    test('a preset keeps only the answers the installer knows', async ({request}) => {
        const created = await request.post('/api/v1/public/install', {
            data: {options: {EMBER_MODE: 'port', EMBER_PORT: '8080', EMBER_SOMETHING_ELSE: 'rm -rf /'}},
        })
        const {code} = await created.json()

        const fetched = await request.get(`/api/v1/public/install/${code}`)
        const body = await fetched.text()

        expect(body).toContain('EMBER_MODE=port')
        expect(body).toContain('EMBER_PORT=8080')
        expect(body).not.toContain('EMBER_SOMETHING_ELSE')
        expect(body).not.toContain('rm -rf')
    })

    test('a code that never existed is refused', async ({request}) => {
        const response = await request.get('/api/v1/public/install/ZZZZZZ')

        expect(response.status()).toBe(404)
    })
})
