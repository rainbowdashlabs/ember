/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {ColumnTypes, type TableColumn} from '@/components/table/tableColumn'
import type {MemberCheckSummary} from '@/api/inventoryCheck'
import {byValue} from '@/composables/useSortable'
import {useDataTable} from '@/composables/useDataTable'
import {checkerName, formatDate, isLockedByMe, isLockedByOther, memberName} from './memberHelpers'

/** What the status column can say about a member's check. A member checked and free says nothing. */
export const CheckStatus = {
    LOCKED: 'locked',
    MINE: 'mine',
    NEVER: 'never',
} as const

/**
 * The members whose equipment can be checked, as a table sorted by name to begin with.
 *
 * @param members         the members of the tab that is open
 * @param currentMemberId who is looking, which decides whether a check under way is theirs
 */
export function useMemberCheckTable(members: () => MemberCheckSummary[], currentMemberId: () => number | undefined) {
    const {t} = useI18n()

    function statusOf(member: MemberCheckSummary): string | null {
        if (isLockedByOther(member, currentMemberId())) return CheckStatus.LOCKED
        if (isLockedByMe(member, currentMemberId())) return CheckStatus.MINE
        return member.lastCheckedAt ? null : CheckStatus.NEVER
    }

    const columns = computed<TableColumn<MemberCheckSummary>[]>(() => [
        {key: 'name', label: t('inventory.check.member'), type: ColumnTypes.TEXT, value: memberName, pinned: true},
        {
            key: 'lastChecked', label: t('inventory.check.lastChecked'), type: ColumnTypes.DATE_TIME,
            value: member => member.lastCheckedAt,
            display: member => formatDate(member.lastCheckedAt, t('inventory.check.neverChecked')),
        },
        {
            key: 'checkedBy', label: t('inventory.check.checkedBy'), type: ColumnTypes.TEXT,
            value: member => member.checkerFirstName ? checkerName(member) : null,
        },
        {
            key: 'status', label: t('inventory.check.status'), type: ColumnTypes.ENUM, value: statusOf,
            options: [
                {value: CheckStatus.LOCKED, label: t('inventory.check.locked')},
                {value: CheckStatus.MINE, label: t('inventory.check.lockedByMe')},
                {value: CheckStatus.NEVER, label: t('inventory.check.neverChecked')},
            ],
        },
    ])

    return useDataTable<MemberCheckSummary>({
        id: 'inventory-checks',
        rows: members,
        columns,
        rowKey: member => member.memberId,
        fallbackSort: byValue(memberName),
        sort: {key: 'name'},
    })
}
