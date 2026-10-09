/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, type Page} from '@playwright/test'
import {DEMO_PASSWORD} from './auth'

/** A few strokes on the drawing pad, which is all a signature picture needs. */
async function drawSignature(page: Page) {
    const box = await page.getByTestId('signature-canvas').boundingBox()
    if (!box) throw new Error('The drawing pad has no size')
    await page.mouse.move(box.x + box.width * 0.2, box.y + box.height * 0.6)
    await page.mouse.down()
    for (let step = 1; step <= 10; step++) {
        await page.mouse.move(box.x + box.width * (0.2 + step * 0.06), box.y + box.height * (step % 2 ? 0.35 : 0.65))
    }
    await page.mouse.up()
}

/**
 * Walks the signing screens from the overview to the end: every field ticked, every detail typed,
 * a picture drawn wherever none is kept, the check, and the seeded password as the one confirmation.
 * Only for somebody without a second factor, whose confirmation is the password.
 *
 * <p>Each screen is waited for before it is read: the confirmation loads once the signatures are
 * started on the server, and pressing on before it is there presses a button that has gone.
 */
export async function signInOneGo(page: Page) {
    await expect(page.getByTestId('signing-overview'), 'something waits to be signed').toBeVisible({timeout: 30_000})
    await page.getByTestId('signing-start').click()
    const proof = page.getByTestId('signing-proof')
    const next = page.getByTestId('signing-next')
    for (let screen = 0; screen < 12; screen++) {
        await expect(next.or(proof).first()).toBeVisible({timeout: 15_000})
        if (await proof.isVisible()) break
        for (const box of await page.locator('[data-testid^="signing-agree-"]').all()) {
            if (!(await box.isChecked())) await box.check()
        }
        for (const entry of await page.locator('[data-testid^="signing-fill-in-"]').all()) {
            if (!(await entry.inputValue())) await entry.fill('0171 2345678')
        }
        if (await page.getByTestId('signature-canvas').isVisible()) await drawSignature(page)
        await next.click()
    }
    await proof.locator('input[type="password"]').fill(DEMO_PASSWORD)
    await proof.locator('form').filter({has: page.locator('input[type="password"]')})
        .locator('button[type="submit"]').click()
    await expect(page.getByTestId('signing-done')).toBeVisible({timeout: 30_000})
}
