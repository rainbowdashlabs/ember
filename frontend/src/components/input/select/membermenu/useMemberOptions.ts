/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, onBeforeUnmount, ref, shallowRef, watch} from 'vue'
import {matchesWords} from '@/util/listSearch'
import {byMemberName} from '@/util/memberOrder'
import type {MemberOption} from '../memberOption'

/** How long to wait before asking the server again, which is about as long as a word takes to type. */
const SEARCH_DEBOUNCE_MS = 250

/** Where a menu gets the people it offers, and what it has been asked to narrow them to. */
export interface MemberOptionSource {
    members: () => MemberOption[]
    /** Asks the server instead of holding the list. Absent where the caller holds it. */
    searchFn: () => ((query: string) => Promise<MemberOption[]>) | undefined
    /** Puts a name to a value the fetched list does not happen to contain. */
    resolveFn: () => ((value: string) => Promise<MemberOption | null>) | undefined
    search: () => string
    userType: () => string
    /** Whoever the menu is holding, by value, which is who still has to be named. */
    chosenValues: () => string[]
}

/**
 * The people a member menu offers, whether it holds them or asks for them.
 *
 * <p>A station of three hundred members is fine in memory and is narrowed here; the page editor
 * searches the whole instance and is narrowed by the server, which has already answered the very
 * question that was typed. Keeping both behind one shape is what lets the menu itself not care.
 *
 * <p>Everybody met once is remembered. A fetched list holds whatever answered the last query, which is
 * rarely the person already chosen, so without that a saved choice would read as its own identifier
 * the moment the reader typed anything.
 */
export function useMemberOptions(source: MemberOptionSource) {
    const fetched = shallowRef<MemberOption[]>([])
    const fetching = ref(false)
    const known = ref(new Map<string, MemberOption>())
    let debounce: ReturnType<typeof setTimeout> | null = null

    const isRemote = computed(() => source.searchFn() != null)

    function remember(options: MemberOption[]) {
        if (options.length === 0) return
        const next = new Map(known.value)
        for (const option of options) next.set(option.value, option)
        known.value = next
    }

    async function runSearch(query: string) {
        const searchFn = source.searchFn()
        if (!searchFn) return
        fetching.value = true
        try {
            const results = await searchFn(query)
            fetched.value = results
            remember(results)
        } catch {
            fetched.value = []
        } finally {
            fetching.value = false
        }
    }

    /** Asks again for what is typed now, which is what opening the menu wants. */
    async function refresh() {
        if (isRemote.value) await runSearch(source.search())
    }

    /** Somebody chosen before the menu was drawn still has to be named, which a fetched list cannot do. */
    async function resolveMissing() {
        const resolveFn = source.resolveFn()
        if (!resolveFn) return
        const missing = source.chosenValues().filter(value => !known.value.get(value))
        if (missing.length === 0) return
        const resolved = await Promise.all(missing.map(value => resolveFn(value).catch(() => null)))
        remember(resolved.filter((option): option is MemberOption => option != null))
    }

    watch(source.members, members => remember(members), {immediate: true, deep: true})
    watch(source.chosenValues, resolveMissing, {immediate: true})

    watch(source.search, query => {
        if (!isRemote.value) return
        if (debounce) clearTimeout(debounce)
        debounce = setTimeout(() => runSearch(query), SEARCH_DEBOUNCE_MS)
    })

    onBeforeUnmount(() => {
        if (debounce) clearTimeout(debounce)
    })

    /** The rows on offer: narrowed by the kind, then by what was typed, then in first-name order. */
    const matching = computed(() => {
        if (isRemote.value) return fetched.value
        const needle = source.search().trim()
        const kind = source.userType()
        return source.members()
            .filter(option => !kind || option.userType === kind)
            .filter(option => !needle || matchesWords(`${option.name} ${option.email ?? ''}`, needle))
            .toSorted(byMemberName(option => option.name))
    })

    const selectedOptions = computed(() => source.chosenValues()
        .map(value => known.value.get(value))
        .filter((option): option is MemberOption => option != null))

    return {matching, selectedOptions, fetching, optionFor: (value: string) => known.value.get(value), refresh}
}
