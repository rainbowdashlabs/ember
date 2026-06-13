/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import type { ExchangeStatusName } from '@/api/types'
import { ExchangeStatus } from '@/api/types'

const { t } = useI18n()

defineProps<{
  status: ExchangeStatusName
}>()

function statusLabel(status: ExchangeStatusName): string {
  return t(`exchanges.status.${status}`)
}
</script>

<template>
  <InfoBadge v-if="status === ExchangeStatus.ANNOUNCED">{{ statusLabel(status) }}</InfoBadge>
  <PrimaryBadge v-else-if="status === ExchangeStatus.RECEIVED || status === ExchangeStatus.ARRIVED">{{ statusLabel(status) }}</PrimaryBadge>
  <SecondaryBadge v-else-if="status === ExchangeStatus.SHIPPED">{{ statusLabel(status) }}</SecondaryBadge>
  <SuccessBadge v-else-if="status === ExchangeStatus.DONE">{{ statusLabel(status) }}</SuccessBadge>
</template>
