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

/** The same for what an association hands out, so one picker can draw either set. */
export async function getClusterPermissionHierarchy(): Promise<PermissionNode[]> {
    const res = await client.get<PermissionNode[]>('/data/cluster-permissions')
    return res.data
}

/**
 * The permissions worth naming out of a set somebody holds: the ones nothing else in the set already
 * carries.
 *
 * Listing a right beside the one that grants it says the same thing twice and buries what is actually
 * distinctive. Somebody holding the whole of member management should read as holding that, not as holding
 * it and the four smaller rights it is made of.
 */
export function highestOf(held: readonly string[], hierarchy: readonly PermissionNode[]): string[] {
    const children = new Map(hierarchy.map(node => [node.name, node.children]))
    const covered = new Set<string>()

    function cover(name: string) {
        for (const child of children.get(name) ?? []) {
            if (covered.has(child)) continue
            covered.add(child)
            cover(child)
        }
    }

    for (const name of held) cover(name)
    return held.filter(name => !covered.has(name))
}
