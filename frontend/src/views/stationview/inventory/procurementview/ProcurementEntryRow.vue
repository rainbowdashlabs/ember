/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import type { ProcurementResponse } from '@/api/generated/schema'
import { formatDate } from '@/util/format'

const { t } = useI18n()

defineProps<{
  entry: ProcurementResponse
  canManageProcurement: boolean
}>()

defineEmits<{
  fulfill: [id: number]
  fromStock: [entry: ProcurementResponse]
  delete: [id: number]
}>()

</script>

<template>
  <NeutralContainer data-testid="procurement-entry">
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
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
      </div>

      <ButtonRow v-if="canManageProcurement" class="shrink-0">
        <SecondaryButton
            v-if="!entry.fulfilledAt && entry.stock.length > 0"
            :icon="['fas', 'box']"
            data-testid="procurement-from-stock"
            @click="$emit('fromStock', entry)"
        >
          {{ t('procurement.fromStock') }}
        </SecondaryButton>
        <PrimaryButton
            v-if="!entry.fulfilledAt"
            :icon="['fas', 'check']"
            data-testid="procurement-fulfill"
            @click="$emit('fulfill', entry.id)"
        >
          {{ t('procurement.markFulfilled') }}
        </PrimaryButton>
        <DeleteButton @click="$emit('delete', entry.id)" />
      </ButtonRow>
    </div>
  </NeutralContainer>
</template>
