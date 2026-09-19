/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, reactive, ref, toValue, watch, type MaybeRefOrGetter} from 'vue'
import {useI18n} from 'vue-i18n'
import type {ColumnPickerOption} from '@/components/table/columns'
import {
    cellPasses, choicesFor, compareByColumn, filterKindOf, filterValuesOf, sortValueOf,
    type FilterChoice, type FilterKind, type YesNo,
} from '@/components/table/columnFilter'
import {ColumnTypes, scalarsOf, wordScalar, type TableColumn} from '@/components/table/tableColumn'
import {sortIconFor, type AriaSort, type SortComparator, type SortDirection} from '@/composables/useSortable'
import {loadColumnChoices, saveColumnChoices, type ColumnChoices} from '@/util/columnChoices'
import {sessionStationId} from '@/util/sessionState'

/**
 * How one table is being looked at: its sort, its filters and its search.
 *
 * <p>Held apart from the table so a screen can keep several and switch between them, the way the
 * member list keeps one per tab. Its filters are the shape saved filters are stored in.
 */
export interface DataTableState {
    sortKey: string | null
    sortDirection: SortDirection
    /** Per column, what its filter holds. */
    filters: Map<string, Set<string>>
    /** The columns whose empty cells pass their filter, or with nothing else held, only they do. */
    empties: Set<string>
    search: string
}

export function emptyTableState(sortKey: string | null = null, sortDirection: SortDirection = 'asc'): DataTableState {
    return {sortKey, sortDirection, filters: new Map(), empties: new Set(), search: ''}
}

export interface DataTableOptions<Row> {
    /**
     * Names the table where its column choices are remembered, per station. Changing it switches to
     * that table's choices, which is how a screen with tabs keeps columns per tab.
     */
    id: MaybeRefOrGetter<string>
    rows: MaybeRefOrGetter<readonly Row[]>
    columns: MaybeRefOrGetter<readonly TableColumn<Row>[]>
    rowKey: (row: Row) => string | number
    /** Where the sort, filters and search live, when the screen keeps them itself. */
    state?: MaybeRefOrGetter<DataTableState>
    /** Breaks ties the sorted column leaves, and orders the rows while no column is sorted. */
    fallbackSort?: SortComparator<Row>
    /** Further words the search looks through beyond the searchable columns, such as an address. */
    searchText?: (row: Row) => string
}

interface FilterDialog {
    open: boolean
    key: string
    label: string
    kind: FilterKind
    choices: FilterChoice[]
    selected: Set<string>
    includeEmpty: boolean
}

/**
 * A table of records: which columns show, how the rows are sorted, filtered and searched.
 *
 * <p>Everything follows from each column's type, so a screen names its columns and never wires a
 * sort or a filter by hand. Filters stand only on the columns that show: a column taken out of view
 * takes its filter with it rather than hiding rows for a reason the reader can no longer see.
 *
 * <p>Empty cells sort last whichever way a column is turned, because a list that opens on a block
 * of blanks reads as a list with nothing in it.
 *
 * @typeParam Row the record one line of the table stands for
 */
export function useDataTable<Row>(options: DataTableOptions<Row>) {
    const {t} = useI18n()
    const yesNo: YesNo = {yes: t('common.yes'), no: t('common.no')}

    const ownState = reactive(emptyTableState()) as DataTableState
    const state = computed<DataTableState>(() => toValue(options.state) ?? ownState)

    const memoryKey = computed(() => `${sessionStationId.value ?? '-'}:${toValue(options.id)}`)
    const choices = ref<ColumnChoices>({})
    watch(memoryKey, key => { choices.value = loadColumnChoices(key) }, {immediate: true})

    const columns = computed(() => toValue(options.columns))

    function isShown(column: TableColumn<Row>): boolean {
        return column.pinned || (choices.value[column.key] ?? column.defaultVisible ?? true)
    }

    const visibleColumns = computed(() => columns.value.filter(isShown))

    const pickerOptions = computed<ColumnPickerOption[]>(() => columns.value
        .filter(column => !column.pinned)
        .map(column => ({key: column.key, label: column.label, visible: isShown(column)})))

    function toggleColumn(key: string | number) {
        const column = columns.value.find(candidate => candidate.key === String(key))
        if (!column || column.pinned) return
        choices.value = {...choices.value, [column.key]: !isShown(column)}
        saveColumnChoices(memoryKey.value, choices.value)
    }

    /** Shows or hides several columns as one choice, remembered once. Pinned columns stay. */
    function setColumnsVisible(keys: readonly (string | number)[], visible: boolean) {
        const next = {...choices.value}
        for (const key of keys) {
            const column = columns.value.find(candidate => candidate.key === String(key))
            if (column && !column.pinned) next[column.key] = visible
        }
        choices.value = next
        saveColumnChoices(memoryKey.value, choices.value)
    }

    function columnOf(key: string): TableColumn<Row> | undefined {
        return columns.value.find(column => column.key === key)
    }

    function display(column: TableColumn<Row>, row: Row): string {
        if (column.display) return column.display(row)
        return scalarsOf(column.value(row)).map(scalar => wordScalar(column, scalar, yesNo)).join(', ')
    }

    function isSortable(column: TableColumn<Row>): boolean {
        return column.sortable ?? true
    }

    function isFilterable(column: TableColumn<Row>): boolean {
        return column.filterable ?? true
    }

    function isSearchable(column: TableColumn<Row>): boolean {
        return column.searchable ?? (column.type === ColumnTypes.TEXT || column.type === ColumnTypes.ENUM)
    }

    function hasFilter(key: string): boolean {
        return (state.value.filters.get(key)?.size ?? 0) > 0 || state.value.empties.has(key)
    }

    const activeFilters = computed(() => visibleColumns.value.filter(column => isFilterable(column) && hasFilter(column.key)))

    function passesFilters(row: Row): boolean {
        return activeFilters.value.every(column => cellPasses(
            filterKindOf(column.type),
            filterValuesOf(column, row, yesNo),
            state.value.filters.get(column.key) ?? new Set(),
            state.value.empties.has(column.key),
        ))
    }

    function passesSearch(row: Row, query: string): boolean {
        if (!query) return true
        const searched = visibleColumns.value.filter(isSearchable).map(column => display(column, row))
        if (options.searchText) searched.push(options.searchText(row))
        return searched.some(text => text.toLowerCase().includes(query))
    }

    const allRows = computed(() => toValue(options.rows))

    const filteredRows = computed(() => {
        const query = state.value.search.trim().toLowerCase()
        return allRows.value.filter(row => passesSearch(row, query) && passesFilters(row))
    })

    const sortColumn = computed(() => {
        const key = state.value.sortKey
        const column = key === null ? undefined : columnOf(key)
        return column && isShown(column) && isSortable(column) ? column : undefined
    })

    const rows = computed(() => {
        const column = sortColumn.value
        const fallback = options.fallbackSort
        if (!column) return fallback ? filteredRows.value.toSorted(fallback) : filteredRows.value
        const factor = state.value.sortDirection === 'asc' ? 1 : -1
        return filteredRows.value.toSorted((a, b) => {
            const aEmpty = sortValueOf(column, a, yesNo) === null
            const bEmpty = sortValueOf(column, b, yesNo) === null
            if (aEmpty !== bEmpty) return aEmpty ? 1 : -1
            const primary = aEmpty ? 0 : compareByColumn(column, a, b, yesNo) * factor
            return primary !== 0 || !fallback ? primary : fallback(a, b)
        })
    })

    function toggleSort(key: string) {
        const current = state.value
        if (current.sortKey === key) {
            current.sortDirection = current.sortDirection === 'asc' ? 'desc' : 'asc'
            return
        }
        current.sortKey = key
        current.sortDirection = 'asc'
    }

    const sortKey = computed({
        get: () => sortColumn.value?.key ?? null,
        set: (key: string | null) => { state.value.sortKey = key },
    })

    const sortDirection = computed({
        get: () => state.value.sortDirection,
        set: (direction: SortDirection) => { state.value.sortDirection = direction },
    })

    function sortIcon(key: string): string {
        return sortIconFor(sortColumn.value?.key === key, state.value.sortDirection)
    }

    function ariaSort(key: string): AriaSort {
        if (sortColumn.value?.key !== key) return 'none'
        return state.value.sortDirection === 'asc' ? 'ascending' : 'descending'
    }

    const dialog = reactive<FilterDialog>({
        open: false, key: '', label: '', kind: 'values', choices: [], selected: new Set(), includeEmpty: false,
    })

    function openFilter(key: string) {
        const column = columnOf(key)
        if (!column) return
        dialog.key = key
        dialog.label = column.label
        dialog.kind = filterKindOf(column.type)
        dialog.choices = choicesFor(column, allRows.value, yesNo)
        dialog.selected = new Set(state.value.filters.get(key) ?? [])
        dialog.includeEmpty = state.value.empties.has(key)
        dialog.open = true
    }

    /** Sets what one column's filter holds. Nothing held and no empties clears it. */
    function setFilter(key: string, selected: ReadonlySet<string>, includeEmpty: boolean) {
        const current = state.value
        const filters = new Map(current.filters)
        if (selected.size > 0) filters.set(key, new Set(selected))
        else filters.delete(key)
        current.filters = filters

        const empties = new Set(current.empties)
        if (includeEmpty) empties.add(key)
        else empties.delete(key)
        current.empties = empties
    }

    function applyFilter(selected: Set<string>, includeEmpty: boolean) {
        setFilter(dialog.key, selected, includeEmpty)
    }

    function clearFilters() {
        state.value.filters = new Map()
        state.value.empties = new Set()
    }

    const search = computed({
        get: () => state.value.search,
        set: (value: string) => { state.value.search = value },
    })

    return reactive({
        columns,
        visibleColumns,
        pickerOptions,
        toggleColumn,
        setColumnsVisible,
        rows,
        rowKey: options.rowKey,
        display,
        isSortable,
        isFilterable,
        toggleSort,
        sortKey,
        sortDirection,
        sortIcon,
        ariaSort,
        hasFilter,
        activeFilterCount: computed(() => activeFilters.value.length),
        filterDialog: dialog,
        openFilter,
        applyFilter,
        setFilter,
        clearFilters,
        search,
    })
}

export type DataTableApi<Row> = ReturnType<typeof useDataTable<Row>>
