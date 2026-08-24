/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {buildHelpSearchIndex, HELP_PAGE_MAP} from './useHelpSearch'

/**
 * What the help center search promises about its own index.
 *
 * <p>The search was written working and emptied by a change somewhere else: the help text moved into a
 * chunk merged at runtime, and the index went on being built from the file it had left. It answered
 * nothing for any word on any of 257 pages but one, and nothing anywhere noticed. That is the failure a
 * unit test exists for.
 */
describe('the help center search index', () => {
    it('holds an entry for every page in the map, not some of them', async () => {
        const index = await buildHelpSearchIndex()
        const missing = HELP_PAGE_MAP.filter(page => !index.some(entry => entry.route === page.route))
        expect(missing.map(page => page.i18nPrefix), 'a prefix that resolves to nothing is a page nobody can find')
            .toEqual([])
    })

    it('finds a word the help text certainly contains', async () => {
        const index = await buildHelpSearchIndex()
        const found = index.filter(entry => entry.text.toLowerCase().includes('termine'))
        expect(found.length, 'Termine is in the help text more than a hundred times').toBeGreaterThan(1)
    })

    it('reaches the association, whose pages were in no map at all', async () => {
        const index = await buildHelpSearchIndex()
        const association = index.filter(entry => entry.path.startsWith('/helpcenter/cluster'))
        expect(association.length, 'the association has help of its own and it has to be findable')
            .toBeGreaterThan(30)
        expect(association.every(entry => entry.text.length > 0), 'and every one of them carries text')
            .toBeTruthy()
    })
})
