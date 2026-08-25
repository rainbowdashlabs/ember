/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment node
import {readdirSync, readFileSync, statSync} from 'node:fs'
import {join, relative} from 'node:path'
import {describe, expect, it} from 'vitest'

/**
 * What every sidebar in Ember promises about its own addresses.
 *
 * <p>A sweep found ten places where a group and its entries disagreed: a group dark while one of its own
 * entries was open, and two groups whose declared address no page exists at, so they were dark whatever
 * the reader was looking at. Deriving the prefixes from the entries fixed the class; this keeps it fixed,
 * because the explicit prefix escape hatch can still be written wrongly and a new sidebar can still
 * invent an address.
 *
 * <p>The markup is read rather than mounted. It is a fact about the files, one test covers every sidebar
 * at once, and driving a browser to learn it would be slower and no more certain.
 *
 * <p>A destination the markup works out for itself is skipped rather than guessed at: a template literal,
 * a ternary or a bound name is not a path this can resolve, and failing on one would only teach people
 * to stop writing them. Those are the board keys and the wizard steps, and they are exactly what the
 * explicit prefix exists for.
 */
const SRC = join(import.meta.dirname, '..', '..')

function filesUnder(dir: string, ending: string): string[] {
    const found: string[] = []
    for (const entry of readdirSync(dir)) {
        const path = join(dir, entry)
        if (statSync(path).isDirectory()) found.push(...filesUnder(path, ending))
        else if (entry.endsWith(ending)) found.push(path)
    }
    return found
}

/** Every address a page file answers at, worked out from where the file sits. */
function routePaths(): Set<string> {
    const paths = new Set<string>()
    for (const file of filesUnder(join(SRC, 'pages'), '.vue')) {
        const route = relative(join(SRC, 'pages'), file)
            .replace(/\\/g, '/')
            .replace(/\.vue$/, '')
            .replace(/\/index$/, '')
        paths.add(`/${route}`)
    }
    return paths
}

/** One `<SidebarGroup …>` or `<SidebarSubGroup …>` opening tag, whether it closes itself or holds entries. */
interface Group {
    file: string
    prefixes: string[]
    childPaths: string[]
    ownPath: string | null
    /** Whether anything about where this group leads is worked out at runtime. */
    dynamic: boolean
}

/**
 * The attributes of one opening tag, read to the `>` that actually closes it.
 *
 * <p>Quote-aware rather than a regular expression, because an arrow function in a handler carries a
 * `>` of its own: `@update:open-group="v => emit(…)"` ends the tag early for anything scanning
 * naively, and the prefix written after it goes unseen.
 */
function openingTag(source: string, from: number): {attributes: string; selfClosing: boolean; end: number} {
    let quote: string | null = null
    for (let i = from; i < source.length; i++) {
        const c = source[i]!
        if (quote) {
            if (c === quote) quote = null
            continue
        }
        if (c === '"' || c === "'") {
            quote = c
            continue
        }
        if (c === '>') {
            const selfClosing = source[i - 1] === '/'
            return {attributes: source.slice(from, selfClosing ? i - 1 : i), selfClosing, end: i + 1}
        }
    }
    return {attributes: source.slice(from), selfClosing: true, end: source.length}
}

function literal(attributes: string, name: string): string | null {
    const match = new RegExp(`(?:^|\\s)${name}="([^"]*)"`).exec(attributes)
    return match ? match[1]! : null
}

/**
 * What one group's entries say, which is what it reads off them at runtime: where an entry leads, and
 * what a subsection nested in it declares. A subsection's own entries are not in that set, because a
 * group holds the subsection, not the subsection's contents.
 */
function entriesIn(body: string, tag: string): {paths: string[]; dynamic: boolean} {
    const own = tag === 'SidebarGroup' ? body.replace(/<SidebarSubGroup\b[\s\S]*?<\/SidebarSubGroup>/g, '') : body
    const nested = tag === 'SidebarGroup'
        ? [...body.matchAll(/<SidebarSubGroup\b([^>]*)>/g)].map(match => literal(match[1]!, 'prefix'))
        : []
    const paths = [
        ...[...own.matchAll(/\sto="([^"{]*)"/g)].map(match => match[1]!),
        ...nested.filter((prefix): prefix is string => prefix !== null),
    ].filter(path => path.startsWith('/'))
    return {paths, dynamic: /\s:to="/.test(own) || /<SidebarSubGroup\b[^>]*\s:prefix="/.test(body)}
}

function groupsIn(file: string, tag: string): Group[] {
    const source = readFileSync(file, 'utf8')
    const groups: Group[] = []
    for (const match of source.matchAll(new RegExp(`<${tag}\\b`, 'g'))) {
        const {attributes, selfClosing, end} = openingTag(source, match.index + match[0].length)
        const prefix = literal(attributes, 'prefix')
        const ownPath = literal(attributes, 'to')

        const body = selfClosing ? '' : source.slice(end).split(`</${tag}>`)[0]!
        const {paths, dynamic} = entriesIn(body, tag)

        groups.push({
            file: relative(SRC, file),
            prefixes: prefix ? [prefix] : [],
            childPaths: paths,
            ownPath: ownPath && ownPath.startsWith('/') ? ownPath : null,
            dynamic: /\s:prefix="/.test(attributes) || /\s:to="/.test(attributes) || dynamic,
        })
    }
    return groups
}

const groups = ['SidebarGroup', 'SidebarSubGroup']
    .flatMap(tag => filesUnder(SRC, '.vue')
        .filter(file => readFileSync(file, 'utf8').includes(`<${tag}`))
        .flatMap(file => groupsIn(file, tag)))
    .filter(group => !group.dynamic)

describe('every sidebar group', () => {
    it('is found at all, so a passing suite means something', () => {
        expect(groups.length).toBeGreaterThan(20)
    })

    it('writes a prefix only for an address a page answers at', () => {
        const pages = routePaths()
        const invented = groups
            .flatMap(group => group.prefixes.map(prefix => ({file: group.file, prefix})))
            .filter(({prefix}) => !pages.has(prefix) && ![...pages].some(page => page.startsWith(`${prefix}/`)))
        expect(invented, 'a prefix nobody can stand on leaves its group dark for good').toEqual([])
    })

    /**
     * A group reads where its entries lead off the entries themselves, so a written prefix has to earn its
     * place by reaching a page they do not: an overview nobody links to, a detail page opened from a list.
     * A prefix whose whole area the entries already cover is the duplication this repair was about, put
     * back by hand, and it is what lets a group and its own contents drift apart again.
     */
    it('writes a prefix only for pages its entries do not already reach', () => {
        const pages = routePaths()
        const dead = groups.flatMap(group => {
            const said = [...group.childPaths, ...(group.ownPath ? [group.ownPath] : [])]
            return group.prefixes
                .filter(prefix => [...pages]
                    .filter(page => page === prefix || page.startsWith(`${prefix}/`))
                    .every(page => said.some(path => page === path || page.startsWith(`${path}/`))))
                .map(prefix => ({file: group.file, prefix}))
        })
        expect(dead, 'the entries already reach every page under this, so it says nothing new').toEqual([])
    })

    /**
     * One group's reach covering another's is allowed and settled by longest match wins: `/cluster`
     * genuinely covers the whole panel. What can never be right is a group that reaches nothing, because
     * nothing will ever light it.
     */
    it('reaches somewhere', () => {
        const empty = groups
            .filter(group => group.prefixes.length === 0 && !group.ownPath && group.childPaths.length === 0)
            .map(group => group.file)
        expect(empty, 'a group that names nothing is never lit').toEqual([])
    })
})
