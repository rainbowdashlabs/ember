/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {AdminStationSummary} from '@/api/generated/schema'
import StationLogo from './StationLogo.vue'
import StationStateBadge from './StationStateBadge.vue'
import ManagerReminder from './ManagerReminder.vue'

/**
 * One station as a tile: its logo, name and state, how many members it has, its association and its
 * manager, and for a station that waits the reminder to that manager.
 */
defineProps<{
  station: AdminStationSummary
  onReminded?: (station: AdminStationSummary) => void
}>()

defineEmits<{
  edit: [id: string]
  delete: [station: AdminStationSummary]
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="flex flex-col gap-3" data-testid="station-tile">
    <div class="flex items-start gap-3">
      <StationLogo :uid="station.id" :name="station.name"/>
      <div class="min-w-0 flex-1 space-y-1">
        <div class="font-medium text-lg truncate">{{ station.name }}</div>
        <StationStateBadge :state="station.state"/>
      </div>
      <div class="flex items-center gap-2">
        <EditButton @click="$emit('edit', station.id)"/>
        <DeleteButton @click="$emit('delete', station)"/>
      </div>
    </div>
    <div class="space-y-0.5 text-sm">
      <MutedText tag="p" size="sm">{{ t('adminStations.memberCount', {count: station.memberCount}) }}</MutedText>
      <MutedText v-if="station.associationName" tag="p" size="sm">
        {{ t('adminStations.inAssociation', {name: station.associationName}) }}
      </MutedText>
      <MutedText v-if="station.managerName || station.managerEmail" tag="p" size="sm">
        {{ t('adminStations.managedBy', {name: station.managerName ?? station.managerEmail}) }}
      </MutedText>
    </div>
    <ManagerReminder :station="station" :on-reminded="onReminded"/>
  </NeutralContainer>
</template>
