/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import TreeNodeButton from '@/components/button/TreeNodeButton.vue'
import type {InventoryContainer, InventoryContainerKind} from '@/api/inventoryContainers'

const props = defineProps<{
  container: InventoryContainer
  childrenByParent: Map<number, InventoryContainer[]>
  kindById: Map<number, InventoryContainerKind>
  search: string
}>()

const emit = defineEmits<{
  (e: 'open', container: InventoryContainer): void
}>()

const children = computed(() => props.childrenByParent.get(props.container.id) ?? [])
const kind = computed(() => props.container.kindId ? props.kindById.get(props.container.kindId) : null)
const matchesSearch = computed(() => {
  if (!props.search) return true
  const term = props.search.toLowerCase()
  return props.container.name.toLowerCase().includes(term)
      || (props.container.internalId ?? '').toLowerCase().includes(term)
})
</script>

<template>
  <div>
    <TreeNodeButton
        class="flex items-center gap-2 px-2 py-1.5 hover:bg-(--bg-accent)"
        :class="matchesSearch ? '' : 'opacity-60'"
        @click="emit('open', container)"
    >
      <font-awesome-icon :icon="['fas', kind?.icon ?? 'box']" class="w-4 text-(--text-muted)" />
      <span class="font-medium">{{ container.name }}</span>
      <span v-if="container.internalId" class="text-xs text-(--text-muted)">{{ container.internalId }}</span>
      <span v-if="kind" class="ml-auto text-xs text-(--text-muted)">{{ kind.label }}</span>
    </TreeNodeButton>
    <ul v-if="children.length > 0" class="pl-5 border-l border-(--bg-accent) ml-3 mt-1 flex flex-col gap-1">
      <li v-for="child in children" :key="child.id">
        <ContainerTreeNode
            :container="child"
            :children-by-parent="childrenByParent"
            :kind-by-id="kindById"
            :search="search"
            @open="emit('open', $event)"
        />
      </li>
    </ul>
  </div>
</template>
