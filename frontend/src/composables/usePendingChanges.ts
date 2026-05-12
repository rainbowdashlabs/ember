/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, ref} from 'vue'
import {profileFieldChanges} from '@/api'

const count = ref(0)

export function usePendingChanges() {
    async function refresh() {
        try {
            const summaries = await profileFieldChanges.getPendingSummary()
            count.value = summaries.reduce((sum, s) => sum + s.pendingCount, 0)
        } catch {
            // ignore — user may not have permission
        }
    }

    return {
        pendingChangesCount: readonly(count),
        refresh,
    }
}
