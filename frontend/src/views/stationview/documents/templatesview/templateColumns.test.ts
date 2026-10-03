/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {mount} from '@vue/test-utils'
import {defineComponent} from 'vue'
import {describe, expect, it} from 'vitest'
import {DocumentTemplateKind, type DocumentTemplateSummary} from '@/api/generated/schema'
import {ColumnTypes} from '@/components/table/tableColumn'
import {useDataTable, type DataTableApi} from '@/composables/useDataTable'
import {LAST_USED_COLUMN, templateColumns} from './templateColumns'

const t = (key: string) => key

function template(id: number, name: string, lastUsedAt: string | null): DocumentTemplateSummary {
    return {
        id,
        name,
        kind: DocumentTemplateKind.LETTER,
        legal: false,
        forAppointments: false,
        selfService: false,
        ofAssociation: false,
        version: 1,
        createdAt: `2026-0${id}-01T08:00:00Z`,
        updatedAt: '2026-09-01T08:00:00Z',
        lastUsedAt,
        archivedAt: null,
    }
}

const TEMPLATES = [
    template(1, 'Nie benutzt', null),
    template(2, 'Letzte Woche', '2026-09-26T08:00:00Z'),
    template(3, 'Heute', '2026-10-03T08:00:00Z'),
    template(4, 'Auch nie', null),
]

/** The list as the templates page opens it. */
function openedList(): DataTableApi<DocumentTemplateSummary> {
    let api: DataTableApi<DocumentTemplateSummary> | null = null
    mount(defineComponent({
        setup() {
            api = useDataTable<DocumentTemplateSummary>({
                id: 'document-templates-test',
                rows: TEMPLATES,
                columns: templateColumns(t, false),
                rowKey: row => row.id,
                sort: {key: LAST_USED_COLUMN, direction: 'desc'},
            })
            return () => null
        },
    }))
    return api as unknown as DataTableApi<DocumentTemplateSummary>
}

describe('templateColumns', () => {
    it('says which templates the association keeps where the list holds them', () => {
        const keys = templateColumns(t, true).map(column => column.key)
        expect(keys).toContain('ofAssociation')
        expect(keys.indexOf('ofAssociation')).toBe(keys.indexOf('kind') + 1)
    })

    it('leaves that out of the association\'s own list', () => {
        expect(templateColumns(t, false).map(column => column.key)).not.toContain('ofAssociation')
    })

    it('dates the creation, the last change and the last use as moments', () => {
        const dated = templateColumns(t, false).filter(column => column.type === ColumnTypes.DATE_TIME)

        expect(dated.map(column => column.key)).toEqual(['createdAt', 'updatedAt', LAST_USED_COLUMN])
    })

    it('opens on the template used last, with those never used at the end', () => {
        expect(openedList().rows.map(row => row.id).slice(0, 2)).toEqual([3, 2])
        expect(openedList().rows.slice(2).map(row => row.id).toSorted()).toEqual([1, 4])
    })

    it('sorts by when a template was created', () => {
        const list = openedList()
        list.sortBy('createdAt')
        list.sortDirection = 'asc'

        expect(list.rows.map(row => row.id)).toEqual([1, 2, 3, 4])
    })
})
