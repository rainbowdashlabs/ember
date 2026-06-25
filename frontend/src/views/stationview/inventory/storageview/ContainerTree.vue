/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ContainerTreeNode from '@/views/stationview/inventory/storageview/ContainerTreeNode.vue'
import type {InventoryContainer, InventoryContainerKind} from '@/api/inventoryContainers'

defineProps<{
  roots: InventoryContainer[]
  childrenByParent: Map<number, InventoryContainer[]>
  kindById: Map<number, InventoryContainerKind>
  search?: string
}>()

const emit = defineEmits<{
  (e: 'open', container: InventoryContainer): void
}>()
</script>

<template>
  <ul class="flex flex-col gap-1">
    <li v-for="root in roots" :key="root.id">
      <ContainerTreeNode
          :container="root"
          :children-by-parent="childrenByParent"
          :kind-by-id="kindById"
          :search="search ?? ''"
          @open="emit('open', $event)"
      />
    </li>
  </ul>
</template>
