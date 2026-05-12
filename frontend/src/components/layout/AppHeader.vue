/*
*     SPDX-License-Identifier: AGPL-3.0-only
*
*     Copyright (C) RainbowDashLabs and Contributor
*/
<script lang="ts" setup>
defineProps<{
  title?: string
  subtitle?: string
}>()

defineEmits<{
  toggleSidebar: []
}>()
</script>

<template>
  <header class="flex h-14 items-center border-b border-bg-light-accent dark:border-bg-dark-accent px-4 gap-4">
    <button
        aria-label="Menu"
        class="p-2 rounded-lg text-[var(--text)] hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent lg:hidden"
        @click="$emit('toggleSidebar')"
    >
      <font-awesome-icon :icon="['fas', 'bars']" class="h-5 w-5"/>
    </button>

    <div v-if="title" class="flex flex-col justify-center min-w-0">
      <span class="text-base font-semibold truncate">{{ title }}</span>
      <span v-if="subtitle" class="text-xs text-[var(--text-muted)] truncate">{{ subtitle }}</span>
    </div>

    <div class="flex-1"/>

    <div class="flex items-center gap-2">
      <slot/>
    </div>
  </header>
</template>
