/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {Comment as VComment, type VNode} from 'vue'

/**
 * The entries a sidebar slot actually holds, with the fragments a `v-if` or `v-for` leaves behind
 * flattened away and the placeholder comments of a false `v-if` dropped.
 *
 * <p>Only fragments are descended into. An earlier attempt recursed into any array of children it found,
 * read a property off something that was not a vnode, and took every sidebar in Ember down with it.
 */
export function sidebarEntryVNodes(vnodes: VNode[]): VNode[] {
    const entries: VNode[] = []
    for (const vnode of vnodes) {
        if (!vnode || vnode.type === VComment) continue
        if (typeof vnode.type === 'symbol' && Array.isArray(vnode.children)) {
            entries.push(...sidebarEntryVNodes(vnode.children as VNode[]))
        } else {
            entries.push(vnode)
        }
    }
    return entries
}

/**
 * Every address the entries of a sidebar group lead to, read off the entries themselves.
 *
 * <p>A group that restates where its entries go can disagree with them, and did: a section stayed dark on
 * a page it holds, or a subsection collapsed on a page it links to. Read off the entries there is no
 * second opinion to disagree with.
 *
 * <p>Both what an entry links to and what a nested subsection declares count, so a section reaches
 * everything its subsections do without being told twice.
 */
export function collectSidebarPaths(vnodes: VNode[]): string[] {
    const paths: string[] = []
    for (const entry of sidebarEntryVNodes(vnodes)) {
        const props = entry.props as { to?: unknown, prefix?: unknown } | null
        addPath(props?.to, paths)
        if (Array.isArray(props?.prefix)) props.prefix.forEach(written => addPath(written, paths))
        else addPath(props?.prefix, paths)
    }
    return paths
}

function addPath(value: unknown, into: string[]): void {
    if (typeof value === 'string' && value.startsWith('/')) into.push(value)
}
