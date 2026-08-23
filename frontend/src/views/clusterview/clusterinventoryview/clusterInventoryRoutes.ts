/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {InventoryRoutes} from '@/composables/useInventoryRoutes'

/**
 * Where the association's gear screens live.
 *
 * <p>The names it leaves out are the point. An association keeps gear in containers rather than on
 * people, so there is no member to open and no member check to walk. It hands gear to a station
 * rather than lending it to somebody, so none of the lending screens exist. A control that would
 * lead to one of those renders nowhere here, because nothing names it.
 */
export const CLUSTER_INVENTORY_ROUTES: InventoryRoutes = {
    manage: 'cluster-inventory',
    detail: 'cluster-inventory-detail',
    edit: 'cluster-inventory-edit',
    item: 'cluster-inventory-item',
    storage: 'cluster-inventory-storage',
    container: 'cluster-inventory-container',
    movement: 'cluster-inventory-movement',
    procurement: 'cluster-inventory-procurement',
    requirements: 'cluster-inventory-requirements',
    checks: 'cluster-inventory-check-container-overview',
    checkContainerOverview: 'cluster-inventory-check-container-overview',
    checkContainerWalk: 'cluster-inventory-check-container-walk',
    dispatch: 'cluster-inventory-dispatch',
}
