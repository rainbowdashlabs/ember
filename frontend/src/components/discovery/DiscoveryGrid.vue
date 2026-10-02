/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import DiscoveryTile from '@/components/discovery/DiscoveryTile.vue'
import {discoveryKey} from '@/components/discovery/tileRules'
import type {DiscoveryViewer} from '@/composables/useDiscoveryViewer'
import type {DiscoveryEntry} from '@/api/generated/schema'

defineProps<{
  stations: DiscoveryEntry[]
  viewer: DiscoveryViewer
}>()

const emit = defineEmits<{
  connect: [station: DiscoveryEntry]
  invite: [station: DiscoveryEntry]
  locate: [station: DiscoveryEntry]
}>()
</script>

<template>
  <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
    <DiscoveryTile
        v-for="station in stations"
        :key="discoveryKey(station)"
        :station="station"
        :viewer="viewer"
        @connect="s => emit('connect', s)"
        @invite="s => emit('invite', s)"
        @locate="s => emit('locate', s)"
    />
  </div>
</template>
