/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, accountWith, pageAsThrowaway} from './fixtures/auth'
import {sidebarEntry} from './fixtures/sidebar'

/**
 * What a right does to the sidebar. A page the reader may not open must not be offered to them in
 * the first place: an entry that leads to a refusal is worse than no entry, because the reader
 * cannot tell whether they did something wrong.
 *
 * Every story checks both sides at once. Asserting only that the member does not see something
 * would pass just as well if the entry had been renamed and nobody saw it any more.
 */
const MANAGER_ONLY = [
    {name: 'station settings', entry: 'Verwalten'},
    {name: 'federation', entry: 'Föderation'},
    {name: 'the public pages', entry: 'Öffentliche Seiten'},
    {name: 'the member list', entry: 'Mitglieder'},
    {name: 'attendance', entry: 'Anwesenheit'},
]

test.describe('Sidebar permissions', () => {
    for (const point of MANAGER_ONLY) {
        test(`${point.name} stays out of a member's sidebar`, async ({managerPage, memberPage}) => {
            await managerPage.goto('/station/dashboard/overview')
            await expect(sidebarEntry(managerPage, point.entry)).toBeVisible()

            await memberPage.goto('/station/dashboard/overview')
            await expect(sidebarEntry(memberPage, 'Dashboard')).toBeVisible()
            await expect(sidebarEntry(memberPage, point.entry)).toHaveCount(0)
        })
    }

    /**
     * Subpoints are held to the same rule as the points above them. A guardian looks after somebody
     * else's profile and is given the entry for it; whoever looks after nobody is not.
     */
    test('the managed profiles of a guardian stay out of a member\'s sidebar', async ({browser, request, memberPage}) => {
        const guardian = await accountWith(request, 'MEMBER_GUARDIAN')
        const guardianPage = await pageAsThrowaway(browser, request, [], guardian)

        // The subpoints of a group only render while the group is open, and a group opens on the
        // routes it holds.
        await guardianPage.goto('/station/profile')
        await expect(sidebarEntry(guardianPage, 'Verwaltete Profile')).toBeVisible()

        await memberPage.goto('/station/profile')
        await expect(sidebarEntry(memberPage, 'Abwesenheit')).toBeVisible()
        await expect(sidebarEntry(memberPage, 'Verwaltete Profile')).toHaveCount(0)

        await guardianPage.context().close()
    })

    /**
     * The manager's own side of the same rule: the member group opens on every subpoint the station
     * has, and each of them is a right somebody could be without.
     */
    test('a manager is offered every subpoint of the member group', async ({managerPage: page}) => {
        await page.goto('/station/members/list')

        for (const subpoint of ['Anlegen', 'Gruppen', 'Tags', 'Typberechtigungen', 'Ehemalige']) {
            await expect(sidebarEntry(page, subpoint)).toBeVisible()
        }
    })
})
