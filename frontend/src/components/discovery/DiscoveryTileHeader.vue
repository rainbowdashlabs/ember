/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import DiscoveryInstanceNote from '@/components/discovery/DiscoveryInstanceNote.vue'
import type {DiscoveryEntry} from '@/api/generated/schema'

/** The logo, or a placeholder where none can be shown, the station's name and, for a remote one, its host. */
defineProps<{
  station: DiscoveryEntry
  large?: boolean
}>()
</script>

<template>
  <div class="flex items-center gap-3">
    <img
        v-if="station.hasLogo && station.logoUrl"
        :src="station.logoUrl"
        :alt="station.name"
        :class="large ? 'w-14 h-14' : 'w-10 h-10'"
        class="rounded-full object-cover"
    />
    <div
        v-else
        :class="large ? 'w-14 h-14' : 'w-10 h-10'"
        class="rounded-full bg-[var(--bg-accent)] flex items-center justify-center shrink-0"
    >
      <font-awesome-icon :icon="['fas', 'building']" class="text-[var(--text-muted)]"/>
    </div>
    <div class="min-w-0 flex-1">
      <div :class="large ? 'text-lg' : ''" class="font-medium truncate">{{ station.name }}</div>
      <DiscoveryInstanceNote v-if="station.instanceHost" :host="station.instanceHost"/>
    </div>
  </div>
</template>
