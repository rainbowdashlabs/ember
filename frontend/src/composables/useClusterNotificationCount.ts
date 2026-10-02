/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly} from 'vue'
import {clusterInbox} from '@/api/notifications'

/**
 * How many of the association's notifications the reader has not read, held once for the bell in
 * the association's menu and the inbox that changes it.
 */
export function useClusterNotificationCount() {
    const count = useState<number>('useClusterNotificationCount', () => 0)

    /** Reads the count again, keeping the last one where the association's inbox cannot be read. */
    async function refresh() {
        await clusterInbox.count().then(found => { count.value = found }).catch(() => {})
    }

    return {
        count: readonly(count),
        refresh,
    }
}
