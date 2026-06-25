/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 *
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import type {DiscoveredStation} from '@/api/discovery'

interface EnrichedStation extends DiscoveredStation {
  distance: number | null
}

defineProps<{
  station: EnrichedStation
}>()

defineEmits<{
  (e: 'focus'): void
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-2 cursor-pointer" @click="$emit('focus')">
    <div class="flex items-start justify-between gap-3">
      <div class="min-w-0 space-y-1">
        <p class="font-medium truncate">{{ station.name }}</p>
        <p v-if="station.slogan" class="text-xs text-(--text-muted) line-clamp-2">{{ station.slogan }}</p>
      </div>
      <div class="flex flex-col items-end gap-1 shrink-0">
        <PrimaryBadge v-if="station.distance != null">
          {{ t('lendingDistance.distanceKm', {distance: station.distance.toFixed(1)}) }}
        </PrimaryBadge>
        <SecondaryBadge>{{ station.memberCount }}</SecondaryBadge>
      </div>
    </div>
    <p v-if="station.city || station.country" class="text-xs text-(--text-muted)">
      {{ [station.city, station.country].filter(Boolean).join(', ') }}
    </p>
  </NeutralContainer>
</template>
