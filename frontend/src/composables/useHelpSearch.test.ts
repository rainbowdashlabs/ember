/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {readdirSync, readFileSync, statSync} from 'node:fs'
import {join, relative} from 'node:path'
import {describe, expect, it} from 'vitest'
import {buildHelpSearchIndex, HELP_PAGE_MAP} from './useHelpSearch'

const PAGES = join(import.meta.dirname, '..', 'pages', 'helpcenter')

/** Every route name a help page answers at, read off the page files rather than off the map. */
function routeNames(): Map<string, string> {
    const found = new Map<string, string>()
    const walk = (dir: string): void => {
        for (const entry of readdirSync(dir)) {
            const path = join(dir, entry)
            if (statSync(path).isDirectory()) {
                walk(path)
                continue
            }
            if (!entry.endsWith('.vue')) continue
            const name = /name: '([^']+)'/.exec(readFileSync(path, 'utf8'))?.[1]
            if (name) found.set(name, relative(PAGES, path))
        }
    }
    walk(PAGES)
    return found
}

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

    /**
     * The rule the other three cannot state, because they all start from the map. A page the map has
     * never heard of is a page the search can never answer with, and the map was ninety-one behind the
     * tree when this was written.
     */
    it('has an entry for every page in the tree', () => {
        const pages = routeNames()
        const mapped = new Set(HELP_PAGE_MAP.map(page => page.route))
        const missing = [...pages].filter(([route]) => !mapped.has(route)).map(([, file]) => file)
        expect(missing, 'a page in no map is a page the search cannot answer with').toEqual([])
    })

    it('names each page once', () => {
        const seen = new Set<string>()
        const twice = HELP_PAGE_MAP.filter(page => !seen.add(page.route)).map(page => page.route)
        expect(twice, 'two entries for one route put the page in the results twice').toEqual([])
    })

    it('points every entry at a page that exists', () => {
        const pages = routeNames()
        const dangling = HELP_PAGE_MAP.filter(page => !pages.has(page.route)).map(page => page.route)
        expect(dangling, 'an entry for a page nobody can reach is a dead result').toEqual([])
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
