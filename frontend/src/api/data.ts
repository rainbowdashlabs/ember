/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type { PermissionNode } from './types'

export async function getPermissionHierarchy(): Promise<PermissionNode[]> {
    const res = await client.get<PermissionNode[]>('/data/permissions')
    return res.data
}
