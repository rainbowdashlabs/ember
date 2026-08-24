/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {test, expect} from './fixtures/auth'

/**
 * The help centre, and the only way into it that is not clicking down a tree.
 *
 * Nothing covered it at all: no spec, no test beside the composable, no check that the map of pages and
 * the page tree agree. The search was written working and emptied by a change somewhere else, when the
 * help text moved into a chunk merged at runtime and the index went on being built from the file it had
 * left. It answered nothing for any word on any of its pages but one.
 */
test.describe('Help centre', () => {
    /**
     * Types into the search box and waits for it to answer.
     *
     * Retyped until it does, because the page is server rendered: the box is on screen and focused
     * before Vue is listening to it, so a word typed at that moment is simply lost. The first search of
     * a visit also fetches the chunk the whole help text lives in, which under four workers queues
     * behind everything else the server is serving.
     */
    async function search(page: Page, word: string) {
        const results = page.getByTestId('help-search-result')
        await expect(async () => {
            await page.getByPlaceholder(/Such/i).first().fill(word)
            await expect(results.first()).toBeVisible({timeout: 5000})
        }).toPass({timeout: 60000})
        return results
    }

    /**
     * CLS-102 - The search answers a word the help centre uses.
     *
     * *Termine* appears in the help text more than a hundred times. Results come back, more than one
     * page is among them, and the association's own pages are reachable, which they were not even
     * before the index broke: none of its 42 pages was in the map at all.
     */
    test('the help centre search answers a word its own pages use', async ({managerPage: page}) => {
        await page.goto('/helpcenter/station/basics')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        const results = await search(page, 'Termine')
        expect(await results.count(), 'more than one page uses the word').toBeGreaterThan(1)
    })

    /**
     * CLS-103 - The association's help is findable.
     *
     * Its 42 pages were in no map, so repairing the index alone would still have left every one of them
     * unreachable by search, which for the association is the half that matters.
     */
    test('the association help pages are in the search', async ({managerPage: page}) => {
        await page.goto('/helpcenter/station/basics')
        await expect(page.getByTestId('app-shell')).toBeVisible()

        const results = await search(page, 'Verband')
        await expect(results.filter({hasText: 'Verband'}).first(),
            'the association is named in what comes back').toBeVisible()
    })
})
