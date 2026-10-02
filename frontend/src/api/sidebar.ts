/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {SidebarCounts} from './generated/schema'

export async function getSidebarCounts(): Promise<SidebarCounts> {
    const res = await client.get<SidebarCounts>('/sidebar-counts')
    return res.data
}
