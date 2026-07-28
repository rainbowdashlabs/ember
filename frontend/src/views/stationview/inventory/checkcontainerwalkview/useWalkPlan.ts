/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type Ref} from 'vue'
import type {InventoryContainer} from '@/api/inventoryContainers'
import {containerPathFor} from '@/util/containerPath'
import type {ExpectedRow} from './types'

interface WalkPlanOptions {
  containers: Ref<InventoryContainer[]>
  rows: Ref<ExpectedRow[]>
  root: Ref<InventoryContainer | null>
  deep: Ref<boolean>
}

/**
 * Derives the container order a deep check walks through and the expected rows
 * that belong to each container of that order.
 */
export function useWalkPlan(options: WalkPlanOptions) {
  const containerById = computed(() => {
    const m = new Map<number, InventoryContainer>()
    for (const c of options.containers.value) m.set(c.id, c)
    return m
  })

  const childrenByParent = computed(() => {
    const m = new Map<number, InventoryContainer[]>()
    for (const c of options.containers.value) {
      if (c.parentId == null) continue
      const list = m.get(c.parentId) ?? []
      list.push(c)
      m.set(c.parentId, list)
    }
    for (const list of m.values()) list.sort((a, b) => a.name.localeCompare(b.name))
    return m
  })

  const rowsByContainer = computed(() => {
    const m = new Map<number, ExpectedRow[]>()
    for (const row of options.rows.value) {
      const cid = row.item.containerId ?? null
      if (cid == null) continue
      const list = m.get(cid) ?? []
      list.push(row)
      m.set(cid, list)
    }
    return m
  })

  const walkOrder = computed<InventoryContainer[]>(() => {
    const start = options.root.value
    if (!start) return []
    const root = containerById.value.get(start.id) ?? start
    if (!options.deep.value) return [root]
    const out: InventoryContainer[] = []
    const stack: InventoryContainer[] = [root]
    while (stack.length > 0) {
      const node = stack.pop()!
      out.push(node)
      const children = childrenByParent.value.get(node.id) ?? []
      for (let i = children.length - 1; i >= 0; i--) stack.push(children[i])
    }
    return out.filter(c => (rowsByContainer.value.get(c.id)?.length ?? 0) > 0)
  })

  function rowsFor(containerId: number): ExpectedRow[] {
    return rowsByContainer.value.get(containerId) ?? []
  }

  function pathFor(container: InventoryContainer): string {
    return containerPathFor(containerById.value, container.id)
  }

  return {containerById, walkOrder, rowsFor, pathFor}
}
