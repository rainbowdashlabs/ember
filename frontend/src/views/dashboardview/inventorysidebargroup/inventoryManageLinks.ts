/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** One entry of the inventory group that is about running the inventory. */
export interface InventoryManageLink {
    name: string
    to: string
    icon: string[]
    label: string
    badge?: number
}
