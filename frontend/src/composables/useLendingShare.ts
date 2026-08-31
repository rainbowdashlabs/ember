/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type ComputedRef} from 'vue'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {useSession} from '@/composables/useSession'
import {StationPermission} from '@/api/types'

/**
 * Whether the sharing controls belong on this screen at all.
 *
 * <p>Two questions, and both have to be yes. Only somebody who may manage lending decides what goes
 * out; and only a station lends, so the same inventory screens shown for an association name no
 * lending route and offer nothing.
 */
export function useLendingShare(): { visible: ComputedRef<boolean> } {
    const routes = useInventoryRoutes()
    const {hasPermission} = useSession()

    const visible = computed(() =>
        Boolean(routes.lendingShares) && hasPermission(StationPermission.INVENTORY_LENDING_MANAGER),
    )

    return {visible}
}
