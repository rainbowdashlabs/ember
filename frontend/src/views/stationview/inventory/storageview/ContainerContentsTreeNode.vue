/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import TreeNodeButton from '@/components/button/TreeNodeButton.vue'
import type {InventoryContainer, InventoryContainerKind} from '@/api/inventoryContainers'
import type {InventoryItem} from '@/api/types'

const props = defineProps<{
  container: InventoryContainer
  childrenByParent: Map<number, InventoryContainer[]>
  itemsByContainer: Map<number, InventoryItem[]>
  kindById: Map<number, InventoryContainerKind>
}>()

defineEmits<{
  (e: 'open-container', id: number): void
  (e: 'open-item', id: number): void
}>()

const childContainers = computed(() => props.childrenByParent.get(props.container.id) ?? [])
const items = computed(() => props.itemsByContainer.get(props.container.id) ?? [])
const kind = computed(() => props.container.kindId ? props.kindById.get(props.container.kindId) : null)
const expanded = ref(true)
const hasContent = computed(() => childContainers.value.length > 0 || items.value.length > 0)
</script>

<template>
  <div class="flex flex-col gap-1">
    <TreeNodeButton
        class="flex items-center gap-2 px-2 py-1.5 hover:bg-(--bg-accent)"
        @click="$emit('open-container', container.id)"
    >
      <span v-if="hasContent" class="-ml-1 w-4 h-4">
        <TreeNodeButton
            class="h-full flex items-center justify-center text-(--text-muted)"
            @click.stop="expanded = !expanded"
        >
          <font-awesome-icon :icon="['fas', expanded ? 'chevron-down' : 'chevron-right']" class="h-3 w-3" />
        </TreeNodeButton>
      </span>
      <span v-else class="w-4 h-4" />
      <font-awesome-icon :icon="['fas', kind?.icon ?? 'box']" class="w-4 text-(--text-muted)" />
      <span class="font-medium">{{ container.name }}</span>
      <span v-if="container.internalId" class="text-xs text-(--text-muted)">{{ container.internalId }}</span>
      <span v-if="hasContent" class="ml-auto text-xs text-(--text-muted)">
        {{ items.length }} · {{ childContainers.length }}
      </span>
    </TreeNodeButton>
    <div v-if="expanded && hasContent" class="pl-5 border-l border-(--bg-accent) ml-3 flex flex-col gap-1">
      <TreeNodeButton
          v-for="item in items"
          :key="`item-${item.id}`"
          class="flex items-center gap-2 px-2 py-1.5 hover:bg-(--bg-accent)"
          @click="$emit('open-item', item.id)"
      >
        <font-awesome-icon :icon="['fas', 'cube']" class="w-4 text-(--text-muted)" />
        <span class="font-medium">{{ item.name ?? '' }}</span>
        <span v-if="item.internalId" class="text-xs text-(--text-muted)">{{ item.internalId }}</span>
      </TreeNodeButton>
      <ContainerContentsTreeNode
          v-for="child in childContainers"
          :key="child.id"
          :container="child"
          :children-by-parent="childrenByParent"
          :items-by-container="itemsByContainer"
          :kind-by-id="kindById"
          @open-container="$emit('open-container', $event)"
          @open-item="$emit('open-item', $event)"
      />
    </div>
  </div>
</template>
