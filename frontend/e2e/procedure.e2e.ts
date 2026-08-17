/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect} from './fixtures/auth'
import {unique} from './fixtures/unique'

/**
 * A procedure of the story's own with one step in it, left open on its page — which is where
 * creating one lands. Every story makes its own: the stories run side by side, and one of them
 * ticking a step off the procedure another one is reading would be a story about the other story.
 */
async function createProcedureWithStep(page: Page): Promise<string> {
    const procedure = unique('Ablauf')

    await page.goto('/station/procedures')
    await page.getByRole('button', {name: 'Neuer Ablauf'}).click()
    await page.waitForURL(/\/station\/procedures\/create/)

    await page.getByRole('textbox').first().fill(procedure)
    await page.getByRole('button', {name: 'Schritt hinzufügen'}).click()
    await page.getByPlaceholder('Titel').first().fill('Erster Schritt')

    // The button that saves a new procedure carries the same words as the one that opened the page
    // for it, so the story takes the one on the page it is standing on.
    await page.getByRole('button', {name: 'Neuer Ablauf'}).last().click()
    await page.waitForURL(/\/station\/procedures\/\d+$/)

    return procedure
}

/**
 * Creating a procedure opens a page of its own rather than a dialog, because a procedure is a list
 * of steps. Adding one appends an empty row that is written in place, so the story writes into the
 * row rather than answering a dialog about it.
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

    test('a procedure is created', async ({managerPage: page}) => {
        const procedure = await createProcedureWithStep(page)

        await page.goto('/station/procedures')
        await expect(page.getByText(procedure).first()).toBeVisible()
    })

    /**
     * A procedure exists to be worked through. The story makes its own, ticks its step off and
     * reloads: a tick that does not survive one was never recorded, and the person following the
     * procedure would do the step twice.
     */
    test('a step of a procedure is ticked off and stays ticked', async ({managerPage: page}) => {
        await createProcedureWithStep(page)

        const check = page.getByRole('button', {name: 'Erledigt', exact: true}).first()
        await expect(check).toBeVisible()
        await check.click()

        await expect(page.getByRole('button', {name: 'Zurücksetzen'}).first()).toBeVisible()

        await page.reload()
        await expect(page.getByRole('button', {name: 'Zurücksetzen'}).first()).toBeVisible()
    })

    /**
     * A template exists so the steps do not have to be typed again. The story loads one, reads the
     * step it brought along, and looks for that step in the procedure it then creates.
     */
    test('a template brings its steps into a new procedure', async ({managerPage: page}) => {
        const procedure = unique('Ablauf')

        await page.goto('/station/procedures/create')

        await page.locator('select').first().selectOption({index: 1})

        const firstStep = page.getByPlaceholder('Titel').first()
        await expect(firstStep).toBeVisible()
        const stepTitle = await firstStep.inputValue()
        expect(stepTitle).not.toBe('')

        await page.getByRole('textbox').first().fill(procedure)
        await page.getByRole('button', {name: 'Neuer Ablauf'}).last().click()

        await page.waitForURL(/\/station\/procedures\/\d+$/)
        await expect(page.getByText(stepTitle).first()).toBeVisible()
    })

    test('the procedure templates are reachable', async ({managerPage: page}) => {
        await page.goto('/station/procedures/templates')

        await expect(page.getByTestId('app-shell')).toBeVisible()
    })
})
