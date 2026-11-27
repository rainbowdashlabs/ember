/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, onMounted, ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type {Permission, PermissionNode} from '@/api/generated/schema'
import { data } from '@/api'

export interface TreeNode {
  name: string
  children: TreeNode[]
}

export interface FlatItem {
  name: string
  node: TreeNode
  /** How far the item is indented below its group: 0 for a direct child of the group. */
  depth: number
  /** The other permissions that include this one besides the one it is listed under. */
  alsoIncludedIn: string[]
}

function countMaxDepth(node: TreeNode): number {
  if (!node.children.length) return 0
  return 1 + Math.max(...node.children.map(countMaxDepth))
}

/**
 * Lists every descendant of a group once, as a tree. A permission with one parent sits under that
 * parent. One with several sits under the parent nested deepest in the group, and the others are
 * returned beside it, so the picker can show where else it is included. Siblings that carry the
 * longest chain with them come first.
 *
 * @param group     the group whose descendants are listed
 * @param parentsOf every permission's direct parents across the whole hierarchy, root excluded
 */
export function nestDescendants(group: TreeNode, parentsOf: Map<string, string[]>): FlatItem[] {
  const nodes = new Map<string, TreeNode>()
  function collect(node: TreeNode) {
    for (const child of node.children) {
      if (nodes.has(child.name)) continue
      nodes.set(child.name, child)
      collect(child)
    }
  }
  collect(group)

  const depthMemo = new Map<string, number>([[group.name, 0]])
  function depthOf(name: string): number {
    const known = depthMemo.get(name)
    if (known !== undefined) return known
    const inGroup = (parentsOf.get(name) ?? []).filter(p => p === group.name || nodes.has(p))
    const depth = 1 + Math.max(0, ...inGroup.map(depthOf))
    depthMemo.set(name, depth)
    return depth
  }

  const childrenOf = new Map<string, TreeNode[]>()
  const homeOf = new Map<string, string>()
  for (const [name, node] of nodes) {
    const inGroup = (parentsOf.get(name) ?? []).filter(p => p === group.name || nodes.has(p))
    const home = inGroup.reduce((best, p) => (depthOf(p) > depthOf(best) ? p : best), inGroup[0] ?? group.name)
    homeOf.set(name, home)
    childrenOf.set(home, [...(childrenOf.get(home) ?? []), node])
  }

  const items: FlatItem[] = []
  function emit(parent: string, depth: number) {
    const children = [...(childrenOf.get(parent) ?? [])].sort((a, b) => countMaxDepth(b) - countMaxDepth(a))
    for (const child of children) {
      const alsoIncludedIn = (parentsOf.get(child.name) ?? []).filter(p => p !== homeOf.get(child.name))
      items.push({name: child.name, node: child, depth, alsoIncludedIn})
      emit(child.name, depth + 1)
    }
  }
  emit(group.name, 0)
  return items
}

/** Which set of permissions a picker is drawing, and the one that sits above all of them. */
export type PermissionScope = 'station' | 'cluster'

/**
 * One numbered grant as the picker draws it: a station's permission row as the server sends it, or
 * an association's permission name, which its port numbers on the way out because the association's
 * API speaks names only.
 */
export type PermissionGrant = Omit<Permission, 'permission'> & {permission: string}

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

  /**
   * The permissions as the picker shows them. The root grants every other permission, so it stands
   * as a leaf toggle beside its children rather than as a header listing them again.
   */
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

    return filterHidden([{name: ROOT, children: []}, ...admin.children.map(buildNode)])
  })

  const parentsOf = computed(() => {
    const map = new Map<string, string[]>()
    for (const node of hierarchy.value) {
      if (node.name === ROOT) continue
      for (const child of node.children) map.set(child, [...(map.get(child) ?? []), node.name])
    }
    return map
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

  /** Children removed because a parent was enabled, kept so unchecking the parent restores them. */
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

  /** Every descendant of a group once, nested under the permission that includes it. */
  function flattenDescendants(node: TreeNode): FlatItem[] {
    return nestDescendants(node, parentsOf.value)
  }

  function alsoIncludedInLabel(item: FlatItem): string {
    return item.alsoIncludedIn.map(name => t(`permissions.${name}.label`)).join(', ')
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
    alsoIncludedInLabel,
    countEnabledDescendants,
    countTotalDescendants,
  }
}
