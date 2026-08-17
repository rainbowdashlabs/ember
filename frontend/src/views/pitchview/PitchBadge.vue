/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import type {Accent} from './pitchTypes'

/** The badge family of the application, picked by accent so slide data stays plain. */
defineProps<{
  tone?: Accent
}>()
</script>

<template>
  <SuccessBadge v-if="tone === 'success'"><slot/></SuccessBadge>
  <ErrorBadge v-else-if="tone === 'error'"><slot/></ErrorBadge>
  <InfoBadge v-else-if="tone === 'info'"><slot/></InfoBadge>
  <SecondaryBadge v-else-if="tone === 'secondary'"><slot/></SecondaryBadge>
  <PrimaryBadge v-else><slot/></PrimaryBadge>
</template>
