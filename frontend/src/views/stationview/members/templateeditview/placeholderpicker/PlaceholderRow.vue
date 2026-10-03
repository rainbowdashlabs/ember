/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import TreeNodeButton from '@/components/button/TreeNodeButton.vue'

/**
 * One entry of the placeholder picker: a step that leads further, marked by an arrow, or a placeholder
 * that is inserted, marked by a plus. A search match names the path it stands under beside it.
 */
defineProps<{
  name: string
  /** Whether choosing it leads to further entries rather than inserting a placeholder. */
  branch: boolean
  /** The path a search match stands under. */
  detail?: string
}>()

const emit = defineEmits<{
  choose: []
}>()
</script>

<template>
  <TreeNodeButton class="flex items-center gap-2 px-2 py-1.5 text-sm hover:bg-(--bg-accent)" @click="emit('choose')">
    <font-awesome-icon :icon="['fas', branch ? 'chevron-right' : 'plus']" class="w-3 shrink-0 text-(--text-muted)"/>
    <span class="min-w-0 flex-1 truncate">{{ name }}</span>
    <span v-if="detail" class="min-w-0 truncate text-xs text-(--text-muted)">{{ detail }}</span>
  </TreeNodeButton>
</template>
