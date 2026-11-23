/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import type { ProcurementResponse } from '@/api/generated/schema'
import { formatDate } from '@/util/format'
import { useInventoryRoutes } from '@/composables/useInventoryRoutes'

const { t } = useI18n()
const routes = useInventoryRoutes()

/**
 * What an order is: the gear, its size and state, who it is for and when it was asked for. An order a
 * swap is waiting for leads to that swap, since delivering it names the swap's replacement rather than
 * starting a hand-out of its own.
 */
defineProps<{
  entry: ProcurementResponse
}>()
</script>

<template>
  <div class="space-y-1">
    <div class="flex items-center gap-2 flex-wrap">
      <span class="font-medium">{{ entry.inventoryName }}</span>
      <SizeBadge>{{ entry.sizeLabel || t('common.unisize') }}</SizeBadge>
      <SuccessBadge v-if="entry.fulfilledAt">{{ t('procurement.fulfilled') }}</SuccessBadge>
      <ErrorBadge v-else>{{ t('procurement.open') }}</ErrorBadge>
      <InfoBadge v-if="entry.stock.length > 0" data-testid="procurement-in-stock">
        {{ t('procurement.inStock', {n: entry.stock.length}) }}
      </InfoBadge>
    </div>
    <div class="text-sm text-(--text-muted) flex items-center gap-1">
      <template v-if="entry.memberIdentity">
        <MemberName :identity="entry.memberIdentity"/>
        <span>,</span>
      </template>
      <span>{{ formatDate(entry.requestedAt) }}</span>
    </div>
    <div v-if="entry.fulfilledAt" class="text-xs text-(--text-muted)">
      {{ t('procurement.fulfilledAt') }}: {{ formatDate(entry.fulfilledAt) }}
    </div>
    <div v-if="entry.notes" class="text-sm">{{ entry.notes }}</div>
    <NuxtLink
        v-if="entry.movementId != null"
        :to="{name: routes.movement, params: {id: entry.movementId}}"
        class="text-sm underline"
        data-testid="procurement-for-swap"
    >
      {{ t('procurement.forSwap') }}
    </NuxtLink>
  </div>
</template>
