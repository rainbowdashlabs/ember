/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'

const props = defineProps<{
  usesOwnBackend: boolean
  quotaUsedPercent: number
}>()

const {t} = useI18n()

const status = computed(() => {
  if (props.quotaUsedPercent >= 95) return 'full'
  if (props.quotaUsedPercent >= 80) return 'warning'
  return 'ok'
})
</script>

<template>
  <span v-if="usesOwnBackend" class="text-xs text-(--text-muted)">—</span>
  <SuccessBadge v-else-if="status === 'ok'">OK</SuccessBadge>
  <InfoBadge v-else-if="status === 'warning'">{{ t('storageMonitoring.warning') }}</InfoBadge>
  <ErrorBadge v-else>{{ t('storageMonitoring.full') }}</ErrorBadge>
</template>
