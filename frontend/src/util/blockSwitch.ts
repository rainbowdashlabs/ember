/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {CellContentType, type BlockCellRequest, type BlockRowRequest, type ContentRow} from '@/api/generated/schema'
import type {RowEditData} from '@/components/content/blockeditor/EditorRow.vue'
import {toRestriction} from '@/components/input/restriction'

/**
 * The saved shape of a block tree turned into the shape the editor works on. Ids of zero mark rows
 * and cells that do not exist yet, which is how the save path tells new from moved. A row's lines
 * between its columns and a block's restriction and guardian condition, which only a letter carries,
 * come along.
 */
export function toEditRows(saved: ContentRow[]): RowEditData[] {
    return [...saved]
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .map(r => ({
            id: r.id,
            sortOrder: r.sortOrder,
            ...(r.columnLines ? {columnLines: true} : {}),
            cells: [...r.cells]
                .sort((a, b) => a.sortOrder - b.sortOrder)
                .map(c => ({
                    id: c.id,
                    sortOrder: c.sortOrder,
                    widthPercent: c.widthPercent,
                    contentType: c.contentType,
                    content: c.content,
                    config: c.config as Record<string, unknown>,
                    ...(c.restriction ? {restriction: toRestriction(c.restriction)} : {}),
                    ...(c.guardianCondition ? {guardianCondition: c.guardianCondition} : {}),
                })),
        }))
}

/**
 * The rows the editor works on as the server takes them, numbered in the order they stand. A row's
 * lines and a block's restriction and guardian condition go along where they are set.
 */
export function toBlockRequests(rows: RowEditData[]): BlockRowRequest[] {
    return rows.map((r, ri) => ({
        sortOrder: ri,
        ...(r.columnLines ? {columnLines: true} : {}),
        cells: r.cells.map((c, ci): BlockCellRequest => ({
            sortOrder: ci,
            widthPercent: c.widthPercent,
            contentType: c.contentType,
            content: c.content,
            config: c.config,
            ...(c.restriction ? {restriction: c.restriction} : {}),
            ...(c.guardianCondition ? {guardianCondition: c.guardianCondition} : {}),
        })),
    }))
}

/**
 * What a body becomes when it is switched from the plain text field to the page editor: one row
 * holding one markdown block with the text already written.
 *
 * <p>The server does exactly this when it switches something that already exists. A thing that does
 * not exist yet has no address to switch, so the browser does it instead and tells the server once
 * saving gives it an id. Both places have to produce the same shape, which is why the shape is
 * written down once.
 */
export function markdownAsSingleBlock(markdown: string): RowEditData[] {
    return [{
        id: 0,
        sortOrder: 0,
        cells: [{
            id: 0,
            sortOrder: 0,
            widthPercent: 100,
            contentType: CellContentType.MARKDOWN,
            content: markdown,
            config: {},
        }],
    }]
}
