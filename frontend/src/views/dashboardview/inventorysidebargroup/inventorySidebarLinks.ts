/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** One entry of the inventory group, wherever in it that entry sits. */
export interface InventorySidebarLink {
    name: string
    to: string
    icon: string[]
    label: string
    badge?: number
}
