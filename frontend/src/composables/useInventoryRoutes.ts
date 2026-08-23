/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {inject, provide, type InjectionKey} from 'vue'

/**
 * The pages the inventory screens are mounted on.
 *
 * <p>An association's gear is a station's gear, kept on the station the association owns and shown
 * through the station's own screens. What differs is where a click lands, because the two sets of
 * pages live at different addresses, and which screens exist at all.
 *
 * <p>An optional key is a screen the owner does not have. A control that leads to one renders only
 * when the routes name it, which is what stops an association's screen from offering a way into a
 * page that is a station's alone. The alternative would be a branch on "is this a cluster" in every
 * component that has such a control, and there are many.
 */
export interface InventoryRoutes {
    manage: string
    detail: string
    edit: string
    item: string
    storage: string
    container: string
    movement: string
    procurement: string
    requirements: string
    checks: string
    checkContainerOverview: string
    checkContainerWalk: string
    /** Absent at an association: it keeps gear in containers, not on people. */
    checkMember?: string
    checkResult?: string
    /** Absent at an association: it dispatches gear to a station, not to a person. */
    member?: string
    /** Absent at a station: sending gear out of the store is the owner's act, not the holder's. */
    dispatch?: string
    exchanges?: string
    /** Absent at an association: it issues gear rather than lending it. */
    lending?: string
    lendingBlocks?: string
    lendingBlocksCreate?: string
    lendingCreate?: string
    lendingRequest?: string
}

export const STATION_INVENTORY_ROUTES: InventoryRoutes = {
    manage: 'inventory-manage',
    detail: 'inventory-detail',
    edit: 'inventory-edit',
    item: 'inventory-item-detail',
    storage: 'inventory-storage',
    container: 'inventory-container-detail',
    movement: 'inventory-movement-detail',
    procurement: 'inventory-procurement',
    requirements: 'inventory-requirements',
    checks: 'inventory-checks',
    checkContainerOverview: 'inventory-check-container-overview',
    checkContainerWalk: 'inventory-check-container-walk',
    checkMember: 'inventory-check-member',
    checkResult: 'inventory-check-result',
    member: 'inventory-member',
    exchanges: 'inventory-exchanges',
    lending: 'inventory-lending',
    lendingBlocks: 'inventory-lending-blocks',
    lendingBlocksCreate: 'inventory-lending-blocks-create',
    lendingCreate: 'inventory-lending-create',
    lendingRequest: 'inventory-lending-request',
}

const INVENTORY_ROUTES: InjectionKey<InventoryRoutes> = Symbol('inventoryRoutes')

/** Mounts the inventory screens below this one on another set of pages. Called by the page, not the view. */
export function provideInventoryRoutes(routes: InventoryRoutes): void {
    provide(INVENTORY_ROUTES, routes)
}

/** Where a click in the inventory screens should land, which is a station's pages unless told otherwise. */
export function useInventoryRoutes(): InventoryRoutes {
    return inject(INVENTORY_ROUTES, STATION_INVENTORY_ROUTES)
}
