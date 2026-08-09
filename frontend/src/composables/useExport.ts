/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import {saveBlob} from '@/util/downloadAuthed'

/**
 * Selectable entry of an export field picker: a stable key plus the label shown to the user.
 */
export interface ExportFieldOption<K extends string | number = string> {
    key: K
    label: string
}

/**
 * Column of an export. {@code value} renders the cell of a single row and is only needed for
 * client side exports; server rendered exports receive the selected keys and resolve the
 * values themselves.
 */
export interface ExportColumn<T> extends ExportFieldOption {
    value?: (row: T) => string
}

export type ExportFormatName = 'csv' | 'values'

function escapeCsv(value: string): string {
    if (value.includes(';') || value.includes('"') || value.includes('\n')) {
        return `"${value.replace(/"/g, '""')}"`
    }
    return value
}

function cellValue<T>(row: T, column: ExportColumn<T>): string {
    return column.value?.(row) ?? ''
}

/**
 * Renders the rows as a semicolon separated CSV document with one header line.
 */
export function buildCsv<T>(rows: T[], columns: ExportColumn<T>[]): string {
    const header = columns.map(c => escapeCsv(c.label)).join(';')
    const body = rows.map(row => columns.map(c => escapeCsv(cellValue(row, c))).join(';'))
    return [header, ...body].join('\n')
}

/**
 * Builds the export document for the given rows and columns and saves it to disk. The
 * {@code values} format joins the cells of a single column into one semicolon separated list
 * and falls back to CSV whenever more than one column is exported.
 *
 * @param rows     rows to export, in output order.
 * @param columns  columns to export, in output order.
 * @param fileName file name without extension; the extension follows the resolved format.
 * @param format   requested output format.
 */
export function downloadExport<T>(
    rows: T[],
    columns: ExportColumn<T>[],
    fileName: string,
    format: ExportFormatName = 'csv',
): void {
    const [singleColumn] = columns
    const asValues = format === 'values' && columns.length === 1 && singleColumn !== undefined
    const content = asValues
        ? rows.map(row => cellValue(row, singleColumn)).filter(v => v).join('; ')
        : buildCsv(rows, columns)
    const type = asValues ? 'text/plain;charset=utf-8' : 'text/csv;charset=utf-8'
    saveBlob(new Blob([content], {type}), `${fileName}.${asValues ? 'txt' : 'csv'}`)
}

export interface UseExportOptions<T> {
    /** Rows the export can pick from, usually the currently filtered list. */
    rows: () => T[]
    /** Stable identity of a row, used for the row selection. */
    rowId: (row: T) => number
    /** Columns offered by the field picker, in export order. */
    columns: () => ExportColumn<T>[]
    /** File name without extension used by {@code performExport}. */
    fileName?: string
    /** Column keys preselected whenever the export starts. */
    defaultColumns?: string[]
    /** Preselects every row when the export starts. */
    selectAllRows?: boolean
}

/**
 * Drives the shared export flow: an export mode in which rows are selected, a column
 * selection fed by the caller's column definitions, and the download of the resulting
 * document. Callers that export server side ignore {@code performExport} and read
 * {@code selectedRows} and {@code selectedColumns} instead.
 */
export function useExport<T>(options: UseExportOptions<T>) {
    const exportMode = ref(false)
    const showExportModal = ref(false)
    const selectedIds = ref<Set<number>>(new Set())
    const selectedColumns = ref<Set<string>>(new Set(options.defaultColumns ?? []))

    const columnOptions = computed<ExportFieldOption[]>(() =>
        options.columns().map(c => ({key: c.key, label: c.label})),
    )

    const selectedRows = computed(() => options.rows().filter(r => selectedIds.value.has(options.rowId(r))))

    const allRowsSelected = computed(() => {
        const rows = options.rows()
        return rows.length > 0 && rows.every(r => selectedIds.value.has(options.rowId(r)))
    })

    function startExport() {
        exportMode.value = true
        selectedIds.value = options.selectAllRows ? new Set(options.rows().map(options.rowId)) : new Set()
        selectedColumns.value = new Set(options.defaultColumns ?? [])
    }

    function cancelExport() {
        exportMode.value = false
        showExportModal.value = false
        selectedIds.value = new Set()
    }

    function toggleExportMode() {
        if (exportMode.value) {
            cancelExport()
        } else {
            startExport()
        }
    }

    function toggleRow(id: number) {
        const next = new Set(selectedIds.value)
        if (next.has(id)) { next.delete(id) } else { next.add(id) }
        selectedIds.value = next
    }

    function toggleAllRows() {
        selectedIds.value = allRowsSelected.value ? new Set() : new Set(options.rows().map(options.rowId))
    }

    function toggleColumn(key: string) {
        const next = new Set(selectedColumns.value)
        if (next.has(key)) { next.delete(key) } else { next.add(key) }
        selectedColumns.value = next
    }

    function selectColumns(keys: string[]) {
        selectedColumns.value = new Set(keys)
    }

    function openExportModal() {
        if (selectedIds.value.size === 0) return
        showExportModal.value = true
    }

    function performExport(format: ExportFormatName = 'csv') {
        const columns = options.columns().filter(c => selectedColumns.value.has(c.key))
        if (columns.length === 0) return
        downloadExport(selectedRows.value, columns, options.fileName ?? 'export', format)
        cancelExport()
    }

    return {
        exportMode,
        showExportModal,
        selectedIds,
        selectedColumns,
        selectedRows,
        columnOptions,
        allRowsSelected,
        startExport,
        cancelExport,
        toggleExportMode,
        toggleRow,
        toggleAllRows,
        toggleColumn,
        selectColumns,
        openExportModal,
        performExport,
    }
}
