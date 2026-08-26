/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, onMounted, ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { PermissionNode } from '@/api/data'
import type { PermissionGrant } from '@/api/types'
import { data } from '@/api'

export interface TreeNode {
  name: string
  children: TreeNode[]
}

export interface FlatItem {
  name: string
  node: TreeNode
  chainDepth: number
}

/** Which set of permissions a picker is drawing, and the one that sits above all of them. */
export type PermissionScope = 'station' | 'cluster'

const ROOTS: Record<PermissionScope, string> = {
  station: 'STATION_ADMINISTRATOR',
  cluster: 'CLUSTER_ADMINISTRATOR',
}

/**
 * The permission hierarchy behind the picker: which permissions exist, which ones a selection
 * implies, and what toggling one does to the rest.
 *
 * A permission granted by an ancestor is shown as enabled but not editable, because selecting it
 * on its own would change nothing. Toggling a parent on therefore removes its explicitly selected
 * children - and remembers them, so toggling the parent back off in the same session restores the
 * selection the user had rather than silently discarding it.
 *
 * @param selected          the selected grant ids
 * @param allRoles          every grant, used to map a permission name onto its id
 * @param lockedPermissions permissions the caller grants unconditionally, with the reason shown
 * @param onChange          called with the new selection whenever a toggle changes it
 * @param scope             whose permissions these are, the station's or an association's
 */
export function usePermissionTree(
  selected: Ref<Set<number>>,
  allRoles: Ref<PermissionGrant[]>,
  lockedPermissions: Ref<Map<string, string> | undefined>,
  onChange: (next: Set<number>) => void,
  scope: PermissionScope = 'station',
) {
  const { t } = useI18n()

  const ROOT = ROOTS[scope]
  const hierarchy = ref<PermissionNode[]>([])
  const loading = ref(true)

  onMounted(async () => {
    hierarchy.value = scope === 'cluster'
      ? await data.getClusterPermissionHierarchy()
      : await data.getPermissionHierarchy()
    loading.value = false
  })

  const tree = computed<TreeNode[]>(() => {
    if (!hierarchy.value.length) return []

    const nodeMap = new Map<string, PermissionNode>()
    for (const n of hierarchy.value) nodeMap.set(n.name, n)

    function buildNode(name: string): TreeNode {
      return {name, children: nodeMap.get(name)?.children.map(buildNode) ?? []}
    }

    const admin = nodeMap.get(ROOT)
    if (!admin) return []

    function filterHidden(nodes: TreeNode[]): TreeNode[] {
      return nodes
        .filter(n => n.name !== 'USER')
        .map(n => ({...n, children: filterHidden(n.children)}))
    }

    // STATION_ADMINISTRATOR transitively grants every other permission, so listing its
    // descendants under the group header is just noise - keep the toggle but render it as a leaf.
    return filterHidden([{name: ROOT, children: []}, ...admin.children.map(buildNode)])
  })

  const roleByName = computed(() => {
    const map = new Map<string, PermissionGrant>()
    for (const r of allRoles.value) map.set(r.permission, r)
    return map
  })

  function isDirectlySelected(name: string): boolean {
    const role = roleByName.value.get(name)
    return role ? selected.value.has(role.id) : false
  }

  function isLocked(name: string): boolean {
    return lockedPermissions.value?.has(name) ?? false
  }

  function lockedLabel(name: string): string {
    return lockedPermissions.value?.get(name) ?? ''
  }

  /**
   * Maps each permission onto the ancestor that already grants it, so the picker can show it as
   * enabled-but-fixed rather than as something the user still has to tick.
   */
  const implicitlyGrantedBy = computed<Map<string, string>>(() => {
    const map = new Map<string, string>()

    function markDescendants(node: TreeNode, grantedBy: string) {
      for (const child of node.children) {
        if (!map.has(child.name)) map.set(child.name, grantedBy)
        markDescendants(child, grantedBy)
      }
    }

    function walk(nodes: TreeNode[]) {
      for (const node of nodes) {
        if (isDirectlySelected(node.name) || isLocked(node.name)) markDescendants(node, node.name)
        walk(node.children)
      }
    }

    walk(tree.value)

    if (isDirectlySelected(ROOT) || isLocked(ROOT)) {
      function markAll(nodes: TreeNode[]) {
        for (const node of nodes) {
          if (node.name !== ROOT && !map.has(node.name)) map.set(node.name, ROOT)
          markAll(node.children)
        }
      }
      markAll(tree.value)
    }
    return map
  })

  function isImplicit(name: string): boolean {
    return implicitlyGrantedBy.value.has(name)
  }

  function isEffectivelyEnabled(name: string): boolean {
    return isDirectlySelected(name) || isImplicit(name) || isLocked(name)
  }

  function isDisabled(name: string): boolean {
    return isImplicit(name) || isLocked(name)
  }

  function grantedByLabel(name: string): string {
    const grantedBy = implicitlyGrantedBy.value.get(name)
    return grantedBy ? t(`permissions.${grantedBy}.label`) : ''
  }

  function allChildNames(node: TreeNode): string[] {
    const result: string[] = []
    for (const child of node.children) {
      result.push(child.name, ...allChildNames(child))
    }
    return result
  }

  // Children removed because a parent was enabled, kept so unchecking the parent restores them.
  const collapsedChildren = ref<Map<string, Set<number>>>(new Map())

  function toggle(name: string, node: TreeNode) {
    if (isDisabled(name)) return
    const role = roleByName.value.get(name)
    if (!role) return
    const next = new Set(selected.value)

    if (next.has(role.id)) {
      next.delete(role.id)
      const saved = collapsedChildren.value.get(name)
      if (saved) {
        for (const id of saved) next.add(id)
        collapsedChildren.value.delete(name)
      }
    } else {
      next.add(role.id)
      const removed = new Set<number>()
      for (const childName of allChildNames(node)) {
        const childRole = roleByName.value.get(childName)
        if (childRole && next.has(childRole.id)) {
          removed.add(childRole.id)
          next.delete(childRole.id)
        }
      }
      if (removed.size) collapsedChildren.value.set(name, removed)
    }
    onChange(next)
  }

  function toggleLeaf(name: string) {
    if (isDisabled(name)) return
    const role = roleByName.value.get(name)
    if (!role) return
    const next = new Set(selected.value)
    if (next.has(role.id)) next.delete(role.id)
    else next.add(role.id)
    onChange(next)
  }

  const expanded = ref<Set<string>>(new Set())

  function toggleExpand(name: string) {
    const next = new Set(expanded.value)
    if (next.has(name)) next.delete(name)
    else next.add(name)
    expanded.value = next
  }

  function isExpanded(name: string): boolean {
    return expanded.value.has(name)
  }

  function countMaxDepth(node: TreeNode): number {
    if (!node.children.length) return 0
    return 1 + Math.max(...node.children.map(countMaxDepth))
  }

  /**
   * Every descendant of a node, deepest chain first, so the picker lists the permissions that
   * carry the most with them at the top.
   */
  function flattenDescendants(node: TreeNode): FlatItem[] {
    const items: FlatItem[] = []
    const seen = new Set<string>()
    function walk(n: TreeNode) {
      for (const child of n.children) {
        if (!seen.has(child.name)) {
          seen.add(child.name)
          items.push({name: child.name, node: child, chainDepth: countMaxDepth(child)})
        }
        walk(child)
      }
    }
    walk(node)
    return items.sort((a, b) => b.chainDepth - a.chainDepth)
  }

  function countEnabledDescendants(node: TreeNode): number {
    return flattenDescendants(node).filter(i => isEffectivelyEnabled(i.name)).length
  }

  function countTotalDescendants(node: TreeNode): number {
    return flattenDescendants(node).length
  }

  return {
    loading,
    tree,
    isDirectlySelected,
    isLocked,
    lockedLabel,
    isEffectivelyEnabled,
    isImplicit,
    isDisabled,
    grantedByLabel,
    toggle,
    toggleLeaf,
    toggleExpand,
    isExpanded,
    flattenDescendants,
    countEnabledDescendants,
    countTotalDescendants,
  }
}
