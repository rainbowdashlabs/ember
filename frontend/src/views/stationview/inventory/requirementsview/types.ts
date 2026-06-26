/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type { InventoryRequirement } from '@/api/types'

/**
 * A grouping of inventory requirements rendered as a single card in the
 * requirements view. Each group represents either a user type or a member
 * group.
 */
export interface RequirementGroup {
    type: 'userType' | 'group'
    key: string
    label: string
    items: InventoryRequirement[]
}

export { StationUserTypeLabels as userTypeFriendlyNames } from '@/api/types'
