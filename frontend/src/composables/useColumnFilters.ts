/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {reactive, ref, shallowRef} from 'vue'
import {matchesDateFilter, splitDateTokens} from '@/util/dateFilter'

export type ColumnFilterKind = 'text' | 'date' | 'birthDate'

export interface ColumnFilterOptions<K extends string | number> {
    /** What a column holds, which decides whether it filters by day rather than by display string. */
    kindOf?: (key: K) => ColumnFilterKind
    /** The column's distinct values as the modal offers them; a date column passes raw ISO values. */
    distinctValues: (key: K) => string[]
    /** Filters already standing when the table first opens. */
    initial?: Map<K, Set<string>>
}

/**
 * The per-column value filters behind the shared column filter modal.
 *
 * <p>One instance per table: it holds which values each column is narrowed to, the state the modal
 * shows while one column is being edited, and the match a row has to pass. Date columns go through
 * the day tokens rather than the flat value list, the way the shared modal offers them.
 */
export function useColumnFilters<K extends string | number>(options: ColumnFilterOptions<K>) {
    const multiFilters = shallowRef<Map<K, Set<string>>>(options.initial ?? new Map())
    const emptyFilters = shallowRef<Set<K>>(new Set())

    const modalOpen = ref(false)
    const modalColumn = shallowRef<K | null>(null)
    const modalLabel = ref('')
    const modalValues = ref<string[]>([])
    const modalSelected = shallowRef<Set<string>>(new Set())
    const modalIncludeEmpty = ref(false)
    const modalKind = ref<ColumnFilterKind>('text')

    function kindOf(key: K): ColumnFilterKind {
        return options.kindOf?.(key) ?? 'text'
    }

    function open(key: K, label: string) {
        modalColumn.value = key
        modalLabel.value = label
        modalKind.value = kindOf(key)
        modalValues.value = options.distinctValues(key)
        modalSelected.value = new Set(multiFilters.value.get(key) ?? [])
        modalIncludeEmpty.value = emptyFilters.value.has(key)
        modalOpen.value = true
    }

    function apply(selected: Set<string>, includeEmpty: boolean) {
        const key = modalColumn.value
        if (key === null) return
        const filters = new Map(multiFilters.value)
        if (selected.size > 0) filters.set(key, selected)
        else filters.delete(key)
        multiFilters.value = filters

        const empties = new Set(emptyFilters.value)
        if (includeEmpty) empties.add(key)
        else empties.delete(key)
        emptyFilters.value = empties
    }

    function hasActive(key: K): boolean {
        return (multiFilters.value.get(key)?.size ?? 0) > 0 || emptyFilters.value.has(key)
    }

    /**
     * Whether one row stands where the filters leave it standing.
     *
     * @param valuesOf the row's values in one column; several where a cell holds several things,
     *                 raw ISO days where the column filters as dates
     */
    function rowPasses(valuesOf: (key: K) => string[]): boolean {
        for (const [key, selected] of multiFilters.value) {
            if (selected.size === 0) continue
            const includeEmpty = emptyFilters.value.has(key)
            const tokens = kindOf(key) === 'text' ? null : splitDateTokens(selected)
            const values = valuesOf(key)
            if (values.length === 0 || values.every(value => !value)) {
                if (!includeEmpty) return false
                continue
            }
            const matches = tokens
                ? values.some(value => matchesDateFilter(value, tokens))
                : values.some(value => selected.has(value))
            if (!matches) return false
        }
        for (const key of emptyFilters.value) {
            if ((multiFilters.value.get(key)?.size ?? 0) > 0) continue
            const values = valuesOf(key)
            if (values.length > 0 && values.some(value => !!value)) return false
        }
        return true
    }

    return reactive({
        multiFilters,
        emptyFilters,
        modalOpen,
        modalColumn,
        modalLabel,
        modalValues,
        modalSelected,
        modalIncludeEmpty,
        modalKind,
        open,
        apply,
        hasActive,
        rowPasses,
    })
}

export type ColumnFiltersApi = ReturnType<typeof useColumnFilters>
