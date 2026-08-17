/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import TransferSection from '@/views/stationview/manage/stationview/TransferSection.vue'
import StationImportSection from '@/views/stationview/manage/stationview/StationImportSection.vue'
import ImportProgressChecklist from '@/components/transfer/ImportProgressChecklist.vue'
import type {PitchTransfer} from './pitchTypes'

/** Moving a station between instances, drawn by the application's own transfer sections. */
defineProps<{
  transfer: PitchTransfer
}>()
</script>

<template>
  <template v-if="transfer.progress">
    <ImportProgressChecklist :progress="transfer.progress"/>
  </template>
  <template v-else>
    <TransferSection/>
    <StationImportSection/>
  </template>
</template>
