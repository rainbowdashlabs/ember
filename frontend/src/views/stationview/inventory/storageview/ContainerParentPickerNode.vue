/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import type {InventoryContainer, InventoryContainerKind} from '@/api/inventoryContainers'

const props = defineProps<{
  container: InventoryContainer
  childrenByParent: Map<number, InventoryContainer[]>
  kindById: Map<number, InventoryContainerKind>
  hiddenIds: Set<number>
  selectedId: number | null
  expandedPath: Set<number>
  depth: number
}>()

const emit = defineEmits<{
  (e: 'select', id: number): void
}>()

const visibleChildren = computed(() =>
    (props.childrenByParent.get(props.container.id) ?? []).filter(c => !props.hiddenIds.has(c.id)),
)
const kind = computed(() => props.container.kindId ? props.kindById.get(props.container.kindId) : null)
const selected = computed(() => props.selectedId === props.container.id)
const expanded = ref(props.expandedPath.has(props.container.id))

watch(() => props.expandedPath, (set) => {
  if (set.has(props.container.id)) expanded.value = true
})

function toggle(ev: Event) {
  ev.stopPropagation()
  expanded.value = !expanded.value
}

function select() {
  emit('select', props.container.id)
}
</script>

<template>
  <div>
    <div
        class="flex items-center gap-2 px-2 py-1.5 rounded-theme cursor-pointer transition-colors hover:bg-(--bg-accent)"
        :class="selected ? 'bg-primary/15 text-primary' : ''"
        @click="select"
    >
      <button
          v-if="visibleChildren.length > 0"
          type="button"
          class="w-5 h-5 flex items-center justify-center rounded hover:bg-(--bg-accent) text-(--text-muted)"
          @click="toggle"
      >
        <font-awesome-icon :icon="['fas', expanded ? 'chevron-down' : 'chevron-right']" class="text-xs" />
      </button>
      <span v-else class="w-5 h-5 inline-block" />
      <font-awesome-icon :icon="['fas', kind?.icon ?? 'box']" class="w-4 text-(--text-muted)" />
      <span class="font-medium truncate">{{ container.name }}</span>
      <span v-if="container.internalId" class="text-xs text-(--text-muted) truncate">{{ container.internalId }}</span>
      <span v-if="kind" class="ml-auto text-xs text-(--text-muted) truncate">{{ kind.label }}</span>
    </div>
    <ul v-if="expanded && visibleChildren.length > 0" class="pl-5 border-l border-(--bg-accent) ml-3 mt-1 flex flex-col gap-1">
      <li v-for="child in visibleChildren" :key="child.id">
        <ContainerParentPickerNode
            :container="child"
            :children-by-parent="childrenByParent"
            :kind-by-id="kindById"
            :hidden-ids="hiddenIds"
            :selected-id="selectedId"
            :expanded-path="expandedPath"
            :depth="depth + 1"
            @select="(id) => emit('select', id)"
        />
      </li>
    </ul>
  </div>
</template>
