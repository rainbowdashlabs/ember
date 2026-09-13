/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, type Page} from '@playwright/test'
import {unique} from './unique'

/**
 * Walks the creation wizard and answers with the surname the new person carries.
 *
 * <p>A story that puts somebody into a group, a tag or a ticket needs a person nobody else touches:
 * the seeded people are shared with every other story running at the same time, and one of them
 * marking somebody former takes that row off the page a waiting story is watching.
 *
 * @param page a page signed in as somebody who may create members
 * @return the surname, which is what the lists show and the menu searches by
 */
export async function createMember(page: Page): Promise<string> {
    const surname = unique('Story')

    await page.goto('/station/members/create')
    await expect(page.getByTestId('app-shell')).toBeVisible()

    await page.getByRole('button', {name: 'Weiter'}).first().click()

    await page.getByPlaceholder('Vorname').fill('Testperson')
    await page.getByPlaceholder('Nachname').fill(surname)
    await page.getByPlaceholder('E-Mail-Adresse').fill(`${surname.toLowerCase()}@example.test`)
    await page.getByRole('button', {name: 'Weiter'}).first().click()

    for (let step = 0; step < 4; step += 1) {
        const next = page.getByRole('button', {name: /Weiter|Konto erstellen|Erstellen/}).first()
        if (!await next.isVisible().catch(() => false)) break
        await next.click()
    }

    return surname
}
