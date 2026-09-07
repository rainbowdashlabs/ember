/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'

defineProps<{
  label: string
  checked: boolean
  indeterminate: boolean
  /** 0 for a year, 1 for a month, 2 for a day; indents the row. */
  depth: number
  expandable?: boolean
  expanded?: boolean
  expandLabel?: string
}>()

defineEmits<{
  toggle: []
  expand: []
}>()
</script>

<template>
  <div
      class="flex items-center gap-1 rounded px-2 py-1 text-xs hover:bg-bg-light-accent/50 dark:hover:bg-bg-dark-accent/50"
      :style="{paddingLeft: `${depth * 1.25 + 0.5}rem`}"
  >
    <button
        v-if="expandable"
        type="button"
        class="w-4 shrink-0 text-(--text-muted) hover:text-primary"
        :aria-label="expandLabel"
        :aria-expanded="expanded"
        @click="$emit('expand')"
    >
      <font-awesome-icon :icon="['fas', expanded ? 'chevron-down' : 'chevron-right']" class="h-3 w-3"/>
    </button>
    <span v-else class="w-4 shrink-0"/>
    <label class="flex flex-1 cursor-pointer items-center gap-2">
      <CheckboxInput :model-value="checked" :indeterminate="indeterminate" @update:model-value="$emit('toggle')"/>
      <span>{{ label }}</span>
    </label>
  </div>
</template>
