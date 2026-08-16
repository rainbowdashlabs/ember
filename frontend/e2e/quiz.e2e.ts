/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'
import {unique} from './fixtures/unique'

/** Both halves of the quiz are open to whoever configures them. */
test.describe('Quiz', () => {
    test('a question catalogue is created', async ({managerPage: page}) => {
        const catalogue = unique('Katalog')

        await page.goto('/station/quiz/catalogs')
        await page.getByRole('button', {name: 'Neuer Katalog'}).click()

        await page.getByRole('textbox').first().fill(catalogue)
        await page.getByRole('button', {name: /Speichern|Erstellen|Anlegen/}).last().click()

        await expect(page.getByText(catalogue).first()).toBeVisible()
    })

    /** A catalogue opens on its categories, which is where its questions live. */
    test('a created catalogue opens', async ({managerPage: page}) => {
        const catalogue = unique('Katalog')

        await page.goto('/station/quiz/catalogs')
        await page.getByRole('button', {name: 'Neuer Katalog'}).click()
        await page.getByRole('textbox').first().fill(catalogue)
        await page.getByRole('button', {name: /Speichern|Erstellen|Anlegen/}).last().click()
        await expect(page.getByText(catalogue).first()).toBeVisible()

        await page.getByText(catalogue).first().click()

        await page.waitForURL(/\/station\/quiz\/catalogs\/\d+/)
        await expect(page.getByTestId('app-shell')).toBeVisible()
    })

    /** A catalogue holds its questions in categories, so a fresh one starts by getting one. */
    test('a category is added to a catalogue', async ({managerPage: page}) => {
        const catalogue = unique('Katalog')
        const category = unique('Kategorie')

        await page.goto('/station/quiz/catalogs')
        await page.getByRole('button', {name: 'Neuer Katalog'}).click()
        await page.getByRole('textbox').first().fill(catalogue)
        await page.getByRole('button', {name: /Speichern|Erstellen|Anlegen/}).last().click()
        await page.getByText(catalogue).first().click()
        await page.waitForURL(/\/station\/quiz\/catalogs\/\d+/)

        await page.getByRole('button', {name: 'Neue Kategorie'}).click()
        await page.getByPlaceholder('Kategoriename').fill(category)
        await page.getByRole('button', {name: /Speichern|Erstellen|Anlegen|Neue Kategorie/}).last().click()

        await expect(page.getByText(category).first()).toBeVisible()
    })

    test('the test sheets are reachable', async ({managerPage: page}) => {
        await page.goto('/station/quiz/tests')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        expect(page.url()).toContain('/station/quiz/tests')
    })

    /**
     * The point of a test sheet is somebody sitting it. The seeded station carries an active one,
     * and a member is offered it directly in the list — the row navigates by click handler, so it
     * carries an identifier for the story to aim at.
     */
    test('a member is offered an active test and can begin it', async ({memberPage: page}) => {
        await page.goto('/station/quiz/tests')

        const row = page.getByTestId('test-entry').first()
        await expect(row).toBeVisible()

        await row.getByRole('button', {name: 'Test schreiben'}).click()

        await page.waitForURL(/\/station\/quiz\/tests\/\d+\/take/)
        await expect(page.getByRole('button', {name: 'Test abgeben'})).toBeVisible()
    })

    /** Training asks questions without recording an attempt, so it is open to everyone. */
    test('a member reaches the training', async ({memberPage: page}) => {
        await page.goto('/station/quiz/training')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })
})
