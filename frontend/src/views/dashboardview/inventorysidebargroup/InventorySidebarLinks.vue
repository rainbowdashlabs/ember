/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import type {InventorySidebarLink} from './inventorySidebarLinks'

/**
 * A run of inventory entries, drawn wherever the group puts them.
 *
 * <p>The same list is shown in two shapes: under a heading of its own where there are several, and
 * plainly among the rest where the reader may reach only one. A heading naming a job the reader does
 * not have, with one entry folded underneath it, hides the one thing they came for.
 */
defineProps<{
  links: InventorySidebarLink[]
}>()

const emit = defineEmits<{
  (e: 'navigate'): void
}>()
</script>

<template>
  <SidebarLink
      v-for="link in links"
      :key="link.name"
      :badge="link.badge"
      :icon="link.icon"
      :name="link.name"
      :to="link.to"
      @navigate="emit('navigate')"
  >
    {{ link.label }}
  </SidebarLink>
</template>
