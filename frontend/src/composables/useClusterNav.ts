/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useSession} from '@/composables/useSession'
import {useClusterNotificationCount} from '@/composables/useClusterNotificationCount'
import {CLUSTER_NAV} from '@/data/sidebar/clusterNav'
import {resolveNav} from '@/util/navTree'

/** The association sidebar as the reader sees it, which the sidebar renders and the palette searches. */
export function useClusterNav() {
    const {t} = useI18n()
    const {hasClusterPermission} = useSession()
    const {count} = useClusterNotificationCount()

    const nodes = computed(() => resolveNav(CLUSTER_NAV, {hasClusterPermission, unreadNotifications: count.value}, t))

    return {nodes}
}
