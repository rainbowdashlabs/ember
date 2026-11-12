/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryContainer from '@/components/container/PrimaryContainer.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import type {AdminStationSummary} from '@/api/generated/schema'
import StationTile from './StationTile.vue'

defineProps<{
  stations: AdminStationSummary[]
  onReminded?: (station: AdminStationSummary) => void
}>()

defineEmits<{
  import: []
  edit: [id: string]
  delete: [station: AdminStationSummary]
}>()

const {t} = useI18n()

/** The blank station form, which the first tile of the grid opens. */
const newStationPage = {name: 'admin-station-edit'}
</script>

<template>
  <div class="grid gap-4 sm:grid-cols-2">
    <RowLink :to="newStationPage">
      <PrimaryContainer
          data-onboarding="admin.stations.create"
          class="flex flex-col items-center justify-center gap-2 cursor-pointer py-6 border-dashed hover:opacity-80 transition-opacity h-full"
      >
        <font-awesome-icon :icon="['fas', 'plus']" class="text-2xl"/>
        <span class="font-medium">{{ t('adminStations.create') }}</span>
      </PrimaryContainer>
    </RowLink>

    <NeutralContainer
        class="flex flex-col items-center justify-center gap-2 cursor-pointer py-6 hover:opacity-80 transition-opacity"
        @click="$emit('import')"
    >
      <font-awesome-icon :icon="['fas', 'upload']" class="text-2xl text-(--text-muted)"/>
      <span class="font-medium">{{ t('adminStations.importStation') }}</span>
    </NeutralContainer>

    <StationTile
        v-for="station in stations"
        :key="station.id"
        :station="station"
        :on-reminded="onReminded"
        @edit="$emit('edit', $event)"
        @delete="$emit('delete', $event)"/>
  </div>
</template>
