/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useSidebarCollapse} from '@/composables/useSidebarCollapse'
import {useSidebarInFlyout} from '@/composables/useSidebarFlyoutContext'

defineProps<{
  to: string
  name: string
  icon?: string[]
  badge?: number
}>()

defineEmits<{
  navigate: []
}>()

const {collapsed} = useSidebarCollapse()
const inFlyout = useSidebarInFlyout()
const labelHidden = computed(() => collapsed.value && !inFlyout)
</script>

<template>
  <router-link
      :to="to"
      class="sidebar-link flex items-center gap-3 rounded-theme py-2 text-sm font-medium no-underline transition-colors duration-150"
      :class="labelHidden ? 'lg:justify-center lg:px-2 px-3' : 'px-3'"
      @click="$emit('navigate')"
  >
    <font-awesome-icon v-if="icon" :icon="icon" class="w-4 shrink-0"/>
    <span class="flex-1 truncate" :class="labelHidden ? 'lg:hidden' : ''"><slot/></span>
    <span v-if="badge && badge > 0"
          class="inline-flex items-center justify-center min-w-5 h-5 px-1.5 rounded-full text-xs font-bold bg-error text-error-text"
          :class="labelHidden ? 'lg:hidden' : ''">{{
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
