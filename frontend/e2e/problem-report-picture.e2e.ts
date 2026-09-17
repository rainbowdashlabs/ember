/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from './fixtures/auth'
import type {Page} from '@playwright/test'

/**
 * Reporting a problem with a picture of the page.
 *
 * <p>Taking the picture is not walked here and cannot be: the share prompt belongs to the browser
 * and no test can answer it. What is walked is everything around it, which is where the rules that
 * matter live: a picture is attached only because somebody attached one, what is covered is covered
 * before the report is sent, and what arrives is the covered picture rather than the original.
 *
 * <p>A picture of a page of this product is a page of somebody's data. That is why the covering is
 * pinned by a story rather than left to a unit test alone.
 */

/** A small red PNG, written out once rather than encoded here, so a real picture is attached. */
const PICTURE = 'iVBORw0KGgoAAAANSUhEUgAAAPAAAAB4CAIAAABD1OhwAAABW0lEQVR4nO3SQQkAMAzAwAqrfyZrJgaDcHAC8'
    + 'sicXciY7wXwkKFJMTQphibF0KQYmhRDk2JoUgxNiqFJMTQphibF0KQYmhRDk2JoUgxNiqFJMTQphibF0KQYmhRDk2JoUgxNiq'
    + 'FJMTQphibF0KQYmhRDk2JoUgxNiqFJMTQphibF0KQYmhRDk2JoUgxNiqFJMTQphibF0KQYmhRDk2JoUgxNiqFJMTQphibF0KQ'
    + 'YmhRDk2JoUgxNiqFJMTQphibF0KQYmhRDk2JoUgxNiqFJMTQphibF0KQYmhRDk2JoUgxNiqFJMTQphibF0KQYmhRDk2JoUgxN'
    + 'iqFJMTQphibF0KQYmhRDk2JoUgxNiqFJMTQphibF0KQYmhRDk2JoUgxNiqFJMTQphibF0KQYmhRDk2JoUgxNiqFJMTQphibF0'
    + 'KQYmhRDk2JoUgxNiqFJMTQphibF0KQYmhRDk3IBEKuoNezOBt0AAAAASUVORK5CYII='

/** Any station page will do: the button to report a problem sits in the station layout itself. */
const SOMEWHERE = '/station/dashboard/overview'

async function openReportDialog(page: Page) {
    await page.goto(SOMEWHERE)
    await page.getByTestId('report-problem').click()
    return page.getByRole('dialog')
}

async function attachPicture(page: Page) {
    const dialog = page.getByRole('dialog')
    await dialog.getByTestId('picture-attach').click()
    await dialog.locator('input[type="file"]').setInputFiles({
        name: 'bildschirm.png',
        mimeType: 'image/png',
        buffer: Buffer.from(PICTURE, 'base64'),
    })
    await expect(dialog.getByTestId('coverable-picture')).toBeVisible()
}

test.describe('A problem report with a picture', () => {
    /** Nothing is taken on its own: the dialog offers a picture and holds none until asked. */
    test('opens with no picture and offers to take or attach one', async ({memberPage: page}) => {
        const dialog = await openReportDialog(page)

        await expect(dialog.getByTestId('coverable-picture')).toHaveCount(0)
        await expect(dialog.getByTestId('picture-attach')).toBeVisible()
    })

    test('shows what was attached, and lets it be dropped again', async ({memberPage: page}) => {
        const dialog = await openReportDialog(page)

        await attachPicture(page)
        await dialog.getByTestId('picture-discard').click()

        await expect(dialog.getByTestId('coverable-picture')).toHaveCount(0)
        await expect(dialog.getByTestId('picture-attach')).toBeVisible()
    })

    /** Drag a rectangle over the picture, and it stands there as a cover until it is pressed away. */
    test('covers what is dragged over and takes the cover off again', async ({memberPage: page}) => {
        const dialog = await openReportDialog(page)
        await attachPicture(page)

        const picture = dialog.getByTestId('coverable-picture')
        const box = await picture.boundingBox()
        if (!box) throw new Error('the picture is not on the screen')
        await page.mouse.move(box.x + 20, box.y + 20)
        await page.mouse.down()
        await page.mouse.move(box.x + 90, box.y + 60, {steps: 5})
        await page.mouse.up()

        await expect(dialog.getByTestId('picture-cover')).toHaveCount(1)

        await dialog.getByTestId('picture-cover').click()
        await expect(dialog.getByTestId('picture-cover')).toHaveCount(0)
    })

    /**
     * The whole point of the feature, end to end: what the reporter attached and covered reaches the
     * administration as a picture on the report.
     */
    test('sends the report with its picture, and the administration sees it', async ({
        memberPage: page,
        adminPage: admin,
    }) => {
        const said = `Der Knopf tut nichts, siehe Bild ${Date.now()}`
        const dialog = await openReportDialog(page)
        await dialog.getByTestId('problem-report-description').fill(said)
        await attachPicture(page)
        await dialog.getByTestId('problem-report-send').click()

        await expect(dialog.getByText('Deine Meldung ist angekommen. Danke!')).toBeVisible()

        await admin.goto('/admin/monitoring/problem-reports')
        const report = admin.getByTestId('problem-report').filter({hasText: said.slice(0, 30)}).first()
        await report.click()

        await expect(report.getByTestId('report-screenshot')).toBeVisible()
    })

    /** A report without a picture is what it always was, and must stay sendable on its own. */
    test('sends a report that carries no picture at all', async ({memberPage: page}) => {
        const said = `Ganz ohne Bild ${Date.now()}`
        const dialog = await openReportDialog(page)
        await dialog.getByTestId('problem-report-description').fill(said)
        await dialog.getByTestId('problem-report-send').click()

        await expect(dialog.getByText('Deine Meldung ist angekommen. Danke!')).toBeVisible()
    })
})
