/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import CheckModePanel from '@/views/stationview/attendance/sessionview/CheckModePanel.vue'
import type {PitchCheckMode} from './pitchTypes'

/** The application's own check panel: one member at a time, three buttons, a counter. */
defineProps<{
  check: PitchCheckMode
}>()
</script>

<template>
  <CheckModePanel
      :current-entry="check.entry" :check-index="check.index" :total-unchecked="check.total"
      :member-name="check.name" :member-identity="check.identity"/>
</template>
