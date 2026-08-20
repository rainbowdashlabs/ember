/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'
import {unique} from './fixtures/unique'

/**
 * What the instance says to every station at once.
 *
 * <p>A system entry is one row belonging to no station. That is the whole difference, and it is
 * what these stories check: written once in the administration, read in a station's own news list
 * as if it had always been there, and gone from every list when it is withdrawn.
 */
test.describe('System news', () => {
    /**
     * The point of the feature in one walk: an instance administrator writes a notice, and a member
     * of a station who knows nothing about the administration finds it among their own news, from
     * Ember rather than from anyone in their station.
     */
    test('an entry written by the instance is read in a station', async ({adminPage, managerPage}) => {
        const notice = unique('Wartungsarbeiten')

        await adminPage.goto('/admin/news')
        await adminPage.getByRole('button', {name: 'Systemmeldung schreiben'}).click()
        await adminPage.getByPlaceholder('Titel der Systemmeldung').fill(notice)
        const body = adminPage.locator('[contenteditable="true"]').first()
        await body.click()
        await adminPage.keyboard.type('Am Freitag kurz nicht erreichbar.')
        await adminPage.getByRole('button', {name: 'Veröffentlichen'}).click()

        await expect(adminPage.getByText(notice).first()).toBeVisible()

        await managerPage.goto('/station/news')
        await expect(managerPage.getByText(notice).first()).toBeVisible()
        await expect(managerPage.getByText('Ember').first()).toBeVisible()
    })

    /**
     * Withdrawing is the other half of publishing everywhere: it has to disappear everywhere, and
     * the station never had a copy of its own to keep.
     */
    test('a withdrawn entry is gone from the station that was reading it', async ({adminPage, managerPage}) => {
        const notice = unique('Zurückgezogen')

        await adminPage.goto('/admin/news')
        await adminPage.getByRole('button', {name: 'Systemmeldung schreiben'}).click()
        await adminPage.getByPlaceholder('Titel der Systemmeldung').fill(notice)
        await adminPage.locator('[contenteditable="true"]').first().click()
        await adminPage.keyboard.type('Doch nicht.')
        await adminPage.getByRole('button', {name: 'Veröffentlichen'}).click()
        await expect(adminPage.getByText(notice).first()).toBeVisible()

        await managerPage.goto('/station/news')
        await expect(managerPage.getByText(notice).first()).toBeVisible()

        // The row of this entry alone: several notices are on the page, and under the full suite
        // several more from other workers, so the button has to be the one inside this row.
        await adminPage.getByTestId('system-news').filter({hasText: notice})
            .getByRole('button', {name: 'Löschen'}).click()
        await adminPage.getByRole('button', {name: 'Löschen', exact: true}).last().click()
        await expect(adminPage.getByTestId('system-news').filter({hasText: notice})).toHaveCount(0)

        await managerPage.goto('/station/news')
        await expect(managerPage.getByText(notice)).toHaveCount(0)
    })

    /**
     * The restriction is by user type alone, because groups and tags are things one station has and
     * the entry is read in all of them. A notice for managers is not in a plain member's list.
     */
    test('an entry restricted to a user type is not read by everyone', async ({adminPage, memberPage}) => {
        const notice = unique('Nur Betreuer')
        const forAll = unique('Für alle')

        // An entry nobody is shut out of, so the absence asserted below is the restriction working
        // rather than this member never seeing a system entry at all.
        await adminPage.goto('/admin/news')
        await adminPage.getByRole('button', {name: 'Systemmeldung schreiben'}).click()
        await adminPage.getByPlaceholder('Titel der Systemmeldung').fill(forAll)
        await adminPage.locator('[contenteditable="true"]').first().click()
        await adminPage.keyboard.type('Für jeden lesbar.')
        await adminPage.getByRole('button', {name: 'Veröffentlichen'}).click()
        await expect(adminPage.getByText(forAll).first()).toBeVisible()

        await adminPage.goto('/admin/news')
        await adminPage.getByRole('button', {name: 'Systemmeldung schreiben'}).click()
        await adminPage.getByPlaceholder('Titel der Systemmeldung').fill(notice)
        await adminPage.locator('[contenteditable="true"]').first().click()
        await adminPage.keyboard.type('Nur für die Leitung.')
        await adminPage.getByRole('button', {name: 'Manager', exact: true}).click()
        await adminPage.getByRole('button', {name: 'Veröffentlichen'}).click()
        await expect(adminPage.getByText(notice).first()).toBeVisible()

        await memberPage.goto('/station/news')
        await expect(memberPage.getByText(forAll).first()).toBeVisible()
        await expect(memberPage.getByText(notice)).toHaveCount(0)
    })
})
