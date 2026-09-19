/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {MemberTableCellTypes, type MemberTableColumn, type MemberTableHeader} from '@/api/memberTable'
import {ColumnTypes, type CellValue, type ColumnOption, type ColumnType, type TableColumn} from '@/components/table/tableColumn'

/** One person on the drawn table: the cells by column key, and whatever the screen knows beside them. */
export interface DrawnRow<Extra> {
    memberId: number
    cells: Map<string, string>
    extra: Extra
}

/**
 * The one string that stands for a column, unique across the three kinds: the kind, and then either
 * the builtin's name or the question's id. The server is told the columns, never these strings.
 */
export function keyOf(column: MemberTableHeader | MemberTableColumn): string {
    if (column.kind === 'BUILTIN') return `b:${column.key}`
    return column.kind === 'PROFILE_FIELD' ? `p:${column.fieldId}` : `q:${column.fieldId}`
}

/** The column a key stands for, or null where it names nothing a table can draw. */
export function columnOf(key: string): MemberTableColumn | null {
    const [kind, rest] = [key.slice(0, 1), key.slice(2)]
    if (kind === 'b') return {kind: 'BUILTIN', key: rest, fieldId: null}
    const fieldId = Number(rest)
    if (!Number.isFinite(fieldId)) return null
    return {kind: kind === 'p' ? 'PROFILE_FIELD' : 'REGISTRATION_FIELD', key: null, fieldId}
}

const LISTING_BUILTINS = new Set(['groups', 'tags'])
const DAY_PATTERN = /^(\d{2})\.(\d{2})\.(\d{4})$/
const TRUE_WORDS = new Set(['true', 'ja', 'yes'])

const TYPE_OF: Record<string, ColumnType> = {
    [MemberTableCellTypes.NUMBER]: ColumnTypes.NUMBER,
    [MemberTableCellTypes.DATE]: ColumnTypes.DATE,
    [MemberTableCellTypes.BIRTH_DATE]: ColumnTypes.BIRTH_DATE,
    [MemberTableCellTypes.BOOLEAN]: ColumnTypes.BOOLEAN,
    [MemberTableCellTypes.ENUM]: ColumnTypes.ENUM,
}

/** A drawn day as ISO, whichever of the two ways it was written. */
function isoDay(cell: string): string {
    const match = DAY_PATTERN.exec(cell)
    return match ? `${match[3]}-${match[2]}-${match[1]}` : cell
}

/**
 * A drawn cell read back into what it holds, so it sorts and filters by what it is rather than by
 * how it reads.
 */
function cellValue(header: MemberTableHeader, cell: string | undefined): CellValue {
    if (!cell) return null
    switch (header.type) {
        case MemberTableCellTypes.DATE:
        case MemberTableCellTypes.BIRTH_DATE:
            return isoDay(cell)
        case MemberTableCellTypes.NUMBER: {
            const number = Number(cell.replace(',', '.'))
            return Number.isFinite(number) ? number : cell
        }
        case MemberTableCellTypes.BOOLEAN:
            return TRUE_WORDS.has(cell.toLowerCase())
        default:
            return header.kind === 'BUILTIN' && LISTING_BUILTINS.has(header.key ?? '') ? cell.split(', ') : cell
    }
}

/**
 * One column of the drawn table.
 *
 * @param header  the column as the station offers it
 * @param options the words for the tokens of a column that holds them, such as a kind of member
 */
export function tableColumnOf<Extra>(
    header: MemberTableHeader, options?: readonly ColumnOption[],
): TableColumn<DrawnRow<Extra>> {
    const key = keyOf(header)
    return {
        key,
        label: header.label,
        type: TYPE_OF[header.type] ?? ColumnTypes.TEXT,
        value: row => cellValue(header, row.cells.get(key)),
        options,
        defaultVisible: false,
    }
}
