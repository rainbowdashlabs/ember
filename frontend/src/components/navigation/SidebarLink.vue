/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
  to: string
  name: string
  icon?: string[]
  badge?: number
}>()

defineEmits<{
  navigate: []
}>()
</script>

<template>
  <router-link
      :to="to"
      class="sidebar-link flex items-center gap-3 rounded-theme px-3 py-2 text-sm font-medium no-underline transition-colors duration-150"
      @click="$emit('navigate')"
  >
    <font-awesome-icon v-if="icon" :icon="icon" class="w-4"/>
    <span class="flex-1"><slot/></span>
    <span v-if="badge && badge > 0"
          class="inline-flex items-center justify-center min-w-5 h-5 px-1.5 rounded-full text-xs font-bold bg-error text-error-text">{{
        badge
      }}</span>
  </router-link>
</template>

<style scoped>
.sidebar-link {
  color: var(--text);
}
.sidebar-link:hover {
  background-color: color-mix(in srgb, var(--color-primary) 5%, transparent);
}
.sidebar-link.router-link-exact-active {
  color: var(--color-primary);
  background-color: color-mix(in srgb, var(--color-primary) 15%, transparent);
}
</style>
