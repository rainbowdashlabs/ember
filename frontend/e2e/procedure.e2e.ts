/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'
import {unique} from './fixtures/unique'

/**
 * Creating a procedure opens a page of its own rather than a dialog, because a procedure is a list
 * of steps, and it will not save without at least one.
 *
 * The story is held back on the step editor: the button that adds a step is in the page and cannot
 * be clicked — something sits over it — so the procedure never becomes saveable. Whether that also
 * stops a person with a mouse is worth finding out, and is the reason this is recorded rather than
 * deleted.
 */
test.describe('Procedures', () => {
    /**
     * Editing a procedure went to an address the backend did not answer on, so the save reported
     * nothing and changed nothing. The story walks the same path a person does: open one, change
     * its name, and find the new name after a reload.
     */
    test('a procedure is renamed', async ({managerPage: page}) => {
        const renamed = unique('Ablauf')

        await page.goto('/station/procedures')

        // The list navigates by click handler, so its rows carry an identifier to aim at.
        await page.getByTestId('procedure-entry').first().click()
        await page.waitForURL(/\/station\/procedures\/\d+/)
        await page.goto(`${page.url()}/edit`)

        await page.getByRole('textbox').first().fill(renamed)
        await page.getByRole('button', {name: 'Speichern'}).first().click()

        await page.reload()
        await expect(page.getByRole('textbox').first()).toHaveValue(renamed)
    })

    test.fixme('a procedure is created', async ({managerPage: page}) => {
        const procedure = unique('Ablauf')

        await page.goto('/station/procedures')
        await page.getByRole('button', {name: 'Neuer Ablauf'}).click()
        await page.waitForURL(/\/station\/procedures\/create/)

        await page.getByRole('textbox').first().fill(procedure)

        await page.getByRole('button', {name: 'Schritt hinzufügen'}).click()
        await page.getByPlaceholder('Titel').first().fill('Erster Schritt')

        await page.getByRole('button', {name: 'Speichern'}).click()

        await page.goto('/station/procedures')
        await expect(page.getByText(procedure).first()).toBeVisible()
    })

    test('the procedure templates are reachable', async ({managerPage: page}) => {
        await page.goto('/station/procedures/templates')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })
})
