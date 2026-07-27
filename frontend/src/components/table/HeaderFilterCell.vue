/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
  label: string
  sortIcon?: string
  hasFilter?: boolean
  showSort?: boolean
  showFilter?: boolean
}>()

const emit = defineEmits<{
  sort: []
  filter: []
}>()
</script>

<template>
  <span class="inline-flex items-center gap-1">
    {{ label }}
    <font-awesome-icon
        v-if="showSort && sortIcon"
        :icon="['fas', sortIcon]"
        class="h-3 w-3 text-(--text-muted) cursor-pointer hover:text-primary"
        @click="emit('sort')"
    />
    <font-awesome-icon
        v-if="showFilter"
        :icon="['fas', 'filter']"
        :class="hasFilter ? 'text-primary' : 'text-(--text-muted)'"
        class="h-3 w-3 cursor-pointer hover:text-primary"
        @click.stop="emit('filter')"
    />
  </span>
</template>
