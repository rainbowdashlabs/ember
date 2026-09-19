/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, reactive, ref, toValue, watch, type MaybeRefOrGetter} from 'vue'
import {useI18n} from 'vue-i18n'
import type {ColumnPickerOption} from '@/components/table/columns'
import {
    choicesFor, filterKindOf, filterValuesOf, prepareFilter, sortValueOf,
    type CellFilter, type FilterChoice, type FilterKind, type YesNo,
} from '@/components/table/columnFilter'
import {ColumnTypes, scalarsOf, wordScalar, type TableColumn} from '@/components/table/tableColumn'
import {
    compareSortValues, sortIconFor, type AriaSort, type SortComparator, type SortDirection, type SortValue,
} from '@/composables/useSortable'
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
     * Names the table where its column choices are remembered. Changing it switches to that table's
     * choices, which is how a screen with tabs keeps columns per tab.
     */
    id: MaybeRefOrGetter<string>
    /**
     * Whether the column choices are remembered per station, which a station's own table wants and
     * a table of the association or the administration does not: it is the same table whichever
     * station happens to be selected. Defaults to per station.
     */
    perStation?: boolean
    rows: MaybeRefOrGetter<readonly Row[]>
    columns: MaybeRefOrGetter<readonly TableColumn<Row>[]>
    rowKey: (row: Row) => string | number
    /** The sort a table opens on, where it keeps its own state. */
    sort?: {key: string, direction?: SortDirection}
    /** Where the sort, filters and search live, when the screen keeps them itself, such as per tab. */
    state?: MaybeRefOrGetter<DataTableState>
    /** Breaks ties the sorted column leaves, and orders the rows while no column is sorted. */
    fallbackSort?: SortComparator<Row>
    /** Further words the search looks through beyond the searchable columns, such as an address. */
    searchText?: (row: Row) => string
}

/** The filter of one column as its dialog shows it. */
export interface FilterDialog {
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
 * <p>The rows are sorted before they are searched and filtered, so typing into the search narrows
 * a sorted list rather than sorting it again on every key.
 *
 * @typeParam Row the record one line of the table stands for
 */
export function useDataTable<Row>(options: DataTableOptions<Row>) {
    const {t} = useI18n()
    const yesNo: YesNo = {yes: t('common.yes'), no: t('common.no')}

    const ownState = reactive(emptyTableState(options.sort?.key ?? null, options.sort?.direction)) as DataTableState
    const state = computed<DataTableState>(() => toValue(options.state) ?? ownState)

    const memoryKey = computed(() => {
        const scope = options.perStation === false ? 'all' : sessionStationId.value ?? '-'
        return `${scope}:${toValue(options.id)}`
    })
    const choices = ref<ColumnChoices>({})
    watch(memoryKey, key => { choices.value = loadColumnChoices(key) }, {immediate: true})

    const columns = computed(() => toValue(options.columns))
    const columnsByKey = computed(() => new Map(columns.value.map(column => [column.key, column])))

    function isShown(column: TableColumn<Row>): boolean {
        return column.pinned || (choices.value[column.key] ?? column.defaultVisible ?? true)
    }

    const visibleColumns = computed(() => columns.value.filter(isShown))

    const pickerOptions = computed<ColumnPickerOption[]>(() => columns.value
        .filter(column => !column.pinned)
        .map(column => ({key: column.key, label: column.label, visible: isShown(column)})))

    /** Shows or hides columns as one choice, remembered once. Pinned columns stay. */
    function setColumnsVisible(keys: readonly (string | number)[], visible: boolean) {
        const next = {...choices.value}
        for (const key of keys) {
            const column = columnsByKey.value.get(String(key))
            if (column && !column.pinned) next[column.key] = visible
        }
        choices.value = next
        saveColumnChoices(memoryKey.value, choices.value)
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

    const allRows = computed(() => toValue(options.rows))

    const sortColumn = computed(() => {
        const key = state.value.sortKey
        const column = key === null ? undefined : columnsByKey.value.get(key)
        return column && isShown(column) && isSortable(column) ? column : undefined
    })

    /** Every row in order, each sort value worked out once rather than at every comparison. */
    const sortedRows = computed(() => {
        const column = sortColumn.value
        const fallback = options.fallbackSort
        if (!column) return fallback ? allRows.value.toSorted(fallback) : [...allRows.value]
        const factor = state.value.sortDirection === 'asc' ? 1 : -1
        const keyed: {row: Row, key: SortValue}[] = allRows.value.map(row => ({row, key: sortValueOf(column, row, yesNo)}))
        keyed.sort((a, b) => {
            const aEmpty = a.key === null
            const bEmpty = b.key === null
            if (aEmpty !== bEmpty) return aEmpty ? 1 : -1
            const primary = aEmpty ? 0 : compareSortValues(a.key, b.key) * factor
            return primary !== 0 || !fallback ? primary : fallback(a.row, b.row)
        })
        return keyed.map(entry => entry.row)
    })

    /** The filters standing on the shown columns, each read once per change rather than per row. */
    const activeFilters = computed<{column: TableColumn<Row>, passes: CellFilter}[]>(() => visibleColumns.value
        .filter(column => isFilterable(column) && hasFilter(column.key))
        .map(column => ({
            column,
            passes: prepareFilter(
                filterKindOf(column.type),
                state.value.filters.get(column.key) ?? new Set(),
                state.value.empties.has(column.key),
            ),
        })))

    /** What the search looks through per row, lowercased once per change of rows or columns. */
    const searchTexts = computed(() => {
        const searched = visibleColumns.value.filter(isSearchable)
        return new Map(allRows.value.map(row => {
            const texts = searched.map(column => display(column, row))
            if (options.searchText) texts.push(options.searchText(row))
            return [row, texts.join('\n').toLowerCase()]
        }))
    })

    const rows = computed(() => {
        const query = state.value.search.trim().toLowerCase()
        const filters = activeFilters.value
        if (!query && filters.length === 0) return sortedRows.value
        const texts = query ? searchTexts.value : null
        return sortedRows.value.filter(row =>
            (!texts || (texts.get(row) ?? '').includes(query))
            && filters.every(({column, passes}) => passes(filterValuesOf(column, row, yesNo))))
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

    const filterKey = ref<string | null>(null)

    /** The filter being edited, or null while no dialog is open. */
    const filterDialog = computed<FilterDialog | null>(() => {
        const column = filterKey.value === null ? undefined : columnsByKey.value.get(filterKey.value)
        if (!column) return null
        return {
            key: column.key,
            label: column.label,
            kind: filterKindOf(column.type),
            choices: choicesFor(column, allRows.value, yesNo),
            selected: new Set(state.value.filters.get(column.key) ?? []),
            includeEmpty: state.value.empties.has(column.key),
        }
    })

    function openFilter(key: string) {
        filterKey.value = key
    }

    function closeFilter() {
        filterKey.value = null
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

    const search = computed({
        get: () => state.value.search,
        set: (value: string) => { state.value.search = value },
    })

    return reactive({
        columns,
        visibleColumns,
        pickerOptions,
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
        filterDialog,
        openFilter,
        closeFilter,
        setFilter,
        search,
    })
}

export type DataTableApi<Row> = ReturnType<typeof useDataTable<Row>>
