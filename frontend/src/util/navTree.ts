/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {
    NavChildDef,
    NavEntryDef,
    NavGroupDef,
    NavNode,
    NavPage,
    NavPageDef,
    NavValue,
} from '@/data/sidebar/navTypes'

type Translate = (key: string) => string

function read<C, T>(value: NavValue<C, T>, context: C): T {
    return typeof value === 'function' ? (value as (context: C) => T)(context) : value
}

function resolvePage<C>(def: NavPageDef<C>, context: C, t: Translate): NavPage {
    return {
        kind: 'page',
        key: def.key,
        label: def.text ?? t(read(def.label, context)),
        icon: read(def.icon, context),
        to: read(def.to, context),
        badge: def.badge?.(context) ?? 0,
        onboarding: def.onboarding,
        trailingIcon: def.trailingIcon,
        dimmed: def.dimmed?.(context) ?? false,
        paletteOnly: def.paletteOnly ?? false,
    }
}

/**
 * The entries the sidebar lists: everything but the pages kept for the palette.
 *
 * @param nodes resolved entries
 * @returns the listed ones, in order
 */
export function listed(nodes: NavNode[]): NavNode[] {
    return nodes.filter(node => node.kind === 'group' || !node.paletteOnly)
}

function firstListedPage(nodes: NavNode[]): NavPage | undefined {
    for (const node of nodes) {
        if (node.kind === 'page') {
            if (!node.paletteOnly) return node
            continue
        }
        const found = firstListedPage(node.children)
        if (found) return found
    }
    return undefined
}

/**
 * A group leads to its first listed page. One with nothing listed for this reader is left out, so nobody
 * is shown a row that opens onto nothing; one left with a single page and nothing kept for the palette
 * is that page, under the group's name, because a section of one is a click for its own sake.
 */
function resolveGroup<C>(def: NavGroupDef<C>, context: C, t: Translate): NavNode | null {
    const children = resolveChildren(def.children, context, t)
    const first = firstListedPage(children)
    if (!first) return null
    const label = t(read(def.label, context))
    const icon = read(def.icon, context)
    const badge = def.badge?.(context) ?? 0
    const inline = def.inline?.(context) ?? false
    if (!inline && children.length === 1 && children[0] === first) {
        return {...first, key: def.key, label, icon, badge: badge || first.badge, onboarding: def.onboarding ?? first.onboarding}
    }
    return {
        kind: 'group',
        key: def.key,
        label,
        icon,
        to: first.to,
        prefix: [...(def.prefix === undefined ? [] : read(def.prefix, context)), ...allPages(children).map(page => page.to)],
        badge,
        onboarding: def.onboarding,
        holds: marksUnder(children),
        inline,
        children,
    }
}

/** The onboarding marks of the entries and everything under them, each once. */
function marksUnder(nodes: NavNode[]): string[] {
    const marks = nodes.flatMap(node => [
        ...(node.onboarding ? [node.onboarding] : []),
        ...(node.kind === 'group' ? node.holds : []),
    ])
    return [...new Set(marks)]
}

function resolveEntry<C>(def: NavEntryDef<C>, context: C, t: Translate): NavNode | null {
    if (def.visible && !def.visible(context)) return null
    return def.kind === 'page' ? resolvePage(def, context, t) : resolveGroup(def, context, t)
}

function resolveChildren<C>(defs: NavChildDef<C>[], context: C, t: Translate): NavNode[] {
    return defs
        .flatMap(def => typeof def === 'function' ? def(context) : [def])
        .map(def => resolveEntry(def, context, t))
        .filter((node): node is NavNode => node !== null)
}

/**
 * The sidebar as one reader sees it: every entry they may not reach left out, every label translated
 * and every count read.
 *
 * @param defs the sidebar's entries, as a context's nav list declares them
 * @param context what the reader holds, which the entries' conditions read
 * @param t the translation function
 * @returns the entries to show, in order
 */
export function resolveNav<C>(defs: NavEntryDef<C>[], context: C, t: Translate): NavNode[] {
    return resolveChildren(defs, context, t)
}

function allPages(nodes: NavNode[]): NavPage[] {
    return nodes.flatMap(node => node.kind === 'page' ? [node] : allPages(node.children))
}

/**
 * Every page in a resolved sidebar in sidebar order, each address once. The palette offers exactly these.
 *
 * @param nodes a resolved sidebar
 * @returns its pages, flat
 */
export function navPages(nodes: NavNode[]): NavPage[] {
    const seen = new Set<string>()
    return allPages(nodes).filter(page => !seen.has(page.to) && seen.add(page.to))
}

/**
 * How well an entry covers an address: the length of the longest of its addresses the path is at or
 * under, zero when none is.
 *
 * @param node a resolved entry
 * @param path the address shown
 * @returns the match length
 */
export function matchLength(node: NavNode, path: string): number {
    const covered = node.kind === 'page' ? [node.to] : node.prefix
    return covered.reduce((best, prefix) => path === prefix || path.startsWith(`${prefix}/`) ? Math.max(best, prefix.length) : best, 0)
}

/**
 * The entry among these that holds the page shown, by longest match, so `/cluster` covering the whole
 * panel loses to the section that names the page itself.
 *
 * @param nodes the entries to choose from
 * @param path the address shown
 * @returns the best entry, or undefined when none covers the address
 */
export function entryHolding(nodes: NavNode[], path: string): NavNode | undefined {
    let best: NavNode | undefined
    let bestLength = 0
    for (const node of nodes) {
        const length = matchLength(node, path)
        if (length > bestLength) {
            best = node
            bestLength = length
        }
    }
    return best
}
