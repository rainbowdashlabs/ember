/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, type Page} from './fixtures/auth'
import {unique} from './fixtures/unique'

/**
 * The templates a station writes its events from, and the questions on them.
 *
 * <p>A template is edited once and then used for a year, so the things that go wrong here are the
 * things nobody notices until they are needed: a question in the wrong place, a question asking for a
 * member of a group without saying which group, and a second template that has to be typed out again
 * because the first one could only be copied by hand.
 */

/** Opens the template whose name is given, by way of the list every station starts on. */
async function openTemplate(page: Page, name: string) {
    await page.goto('/station/events/templates')
    const row = page.locator(`[data-testid="template-row"][data-name="${name}"]`).first()
    await row.waitFor()
    await row.getByRole('button').first().click()
    await page.waitForURL(/\/station\/events\/templates\/\d+/)
    await page.getByTestId('event-field-name').first().waitFor()
}

/** The questions in the order the editor lists them, which is the order they will be asked in. */
async function fieldNames(page: Page): Promise<string[]> {
    return page.getByTestId('event-field-name').evaluateAll(
        inputs => inputs.map(input => (input as HTMLInputElement).value),
    )
}

test.describe('Event templates', () => {
    /**
     * A question moves past its neighbour and stays there.
     *
     * <p>Reordering used to mean deleting everything after the misplaced question and typing it in
     * again, so the story reads the order back after a reload rather than trusting the screen.
     */
    test('a question can be moved and the new order is kept', async ({managerPage: page}) => {
        await openTemplate(page, 'Standard-Übung')

        const before = await fieldNames(page)
        expect(before.length, 'the seeded template asks at least two things').toBeGreaterThan(1)

        await page.getByTestId('move-down').first().click()
        await expect.poll(() => fieldNames(page)).toEqual([before[1], before[0], ...before.slice(2)])

        await Promise.all([
            page.waitForResponse(response =>
                response.url().includes('/fields') && response.request().method() === 'PUT'),
            page.getByRole('button', {name: 'Speichern'}).click(),
        ])
        await page.reload()
        await page.getByTestId('event-field-name').first().waitFor()

        expect(await fieldNames(page)).toEqual([before[1], before[0], ...before.slice(2)])
    })

    /**
     * A question that asks for a member of a group says which group.
     *
     * <p>The choice was missing entirely, which left a question that could be saved and then answered
     * with nobody: the group it draws from was never written down.
     */
    test('a question for a member of a group offers the groups', async ({managerPage: page}) => {
        await openTemplate(page, 'Standard-Übung')

        await page.getByRole('button', {name: 'Feld hinzufügen'}).click()
        const added = page.getByTestId('event-field-name').last()
        await added.fill('Ausbilder')

        await page.getByTestId('event-field-type').last().selectOption({label: 'Mitglied aus Gruppe'})

        const groups = page.getByTestId('event-field-group').last()
        await expect(groups).toBeVisible()
        await expect(groups.locator('option')).not.toHaveCount(1)
    })

    /**
     * A question set to half a row is drawn as half a row in the preview.
     *
     * <p>A width is guesswork while the editor is a column of rows, so the preview is what makes the
     * choice answerable at all.
     */
    test('the preview shows how wide the questions are', async ({managerPage: page}) => {
        await openTemplate(page, 'Standard-Übung')

        const preview = page.getByTestId('field-layout-preview')
        await expect(preview).toBeVisible()

        await page.getByTestId('field-width').first().selectOption('half')
        await expect(preview.getByTestId('preview-box').first()).toHaveClass(/sm:col-span-3/)

        await Promise.all([
            page.waitForResponse(response =>
                response.url().includes('/fields') && response.request().method() === 'PUT'),
            page.getByRole('button', {name: 'Speichern'}).click(),
        ])
        await page.reload()
        await page.getByTestId('field-width').first().waitFor()

        await expect(page.getByTestId('field-width').first(), 'the width outlives the save').toHaveValue('half')
    })

    /**
     * Copying a template rather than typing the second one out.
     *
     * <p>Stations run two or three variations of the same evening, and the only way to get the second
     * one was to enter every question again.
     */
    test('a template can be duplicated', async ({managerPage: page}) => {
        await page.goto('/station/events/templates')
        const original = page.locator('[data-testid="template-row"][data-name="Standard-Übung"]').first()
        await original.waitFor()
        const questions = await (async () => {
            await openTemplate(page, 'Standard-Übung')
            const names = await fieldNames(page)
            await page.goto('/station/events/templates')
            return names
        })()

        await original.getByLabel('Duplizieren').click()
        await page.waitForURL(/\/station\/events\/templates\/\d+/)
        await page.getByTestId('event-field-name').first().waitFor()

        expect(await fieldNames(page), 'the copy asks the same things').toEqual(questions)

        await page.goto('/station/events/templates')
        await expect(page.locator('[data-testid="template-row"][data-name="Standard-Übung (Kopie)"]')
            .first()).toBeVisible()
    })

    /**
     * The attendance sheet a template names travels with it onto the appointment.
     *
     * <p>A template said which sheet the attendance is taken on, and applying it to an appointment
     * left that empty, so whoever applied it set the same thing again by hand. The story sets the
     * sheet on the template, applies the template to a new appointment, and reads the appointment's
     * own field back.
     */
    test('applying a template brings its attendance sheet along', async ({managerPage: page}) => {
        // A template of its own rather than one of the seeded ones: the story saves it, and the
        // stories running beside it read the seeded templates as they stand.
        const name = unique('Vorlage')

        await page.goto('/station/events/templates')
        await page.getByRole('button', {name: 'Vorlage erstellen'}).first().click()
        await page.getByPlaceholder('z.B. Standard-Übung').fill(name)
        await page.getByRole('button', {name: 'Vorlage erstellen'}).last().click()
        await page.waitForURL(/\/station\/events\/templates\/\d+/)

        const onTemplate = page.getByTestId('template-attendance-template')
        await onTemplate.selectOption({index: 1})
        const sheet = await onTemplate.inputValue()
        const save = page.locator('.save-button').last()
        await save.click()
        await expect(save).toHaveClass(/bg-success/)

        await page.goto('/station/events/new')
        await page.getByTestId('apply-template').selectOption({label: name})

        await expect(page.getByTestId('event-attendance-template'),
            'the appointment takes the sheet the template named').toHaveValue(sheet)
    })
})
