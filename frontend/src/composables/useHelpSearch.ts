/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, watch} from 'vue'
import i18n from '@/i18n'
import {loadHelpcenterMessages} from '@/composables/useHelpcenterMessages'
import {HELP_PAGES} from '@/composables/helpPages.generated'

export interface HelpSearchEntry {
    route: string
    path: string
    title: string
    section: string
    text: string
}

export interface HelpSearchResult {
    entry: HelpSearchEntry
    snippet: string
    matchStart: number
    matchEnd: number
}

/**
 * One help page as the index reads it: where it answers, and where its text lives.
 *
 * <p>Read out of the pages tree rather than written down beside it, so a page is searchable because it
 * exists. See `helpPages.generated.ts` and the script that writes it.
 */
export interface HelpPage {
    route: string
    path: string
    /**
     * Where the page's text lives under `helpCenter`. A list where an article draws on more than one
     * subtree, which happens when it shows a shared block beside its own.
     */
    i18nPrefix: string | string[]
}


/**
 * Recursively extracts all string values from a nested object.
 */
function flattenStrings(obj: unknown): string[] {
    if (typeof obj === 'string') return [obj]
    if (typeof obj !== 'object' || obj === null) return []
    const result: string[] = []
    for (const value of Object.values(obj)) {
        result.push(...flattenStrings(value))
    }
    return result
}

/**
 * Resolves a dotted key path against a nested object.
 */
function resolveKey(obj: Record<string, unknown>, keyPath: string): unknown {
    const parts = keyPath.split('.')
    let current: unknown = obj
    for (const part of parts) {
        if (typeof current !== 'object' || current === null) return undefined
        current = (current as Record<string, unknown>)[part]
    }
    return current
}

/**
 * Builds the index from the text the pages actually render.
 *
 * <p>It used to be built at module load from the static locale file, on the reasoning that this made it
 * complete regardless of vue-i18n runtime state. That was true when it was written. The help text then
 * moved into its own chunk, merged into the active locale when a help center layout renders, and what
 * stayed in the static file under `helpCenter` was two keys: an index meant to hold 166 pages held one,
 * which is why the search answered nothing for any word on any page but the waiting lists.
 *
 * <p>Reading the merged messages is the fix, and it costs nothing: the search box only ever renders
 * inside a help center layout, and that layout has awaited the chunk before anybody can type. Importing
 * the chunk statically here would work too and would undo the code splitting it exists for.
 */
export async function buildHelpSearchIndex(): Promise<HelpSearchEntry[]> {
    await loadHelpcenterMessages()
    const messages = i18n.global.getLocaleMessage('de-DE') as Record<string, unknown>

    const drawn = HELP_PAGES.map(page => {
        const prefixes = Array.isArray(page.i18nPrefix) ? page.i18nPrefix : [page.i18nPrefix]
        const subtrees = prefixes.map(prefix => resolveKey(messages, prefix)).filter(Boolean)
        return {page, subtrees, title: titleOf(subtrees) ?? lastSegment(page.path)}
    })

    const titleByPath = new Map(drawn.map(({page, title}) => [page.path, title]))

    return drawn
        .filter(({subtrees}) => subtrees.length > 0)
        .map(({page, subtrees, title}) => ({
            route: page.route,
            path: page.path,
            title,
            section: breadcrumb(page.path, title, titleByPath),
            text: subtrees.flatMap(subtree => flattenStrings(subtree)).join(' '),
        }))
}

/**
 * What an article calls itself, which is the title it puts at the top or, on a module overview, the
 * heading it puts over the pages it lists.
 */
function titleOf(subtrees: unknown[]): string | null {
    for (const subtree of subtrees) {
        if (typeof subtree !== 'object' || subtree === null) continue
        const named = subtree as Record<string, unknown>
        const title = named['title'] ?? named['overviewTitle']
        if (typeof title === 'string') return title
    }
    return null
}

/**
 * Where a page sits, said in the words of the pages above it.
 *
 * <p>Walked up the address rather than written down: the pages above a page are the ones whose address
 * it continues. A module's overview page is written at `.../overview` and stands for the section rather
 * than sitting inside it, which is why an address with no page of its own is asked for that one too.
 */
function breadcrumb(path: string, title: string, titleByPath: Map<string, string>): string {
    const segments = path.split('/').filter(Boolean)
    const names: string[] = []
    for (let depth = 1; depth < segments.length; depth++) {
        const ancestor = `/${segments.slice(0, depth).join('/')}`
        const name = titleByPath.get(ancestor) ?? titleByPath.get(`${ancestor}/overview`)
        if (name && !names.includes(name)) names.push(name)
    }
    if (!names.includes(title)) names.push(title)
    return names.join(' > ')
}

/** The last part of an address, as a name of last resort for a page whose article names itself nothing. */
function lastSegment(path: string): string {
    const segments = path.split('/').filter(Boolean)
    return segments[segments.length - 1] ?? path
}

/**
 * Built once and shared by every box that asks afterwards.
 *
 * <p>Started as the help centre renders rather than on the first keystroke: the chunk the text lives in
 * is four thousand lines, and nobody should be typing into a box that is still reading it.
 *
 * <p>A failed attempt is forgotten rather than remembered. The chunk comes over the network, a fetch can
 * fail, and a remembered failure would leave the box answering nothing for the rest of the visit, which
 * is the shape of the fault this whole repair is about.
 */
const index = ref<HelpSearchEntry[]>([])
let building: Promise<void> | null = null

function ensureIndex(): void {
    if (building) return
    building = buildHelpSearchIndex()
        .then(entries => {
            index.value = entries
        })
        .catch(() => {
            building = null
        })
}

export function useHelpSearch() {
    ensureIndex()

    const query = ref('')
    const debouncedQuery = ref('')
    let debounceTimer: ReturnType<typeof setTimeout> | null = null

    watch(query, (val) => {
        if (debounceTimer) clearTimeout(debounceTimer)
        debounceTimer = setTimeout(() => {
            debouncedQuery.value = val.trim()
        }, 250)
    })

    const results = computed<HelpSearchResult[]>(() => {
        const q = debouncedQuery.value.toLowerCase()
        if (!q || q.length < 2) return []

        const matched: HelpSearchResult[] = []
        for (const entry of index.value) {
            const textLower = entry.text.toLowerCase()
            const matchIndex = textLower.indexOf(q)
            if (matchIndex === -1) continue

            const snippetRadius = 60
            const start = Math.max(0, matchIndex - snippetRadius)
            const end = Math.min(entry.text.length, matchIndex + q.length + snippetRadius)
            let snippet = ''
            if (start > 0) snippet += '...'
            snippet += entry.text.substring(start, end)
            if (end < entry.text.length) snippet += '...'

            matched.push({
                entry,
                snippet,
                matchStart: matchIndex - start + (start > 0 ? 3 : 0),
                matchEnd: matchIndex - start + (start > 0 ? 3 : 0) + q.length,
            })

            if (matched.length >= 10) break
        }
        return matched
    })

    const isSearching = computed(() => debouncedQuery.value.length >= 2)

    function clearSearch() {
        query.value = ''
        debouncedQuery.value = ''
    }

    return {
        query,
        results,
        isSearching,
        clearSearch,
    }
}
