/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, type Page} from './fixtures/auth'

/**
 * Sorting a list works the same way wherever a list can be sorted.
 *
 * <p>Two arrows on every row, because they are the only thing a finger can use, and a grip beside them
 * wherever there is a mouse. The stories read the browser's own answer about the pointer rather than
 * branching on which device they run as, so the same story holds on the phone and on the desktop.
 */

/** Whether this browser says it points with something as precise as a mouse. */
async function hasMouse(page: Page): Promise<boolean> {
    return page.evaluate(() => window.matchMedia('(pointer: fine)').matches)
}

/** The questions of an event template, each one a card rather than a line. */
async function openTemplateQuestions(page: Page) {
    await page.goto('/station/events/templates')
    await page.locator('[data-testid="template-row"][data-name="Standard-Übung"]').first()
        .getByRole('button').first().click()
    await page.waitForURL(/\/station\/events\/templates\/\d+/)
    await page.getByTestId('event-field-name').first().waitFor()
}

/** The questions of the first attendance sheet, which is a list every station has. */
async function openAttendanceFields(page: Page) {
    await page.goto('/station/attendance/config')
    await page.getByLabel('Bearbeiten').first().click()
    await page.waitForURL(/\/station\/attendance\/config\/edit\/\d+/)
    await page.getByTestId('attendance-field-row').first().waitFor()
}

test.describe('Sorting', () => {
    test('every row offers the arrows, and the grip only where there is a mouse', async ({managerPage: page}) => {
        await openAttendanceFields(page)

        const fields = page.getByTestId('attendance-fields')
        const rows = await fields.getByTestId('attendance-field-row').count()
        expect(rows, 'the seeded sheet asks something').toBeGreaterThan(0)

        await expect(fields.getByTestId('move-up')).toHaveCount(rows)
        await expect(fields.getByTestId('move-down')).toHaveCount(rows)
        await expect(fields.getByTestId('drag-handle')).toHaveCount(await hasMouse(page) ? rows : 0)
    })

    /**
     * The first row cannot go up and the last cannot go down, which is what keeps the arrows honest
     * about where a row already is.
     */
    test('the arrows at the ends of the list are refused', async ({managerPage: page}) => {
        await openAttendanceFields(page)

        const fields = page.getByTestId('attendance-fields')
        await expect(fields.getByTestId('move-up').first()).toBeDisabled()
        await expect(fields.getByTestId('move-down').last()).toBeDisabled()
    })

    /**
     * A row of one line has its controls beside it, and a row with room to spare has them stacked.
     *
     * <p>The two screens are the two shapes a sortable row comes in: a line naming a question, and a
     * card holding the whole form for one. Standing the controls up in a card puts each of them where
     * the eye looks for it and makes it a larger thing to press.
     */
    test('tall rows stand their controls up and short rows lay them out beside', async ({managerPage: page}) => {
        await openAttendanceFields(page)
        await expect(page.getByTestId('attendance-fields').getByTestId('drag-controls').first())
            .toHaveAttribute('data-layout', 'inline')

        await openTemplateQuestions(page)
        await expect(page.getByTestId('drag-controls').first()).toHaveAttribute('data-layout', 'stacked')
    })

    /**
     * The same two arrows on a screen configured somewhere else entirely: what a station asks about
     * an event. One shape of control, however the list is stored.
     */
    test('the questions of an event template sort the same way', async ({managerPage: page}) => {
        await openTemplateQuestions(page)

        const questions = await page.getByTestId('event-field-name').count()
        await expect(page.getByTestId('move-up')).toHaveCount(questions)
        await expect(page.getByTestId('drag-handle')).toHaveCount(await hasMouse(page) ? questions : 0)
    })
})
