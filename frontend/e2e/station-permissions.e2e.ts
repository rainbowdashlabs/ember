/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'
import {unique} from './fixtures/unique'

/**
 * PERM-1 and PERM-3 of the story list.
 *
 * PERM-2 — granting a right and watching the page appear for the member holding it — needs both
 * roles live at once, which the fixtures now provide from one station. It follows once the group
 * editor's permission tree has stable anchors; guessing at checkbox labels would make it a test of
 * the label rather than of the permission.
 */
test.describe('Permissions', () => {
    test('PERM-1 a group is created', async ({managerPage: page}) => {
        const group = unique('Gruppe')

        await page.goto('/station/members/groups')
        await page.getByRole('button', {name: 'Neue Gruppe'}).click()

        await page.getByRole('textbox').first().fill(group)
        await page.getByRole('button', {name: /Speichern|Erstellen/}).last().click()

        await expect(page.getByText(group)).toBeVisible()
    })

    /**
     * A permission is not a hidden button. The member area has to refuse the page outright, which
     * is what stops a guessed address from working.
     */
    test('PERM-3 a member reaches none of the management pages', async ({memberPage: page}) => {
        for (const path of ['/station/members/groups', '/station/manage', '/station/members/type-permissions']) {
            await page.goto(path)
            await expect(page.getByRole('button', {name: /erstellen/})).toHaveCount(0)
        }
    })
})
