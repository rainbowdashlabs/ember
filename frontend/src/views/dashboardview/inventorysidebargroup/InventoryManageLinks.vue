/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import type {InventoryManageLink} from './inventoryManageLinks'

/**
 * The entries of the inventory group that are about running it.
 *
 * <p>Their own component because they are shown in two shapes: under a heading of their own where
 * there are several, and plainly among the rest where the reader may reach only one. Written twice
 * they would drift apart.
 */
defineProps<{
  links: InventoryManageLink[]
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
