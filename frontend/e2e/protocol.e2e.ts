/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'
import {unique} from './fixtures/unique'

/** The dialog's submit repeats the label of the button that opened it, as several others do. */
test.describe('Protocols', () => {
    test('a test sheet is created', async ({managerPage: page}) => {
        const sheet = unique('Pruefungsbogen')

        await page.goto('/station/protocols')
        await page.getByRole('button', {name: 'Neuer Prüfungsbogen'}).click()

        await page.getByRole('textbox').first().fill(sheet)
        await page.getByRole('button', {name: 'Neuer Prüfungsbogen'}).last().click()

        await expect(page.getByText(sheet).first()).toBeVisible()
    })

    /**
     * A test sheet exists to be run: the run is where people are actually examined. The dialog
     * needs the sheet chosen as well as a name — it keeps its submit disabled until both are there.
     */
    test('a run is opened for a test sheet', async ({managerPage: page}) => {
        const sheet = unique('Bogen');
        const run = unique('Lauf')

        await page.goto('/station/protocols')
        await page.getByRole('button', {name: 'Neuer Prüfungsbogen'}).click()
        await page.getByRole('textbox').first().fill(sheet)
        await page.getByRole('button', {name: 'Neuer Prüfungsbogen'}).last().click()
        await expect(page.getByText(sheet).first()).toBeVisible()

        await page.goto('/station/protocols/runs')
        await page.getByRole('button', {name: 'Neuer Prüfungslauf'}).click()

        await page.getByRole('combobox').first().selectOption({label: sheet})
        await page.getByPlaceholder('Name des Laufs').fill(run)
        await page.getByRole('button', {name: 'Neuer Prüfungslauf'}).last().click()

        await expect(page.getByText(run).first()).toBeVisible()
    })

    /**
     * A run with nobody in it examines nobody, so the story puts a whole member type into it and
     * then opens the grading of the first person listed.
     */
    test('a member of a run is opened for grading', async ({managerPage: page}) => {
        const sheet = unique('Bogen');
        const run = unique('Lauf')

        await page.goto('/station/protocols')
        await page.getByRole('button', {name: 'Neuer Prüfungsbogen'}).click()
        await page.getByRole('textbox').first().fill(sheet)
        await page.getByRole('button', {name: 'Neuer Prüfungsbogen'}).last().click()
        await expect(page.getByText(sheet).first()).toBeVisible()

        await page.goto('/station/protocols/runs')
        await page.getByRole('button', {name: 'Neuer Prüfungslauf'}).click()
        await page.getByRole('combobox').first().selectOption({label: sheet})
        await page.getByPlaceholder('Name des Laufs').fill(run)

        await page.getByRole('button', {name: 'Mitgliedstyp'}).click()
        await page.getByRole('button', {name: 'Mitglied', exact: true}).click()
        await page.keyboard.press('Escape')

        await page.getByRole('button', {name: 'Neuer Prüfungslauf'}).last().click()
        await expect(page.getByText(run).first()).toBeVisible()

        await page.getByText(run).first().click()
        await page.waitForURL(/\/station\/protocols\/runs\/\d+/)

        await page.getByRole('button', {name: 'Prüfen'}).first().click()
        await page.waitForURL(/\/grade\/\d+/)
    })

    test('the protocol list offers a new test sheet to whoever runs them', async ({managerPage: page}) => {
        await page.goto('/station/protocols')

        await expect(page.getByTestId('app-shell')).toBeVisible()
        await expect(page.getByRole('button', {name: 'Neuer Prüfungsbogen'})).toBeVisible()
    })

    test('a member is not offered a new test sheet', async ({memberPage: page}) => {
        await page.goto('/station/protocols')

        await expect(page.getByRole('button', {name: 'Neuer Prüfungsbogen'})).toHaveCount(0)
    })
})
