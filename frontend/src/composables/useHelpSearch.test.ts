/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {readdirSync, readFileSync, statSync} from 'node:fs'
import {join, relative} from 'node:path'
import {describe, expect, it} from 'vitest'
import {buildHelpSearchIndex} from './useHelpSearch'
import {HELP_PAGES} from './helpPages.generated'

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
    it('holds an entry for every page, not some of them', async () => {
        const index = await buildHelpSearchIndex()
        const missing = HELP_PAGES.filter(page => !index.some(entry => entry.route === page.route))
        expect(missing.map(page => page.i18nPrefix), 'a prefix that resolves to nothing is a page nobody can find')
            .toEqual([])
    })

    /**
     * The promise the generated index makes that the hand-written one could not: a page is in the search
     * because it exists. Nobody adds it, so nobody can forget to.
     */
    it('carries text for every page in the tree', async () => {
        const index = await buildHelpSearchIndex()
        const silent = index.filter(entry => entry.text.trim().length === 0).map(entry => entry.route)
        expect(silent, 'a page in the index with no text answers no search').toEqual([])
    })

    it('says where a page sits, ending with the page itself', async () => {
        const index = await buildHelpSearchIndex()
        const wrong = index.filter(entry => !entry.section.endsWith(entry.title)).map(entry => entry.route)
        expect(wrong, 'the last part of the breadcrumb is the page you are looking at').toEqual([])
    })

    it('finds a word the help text certainly contains', async () => {
        const index = await buildHelpSearchIndex()
        const found = index.filter(entry => entry.text.toLowerCase().includes('termine'))
        expect(found.length, 'Termine is in the help text more than a hundred times').toBeGreaterThan(1)
    })

    /**
     * The rule the others cannot state, because they all start from the index. A page the index has
     * never heard of is a page the search can never answer with, and the list was ninety-one behind the
     * tree when it was still written by hand.
     */
    it('has an entry for every page in the tree', () => {
        const pages = routeNames()
        const mapped = new Set(HELP_PAGES.map(page => page.route))
        const missing = [...pages].filter(([route]) => !mapped.has(route)).map(([, file]) => file)
        expect(missing, 'a page in no map is a page the search cannot answer with').toEqual([])
    })

    it('names each page once', () => {
        const seen = new Set<string>()
        const twice = HELP_PAGES.filter(page => !seen.add(page.route)).map(page => page.route)
        expect(twice, 'two entries for one route put the page in the results twice').toEqual([])
    })

    it('points every entry at a page that exists', () => {
        const pages = routeNames()
        const dangling = HELP_PAGES.filter(page => !pages.has(page.route)).map(page => page.route)
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
