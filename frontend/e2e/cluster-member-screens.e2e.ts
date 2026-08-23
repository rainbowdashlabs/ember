/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect, clusterAccountWith, clusterPage} from './fixtures/auth'

/**
 * The two lists an association keeps of people, and the screen behind one of them.
 *
 * The people who run the association and the people at its stations were both called members and both
 * shown as a stack of rows. They are different things and are now shown differently, which is what
 * these stories walk.
 */
test.describe('Cluster member screens', () => {
    /**
     * CLS-63 - The people at the stations are shown in the station's own table.
     *
     * Search, sortable columns and a column picker, none of which the old stack of rows had. The
     * station column is the one thing the station's own list never needs.
     */
    test('the association browses station members in a real table', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_MEMBER_MANAGER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/members')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        // The table's own rows, not a hand rolled stack.
        await expect(page.getByTestId('member-row').first()).toBeVisible({timeout: 15000})

        // Narrowing to one station is offered, because more than one is in view.
        await expect(page.locator('select').first()).toBeVisible()
        await page.context().close()
    })

    /**
     * CLS-64 - A station's own questions are not offered as columns.
     *
     * Stations declare their own, so a union would offer a picker where most columns are empty for
     * most rows. Only what the association asks of everybody can honestly be a column.
     */
    test('no station-local column is offered across the stations', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_MEMBER_MANAGER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/members')
        await expect(page.getByTestId('member-row').first()).toBeVisible({timeout: 15000})

        // Groups and tags belong to one station and are never a column here.
        await expect(page.getByRole('columnheader', {name: /Gruppen/i})).toHaveCount(0)
        await expect(page.getByRole('columnheader', {name: /Tags/i})).toHaveCount(0)
        await page.context().close()
    })

    /**
     * CLS-65 - The roster opens on the roster.
     *
     * The old screen opened on a form to add somebody, with the people below it. The people come
     * first now and adding is a dialog.
     */
    test('the association team opens on its people', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_ADMINISTRATOR')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/team')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        await expect(page.getByTestId('roster-row').first()).toBeVisible({timeout: 15000})

        // Adding is behind a button, so no email box is sitting on the page.
        await expect(page.getByPlaceholder(/@/)).toHaveCount(0)
        await page.context().close()
    })

    /**
     * CLS-66 - Picking somebody shows where each of their rights comes from.
     *
     * Type, their own grants and their groups are three different sources, and the resolved set is
     * what they come to together.
     */
    test('a person on the team shows their rights and where they came from', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_ADMINISTRATOR')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/team')
        await page.getByTestId('roster-row').first().click()

        await expect(page.getByText(/Rechte|Gruppen/).first()).toBeVisible({timeout: 15000})
        await page.context().close()
    })

    /**
     * CLS-67 - Opening somebody shows what is asked of them, from both sides at once.
     *
     * The station's questions and the association's in one form. This screen could not exist until
     * the endpoints behind it were written, which is the whole reason it is here.
     */
    test('the association answers the questions asked of one person', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_MEMBER_MANAGER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/members')
        const row = page.getByTestId('member-row').first()
        await expect(row).toBeVisible({timeout: 15000})
        await row.getByRole('button').first().click()

        await expect(page).toHaveURL(/\/cluster\/members\/\d+$/)
        await expect(page.getByText(/Angaben/i)).toBeVisible({timeout: 15000})
        await page.context().close()
    })

    /**
     * CLS-68 - The old addresses still work.
     *
     * Members came to mean the people at the stations, so the list moved up an address and the roster
     * moved out to one of its own.
     */
    test('the renamed member screens keep their old addresses working', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_MEMBER_MANAGER')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/members/manage')
        await expect(page).toHaveURL(/\/cluster\/members$/)

        await page.context().close()
    })

    /**
     * CLS-69 - The association exports the people across its stations.
     *
     * The station's own column picker and export modal, mounted whole and guarded by the association's
     * export right. It was written down as decided and never walked, which left an open question about
     * whether the button did anything: it hands over a file built from the rows on screen, so there is
     * no endpoint behind it to be missing.
     */
    test('the association exports the members it can see', async ({browser, request}) => {
        const account = await clusterAccountWith(request, 'CLUSTER_MEMBER_EXPORT')
        const page = await clusterPage(browser, request, account)

        await page.goto('/cluster/members')
        await expect(page.getByTestId('member-row').first()).toBeVisible({timeout: 15000})

        await page.getByTestId('members-export').click()
        // Every row on screen, which is what an association exports rather than one station's worth
        await page.getByTestId('member-select-all').click()
        await page.getByTestId('members-export-continue').click()

        const download = page.waitForEvent('download')
        await page.getByTestId('members-export-download').click()
        const file = await download

        expect(file.suggestedFilename()).toBe('verbandsmitglieder.csv')
        await page.context().close()
    })
})
