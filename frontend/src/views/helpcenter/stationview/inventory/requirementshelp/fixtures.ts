/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {RequirementResponse} from '@/api/generated/schema'
import {StationUserType, StationUserTypeLabels} from '@/api/types'
import type {RequirementGroup} from '@/views/stationview/inventory/requirementsview/types'

/** A requirement as the station wrote it itself, with nothing about it out of the ordinary. */
function requirement(fields: Pick<RequirementResponse, 'id' | 'inventoryId' | 'inventoryName' | 'position'> & Partial<RequirementResponse>): RequirementResponse {
    return {clusterName: null, groupId: 0, quantity: 1, stationGroupId: null, userType: null, ...fields}
}

/** Everybody who is a member needs a helmet and a jacket; beginners need boots on top. */
export const groups: RequirementGroup[] = [
    {
        type: 'userType',
        key: StationUserType.MEMBER,
        label: StationUserTypeLabels.MEMBER,
        items: [
            requirement({id: 1, inventoryId: 1, inventoryName: 'Helme', position: 0, userType: StationUserType.MEMBER}),
            requirement({id: 2, inventoryId: 2, inventoryName: 'Jacken', position: 1, userType: StationUserType.MEMBER}),
        ],
    },
    {
        type: 'group',
        key: '1',
        label: 'Anfänger',
        items: [
            requirement({id: 3, inventoryId: 3, inventoryName: 'Stiefel', position: 0, groupId: 1}),
        ],
    },
]

/** The name of an inventory, which every fixture requirement already carries. */
export function inventoryName(id: number): string {
    return `#${id}`
}
