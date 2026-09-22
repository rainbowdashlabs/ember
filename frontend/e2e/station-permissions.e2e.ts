/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'
import {unique} from './fixtures/unique'
import {createMember} from './fixtures/member'
import {pickMemberByName} from './fixtures/memberMenu'

/**
 * Granting a right and watching the page appear for the member holding it needs both
 * roles live at once, which the fixtures now provide from one station. It follows once the group
 * editor's permission tree has stable anchors; guessing at checkbox labels would make it a test of
 * the label rather than of the permission.
 */
test.describe('Permissions', () => {
    /**
     * A group is only useful once somebody is in it, and the members tab is where they go in.
     *
     * <p>Somebody of this story's own goes in, rather than whoever the menu offers first: the seeded
     * people are shared with every story running beside it, and one of them marking that person former
     * took the row this story was waiting for off the page.
     *
     * <p>Picking the member shows them at once and saves in the background, so the story waits for
     * that save before reloading: a reload that comes first cuts the save off, and the group is left
     * empty.
     */
    test('a member is put into a group', async ({managerPage: page}) => {
        const group = unique('Gruppe')
        const surname = await createMember(page)

        await page.goto('/station/members/groups')
        await page.getByRole('button', {name: 'Neue Gruppe'}).click()
        await page.getByRole('textbox').first().fill(group)
        await page.getByRole('button', {name: /Speichern|Erstellen/}).last().click()
        await page.getByText(group).first().click()

        const saved = page.waitForResponse(res =>
            res.request().method() === 'PUT' && /\/groups\/\d+\/members$/.test(new URL(res.url()).pathname))
        await pickMemberByName(page, surname)
        await saved

        await page.reload()
        await page.getByText(group).first().click()
        await expect(page.getByText(surname).first()).toBeVisible()
    })

    test('a group is created', async ({managerPage: page}) => {
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
    test('a member reaches none of the management pages', async ({memberPage: page}) => {
        for (const path of ['/station/members/groups', '/station/manage', '/station/members/type-permissions']) {
            await page.goto(path)
            await expect(page.getByRole('button', {name: /erstellen/})).toHaveCount(0)
        }
    })
})
