/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
export interface PermissionNode {
    name: string
    children: string[]
}

export async function getPermissionHierarchy(): Promise<PermissionNode[]> {
    const res = await client.get<PermissionNode[]>('/data/permissions')
    return res.data
}
