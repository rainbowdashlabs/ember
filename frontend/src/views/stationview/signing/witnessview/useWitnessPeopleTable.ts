/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {ColumnTypes, type TableColumn} from '@/components/table/tableColumn'
import type {WitnessCandidateResponse} from '@/api/generated/schema'
import {byValue} from '@/composables/useSortable'
import {useDataTable} from '@/composables/useDataTable'
import {userTypeOptions} from '@/views/stationview/members/listview/memberColumns'

/**
 * The people a witness may collect signatures from, as a table: name, member type, groups and how many
 * signatures wait on them, sorted by name to begin with. The search also finds the documents that wait.
 *
 * @param people     the people of the tab that is open
 * @param groupNames the name of each group by its id, for the groups column
 */
export function useWitnessPeopleTable(
    people: () => WitnessCandidateResponse[],
    groupNames: () => ReadonlyMap<number, string>,
) {
    const {t} = useI18n()

    const columns = computed<TableColumn<WitnessCandidateResponse>[]>(() => [
        {key: 'name', label: t('signing.witness.person.colName'), type: ColumnTypes.TEXT, value: person => person.name, pinned: true},
        {
            key: 'userType', label: t('signing.witness.person.colType'), type: ColumnTypes.ENUM,
            value: person => person.userType, options: userTypeOptions(),
        },
        {
            key: 'groups', label: t('signing.witness.person.colGroups'), type: ColumnTypes.TEXT,
            value: person => person.groupIds.map(id => groupNames().get(id)).filter((name): name is string => !!name),
        },
        {
            key: 'open', label: t('signing.witness.person.colOpen'), type: ColumnTypes.NUMBER,
            value: person => person.openFields, align: 'right',
        },
    ])

    return useDataTable<WitnessCandidateResponse>({
        id: 'witness-people',
        rows: people,
        columns,
        rowKey: person => person.memberId,
        fallbackSort: byValue(person => person.name.toLowerCase()),
        sort: {key: 'name'},
        searchText: person => person.openDocuments.join(' '),
    })
}
