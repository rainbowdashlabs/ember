/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type { RequirementResponse } from '@/api/generated/schema'

/**
 * A grouping of inventory requirements rendered as a single card in the
 * requirements view. Each group represents either a user type or a member
 * group.
 */
export interface RequirementGroup {
    type: 'userType' | 'group'
    key: string
    label: string
    items: RequirementResponse[]
}

export { StationUserTypeLabels as userTypeFriendlyNames } from '@/api/types'
