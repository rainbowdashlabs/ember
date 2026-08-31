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
    collections: string
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
    /**
     * Where what the store decides is set: the movement chains it walks and what a loss report must
     * carry. Absent at a station, where both sit on the stock screen itself, so the two panels render
     * there and nowhere else. An association has a Settings tab of its own and reaches both through
     * its own routes; the station's panels there asked for a right the association can never hold and
     * showed a refusal in place of a screen.
     */
    settings?: string
    /**
     * Where member groups are defined. Absent at an association: groups stay station-local, so an
     * association keys a requirement to a role and to nothing else.
     */
    memberGroups?: string
    exchanges?: string
    /**
     * Where pieces are moved from one inventory to another, which is how an inventory is split in
     * two without the pieces losing their identity. Absent at an association, whose store is not
     * divided along the line this exists to draw.
     */
    move?: string
    /**
     * Where the names written on the pieces are tidied up into kinds. Absent at an association,
     * whose store is not the drawer of different things this exists for.
     */
    tidy?: string
    /**
     * Where a station writes down an inventory it already owns, member by member. Absent at an
     * association: its store holds gear rather than handing it to people it keeps a list of.
     */
    intake?: string
    /** Absent at an association: it issues gear rather than lending it. */
    lending?: string
    lendingBlocks?: string
    lendingBlocksCreate?: string
    lendingCreate?: string
    lendingRequest?: string
    /** What the station currently puts on offer to its partners. Absent at an association for the same reason. */
    lendingShares?: string
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
    collections: 'inventory-collections',
    checks: 'inventory-checks',
    checkContainerOverview: 'inventory-check-container-overview',
    checkContainerWalk: 'inventory-check-container-walk',
    checkMember: 'inventory-check-member',
    checkResult: 'inventory-check-result',
    member: 'inventory-member',
    memberGroups: 'members-groups',
    exchanges: 'inventory-exchanges',
    move: 'inventory-move',
    tidy: 'inventory-tidy',
    intake: 'inventory-intake',
    lending: 'inventory-lending',
    lendingBlocks: 'inventory-lending-blocks',
    lendingBlocksCreate: 'inventory-lending-blocks-create',
    lendingCreate: 'inventory-lending-create',
    lendingRequest: 'inventory-lending-request',
    lendingShares: 'inventory-lending-shares',
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
