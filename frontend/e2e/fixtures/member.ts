/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, type Page} from '@playwright/test'
import {apiHeaders} from './auth'
import {remember} from './createdMembers'
import {unique} from './unique'

/**
 * Walks the creation wizard and answers with the surname the new person carries.
 *
 * <p>A story that puts somebody into a group, a tag or a ticket needs a person nobody else touches:
 * the seeded people are shared with every other story running at the same time, and one of them
 * marking somebody former takes that row off the page a waiting story is watching.
 *
 * <p>The wizard does not answer with an id, so the person is found on the list once, here, while the
 * name it was just given is still the name it carries, and noted down for removal.
 *
 * @param page a page signed in as somebody who may create members
 * @return the surname, which is what the lists show and the menu searches by
 */
export async function createMember(page: Page): Promise<string> {
    return (await createIdentifiedMember(page)).surname
}

/**
 * Walks the creation wizard like {@link createMember}, and answers with the id it found as well, for a story
 * that goes on to act on the person through the API.
 *
 * @param page a page signed in as somebody who may create members
 * @return the surname, and the id the person was found under
 */
export async function createIdentifiedMember(page: Page): Promise<{surname: string; id: number}> {
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

    const headers = await apiHeaders(page)
    let id: number | undefined
    await expect.poll(async () => {
        const listed = await page.request.get('/api/v1/station-members/rich', {headers})
        if (!listed.ok()) return undefined
        const rows = await listed.json() as {id: number; lastName?: string}[]
        id = rows.find(member => (member.lastName ?? '') === surname)?.id
        return id
    }, {message: `the member ${surname} the wizard made reaches the list`}).toBeDefined()
    await remember(page, headers, id!)
    return {surname, id: id!}
}
