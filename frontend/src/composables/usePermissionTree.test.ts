/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {nestDescendants, type TreeNode} from '@/composables/usePermissionTree'

const children: Record<string, string[]> = {
    INVENTORY_MANAGER: ['INVENTORY_ASSIGN', 'INVENTORY_CREATE', 'INVENTORY_EDIT', 'INVENTORY_LENDING_MANAGER', 'INVENTORY_READ'],
    INVENTORY_EDIT: ['INVENTORY_CREATE'],
    INVENTORY_CREATE: ['INVENTORY_CREATE_INTERNAL', 'INVENTORY_READ'],
    INVENTORY_ASSIGN: ['INVENTORY_READ'],
    INVENTORY_LENDING_MANAGER: ['INVENTORY_LENDING_REQUEST'],
}

function node(name: string): TreeNode {
    return {name, children: (children[name] ?? []).map(node)}
}

function parentsOf(): Map<string, string[]> {
    const map = new Map<string, string[]>()
    for (const [parent, kids] of Object.entries(children)) {
        for (const kid of kids) map.set(kid, [...(map.get(kid) ?? []), parent])
    }
    return map
}

function layout(group: string): string[] {
    return nestDescendants(node(group), parentsOf()).map(item => `${'  '.repeat(item.depth)}${item.name}`)
}

describe('nestDescendants', () => {
    it('nests a permission with one parent under that parent', () => {
        const items = nestDescendants(node('INVENTORY_MANAGER'), parentsOf())
        const request = items.find(item => item.name === 'INVENTORY_LENDING_REQUEST')

        expect(request?.depth).toBe(1)
        expect(request?.alsoIncludedIn).toEqual([])
    })

    it('places a permission with several parents under the deepest one and names the others', () => {
        const items = nestDescendants(node('INVENTORY_MANAGER'), parentsOf())
        const read = items.find(item => item.name === 'INVENTORY_READ')

        expect(read?.depth).toBe(2)
        expect(read?.alsoIncludedIn).toEqual(['INVENTORY_MANAGER', 'INVENTORY_ASSIGN'])
    })

    it('lists every permission once, each right after the one it sits under', () => {
        expect(layout('INVENTORY_MANAGER')).toEqual([
            'INVENTORY_EDIT',
            '  INVENTORY_CREATE',
            '    INVENTORY_READ',
            '    INVENTORY_CREATE_INTERNAL',
            'INVENTORY_ASSIGN',
            'INVENTORY_LENDING_MANAGER',
            '  INVENTORY_LENDING_REQUEST',
        ])
    })
})
