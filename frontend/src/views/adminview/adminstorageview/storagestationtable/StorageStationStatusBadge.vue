/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import {StorageStatus, type StorageStatusName} from './storageStatus'

/** How full a station's room is, as a badge in the colour of the state, or a dash where it has none. */
defineProps<{
  status: StorageStatusName | null
  label: string
}>()
</script>

<template>
  <span v-if="status === null" class="text-xs text-(--text-muted)">-</span>
  <SuccessBadge v-else-if="status === StorageStatus.OK">{{ label }}</SuccessBadge>
  <InfoBadge v-else-if="status === StorageStatus.WARNING">{{ label }}</InfoBadge>
  <ErrorBadge v-else>{{ label }}</ErrorBadge>
</template>
