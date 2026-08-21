/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {CellContentType} from '@/api/pageManage'
import type {RowEditData} from '@/components/content/blockeditor/EditorRow.vue'

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
