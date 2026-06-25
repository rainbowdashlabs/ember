/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
export interface EditableItem {
    id?: number
    tempId: number
    title: string
    description: string
    isPublic: boolean
    userAssigned: boolean
    position: number
    dependsOn: number[]
}
