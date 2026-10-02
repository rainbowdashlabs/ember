/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, apiHeaders} from './fixtures/auth'
import {unique} from './fixtures/unique'
import {createMember} from './fixtures/member'
import type {Page} from '@playwright/test'

/** Opens the edit page of the member the wizard just created, found in the list by their surname. */
async function openEditPage(page: Page, surname: string) {
    await page.goto('/station/members/list')
    await page.getByPlaceholder(/Suche/).first().fill(surname)
    await page.getByTestId('member-row').first().getByRole('button', {name: 'Details'}).click()
    await page.waitForURL(/\/station\/members\/detail\/(\d+)/)
    const id = page.url().match(/detail\/(\d+)/)?.[1]
    await page.goto(`/station/members/edit/${id}`)
}

/** Creates a group with its rules over the API, which is what the groups page sends. */
async function createGroup(page: Page, name: string, rules: {groupSetId: number | null; userTypes: string[]}) {
    const headers = await apiHeaders(page)
    const response = await page.request.post('/api/v1/groups', {headers, data: {name, position: 0, rules}})
    expect(response.ok(), `group ${name} is created`).toBeTruthy()
}

/** Clicks a group chip and waits until the member's groups are saved. */
async function clickAndSave(page: Page, name: string) {
    const saved = page.waitForResponse(res =>
        res.request().method() === 'PUT' && /\/station-members\/\d+\/groups$/.test(new URL(res.url()).pathname))
    await page.getByRole('button', {name, exact: true}).click()
    await saved
}

test.describe('Groups', () => {
    /**
     * A set is made on the groups page and two groups are put into it; a member joins one of them and
     * then moves to the other from their own page, where the set is one choice. Becoming a team member
     * afterwards takes them out of a group bound to members, after the page has said so.
     *
     * <p>Everybody and everything is the story's own: the seeded groups are shared with every other
     * story, and one of them changing a binding would break this one.
     */
    test('a set is made, a member moves within it and a type change leaves a bound group',
        async ({managerPage: page}) => {
            const setName = unique('Stufen')
            await page.goto('/station/members/groups')
            await page.getByRole('button', {name: 'Neues Gruppenset'}).click()
            await page.getByPlaceholder('Name des Gruppensets').fill(setName)
            await page.getByRole('dialog').getByRole('button', {name: 'Speichern'}).click()
            await expect(page.getByText(setName).first()).toBeVisible()

            const headers = await apiHeaders(page)
            const sets = await (await page.request.get('/api/v1/group-sets', {headers})).json() as
                {id: number; name: string}[]
            const setId = sets.find(set => set.name === setName)!.id
            const first = unique('Anfang')
            const second = unique('Weiter')
            const bound = unique('Kinder')
            await createGroup(page, first, {groupSetId: setId, userTypes: []})
            await createGroup(page, second, {groupSetId: setId, userTypes: []})
            await createGroup(page, bound, {groupSetId: null, userTypes: ['MEMBER']})

            await openEditPage(page, await createMember(page))
            await page.getByRole('tab', {name: 'Berechtigungen'}).click()
            const chip = (name: string) => page.getByRole('button', {name, exact: true})

            await clickAndSave(page, first)
            await expect(chip(first)).toHaveAttribute('aria-pressed', 'true')
            await clickAndSave(page, second)
            await expect(chip(second)).toHaveAttribute('aria-pressed', 'true')
            await expect(chip(first)).toHaveAttribute('aria-pressed', 'false')
            await clickAndSave(page, bound)
            await expect(chip(bound)).toHaveAttribute('aria-pressed', 'true')

            await page.getByRole('combobox').first().selectOption('TEAM')
            const confirm = page.getByRole('dialog').filter({hasText: 'Mitgliedstyp ändern'})
            await expect(confirm.getByText(bound)).toBeVisible()
            await confirm.getByRole('button', {name: 'Ändern und aus den Gruppen nehmen'}).click()

            await expect(chip(bound)).toHaveAttribute('aria-pressed', 'false')
            await expect(chip(bound)).toBeDisabled()
            await expect(chip(second)).toHaveAttribute('aria-pressed', 'true')
        })
})
