/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ContainerContentsTreeNode from './ContainerContentsTreeNode.vue'
import TreeNodeButton from '@/components/button/TreeNodeButton.vue'
import type {InventoryContainer, InventoryContainerKind} from '@/api/inventoryContainers'
import type {InventoryItem} from '@/api/types'

const props = defineProps<{
  rootContainerId: number
  containers: InventoryContainer[]
  items: InventoryItem[]
  kindById: Map<number, InventoryContainerKind>
}>()

defineEmits<{
  (e: 'open-container', id: number): void
  (e: 'open-item', id: number): void
}>()

const itemsByContainer = computed(() => {
  const map = new Map<number, InventoryItem[]>()
  for (const item of props.items) {
    if (item.containerId == null) continue
    const list = map.get(item.containerId) ?? []
    list.push(item)
    map.set(item.containerId, list)
  }
  for (const list of map.values()) list.sort((a, b) => (a.name ?? '').localeCompare(b.name ?? ''))
  return map
})

const childrenByParent = computed(() => {
  const map = new Map<number, InventoryContainer[]>()
  for (const c of props.containers) {
    if (c.id === props.rootContainerId) continue
    const parent = c.parentId ?? props.rootContainerId
    const list = map.get(parent) ?? []
    list.push(c)
    map.set(parent, list)
  }
  for (const list of map.values()) list.sort((a, b) => a.name.localeCompare(b.name))
  return map
})

const rootChildren = computed(() => childrenByParent.value.get(props.rootContainerId) ?? [])
const rootItems = computed(() => itemsByContainer.value.get(props.rootContainerId) ?? [])

const {t} = useI18n()
</script>

<template>
  <div class="flex flex-col gap-1">
    <template v-if="rootItems.length > 0">
      <TreeNodeButton
          v-for="item in rootItems"
          :key="`root-item-${item.id}`"
          class="flex items-center gap-2 px-2 py-1.5 hover:bg-(--bg-accent)"
          @click="$emit('open-item', item.id)"
      >
        <font-awesome-icon :icon="['fas', 'cube']" class="w-4 text-(--text-muted)" />
        <span class="font-medium">{{ item.name ?? '' }}</span>
        <span v-if="item.internalId" class="text-xs text-(--text-muted)">{{ item.internalId }}</span>
      </TreeNodeButton>
    </template>
    <template v-if="rootChildren.length === 0 && rootItems.length === 0">
      <p class="text-sm text-(--text-muted) py-2">{{ t('inventory.storage.contentsEmpty') }}</p>
    </template>
    <ContainerContentsTreeNode
        v-for="child in rootChildren"
        :key="child.id"
        :container="child"
        :children-by-parent="childrenByParent"
        :items-by-container="itemsByContainer"
        :kind-by-id="kindById"
        @open-container="$emit('open-container', $event)"
        @open-item="$emit('open-item', $event)"
    />
  </div>
</template>
