/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import {StationState} from '@/api/generated/schema'
import {STATE_LABEL_KEYS} from './stationList'

/** How far a station has come: set up, being set up, waiting for its manager, or without one. */
defineProps<{
  state: StationState
}>()

const {t} = useI18n()
</script>

<template>
  <SuccessBadge v-if="state === StationState.SET_UP">{{ t(STATE_LABEL_KEYS[state]) }}</SuccessBadge>
  <InfoBadge v-else-if="state === StationState.SETTING_UP">{{ t(STATE_LABEL_KEYS[state]) }}</InfoBadge>
  <PrimaryBadge v-else-if="state === StationState.WAITING_FOR_MANAGER">{{ t(STATE_LABEL_KEYS[state]) }}</PrimaryBadge>
  <ErrorBadge v-else>{{ t(STATE_LABEL_KEYS[state]) }}</ErrorBadge>
</template>
