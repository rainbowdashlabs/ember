/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ContainerParentPickerNode from '@/views/stationview/inventory/storageview/ContainerParentPickerNode.vue'
import type {InventoryContainer, InventoryContainerKind} from '@/api/inventoryContainers'

const props = defineProps<{
  containers: InventoryContainer[]
  kinds: InventoryContainerKind[]
  excludeId?: number | null
}>()

const model = defineModel<number | null>({required: true})

const {t} = useI18n()

const kindById = computed(() => {
  const m = new Map<number, InventoryContainerKind>()
  for (const k of props.kinds) m.set(k.id, k)
  return m
})

const childrenByParent = computed(() => {
  const m = new Map<number, InventoryContainer[]>()
  for (const c of props.containers) {
    if (c.parentId == null) continue
    const list = m.get(c.parentId) ?? []
    list.push(c)
    m.set(c.parentId, list)
  }
  for (const list of m.values()) list.sort((a, b) => a.name.localeCompare(b.name))
  return m
})

const hiddenIds = computed(() => {
  const set = new Set<number>()
  if (props.excludeId == null) return set
  const stack = [props.excludeId]
  while (stack.length > 0) {
    const id = stack.pop()!
    if (set.has(id)) continue
    set.add(id)
    for (const child of childrenByParent.value.get(id) ?? []) stack.push(child.id)
  }
  return set
})

const roots = computed(() =>
    props.containers
        .filter(c => c.parentId == null && !hiddenIds.value.has(c.id))
        .sort((a, b) => a.name.localeCompare(b.name)),
)

const expandedPath = computed(() => {
  const set = new Set<number>()
  const byId = new Map<number, InventoryContainer>()
  for (const c of props.containers) byId.set(c.id, c)
  let cursor = model.value
  while (cursor != null) {
    const node = byId.get(cursor)
    if (!node || !node.parentId) break
    set.add(node.parentId)
    cursor = node.parentId
  }
  return set
})

function select(id: number) {
  model.value = id
}

function selectNone() {
  model.value = null
}
</script>

<template>
  <div class="border border-bg-light-accent dark:border-bg-dark-accent rounded-theme bg-(--bg) max-h-72 overflow-auto p-2">
    <div
        class="flex items-center gap-2 px-2 py-1.5 rounded-theme cursor-pointer transition-colors hover:bg-(--bg-accent)"
        :class="model === null ? 'bg-primary/15 text-primary' : ''"
        @click="selectNone"
    >
      <span class="w-5 h-5 inline-block" />
      <font-awesome-icon :icon="['fas', 'minus']" class="w-4 text-(--text-muted)" />
      <span class="font-medium">{{ t('inventory.storage.fields.parentNone') }}</span>
    </div>
    <ul v-if="roots.length > 0" class="mt-1 flex flex-col gap-1">
      <li v-for="root in roots" :key="root.id">
        <ContainerParentPickerNode
            :container="root"
            :children-by-parent="childrenByParent"
            :kind-by-id="kindById"
            :hidden-ids="hiddenIds"
            :selected-id="model"
            :expanded-path="expandedPath"
            :depth="0"
            @select="select"
        />
      </li>
    </ul>
  </div>
</template>
