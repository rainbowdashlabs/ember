/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import type {WaitingListEntryWithScore} from '@/api/generated/schema'
import {waitingList} from '@/api'
import {useConfirmAction} from '@/composables/useConfirmAction'
import {useSidebarCounts} from '@/composables/useSidebarCounts'
import type {Failure} from '@/util/failure'
import {entryFullName} from './detailview/entryFullName'

/**
 * Deleting a waiting list entry behind the confirmation dialog, as the list and the entry's own page
 * both offer it.
 *
 * <p>Once the entry is gone the sidebar counts are read again, since the entry may have been one of
 * those counted, and `afterDeletion` lets the page catch up: the list reads its entries again, the
 * entry's page leaves for the list.
 *
 * @param listId the list the entries belong to
 * @param failure the page's failure, where a refused deletion is reported
 * @param afterDeletion what the page does once the entry is deleted
 */
export function useEntryDeletion(
    listId: Ref<number>,
    failure: Ref<Failure | null>,
    afterDeletion: () => void | Promise<void>,
) {
    const {t} = useI18n()
    const {refresh: refreshSidebarCounts} = useSidebarCounts()

    const action = useConfirmAction<WaitingListEntryWithScore>({
        onConfirm: entry => waitingList.deleteEntry(listId.value, entry.entry.id),
        onSuccess: async () => {
            void refreshSidebarCounts()
            await afterDeletion()
        },
        failure,
    })

    const message = computed(() => t('waitingList.deleteEntryConfirm', {
        name: action.target.value ? entryFullName(action.target.value) : '',
    }))

    return {...action, message}
}
