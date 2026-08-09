/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import type { ProcurementEntry } from '@/api/procurement'
import { formatDate } from '@/util/format'

const { t } = useI18n()

defineProps<{
  entry: ProcurementEntry
  canManageProcurement: boolean
}>()

defineEmits<{
  (e: 'fulfill', id: number): void
  (e: 'delete', id: number): void
}>()

</script>

<template>
  <NeutralContainer>
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
      <div class="space-y-1">
        <div class="flex items-center gap-2 flex-wrap">
          <span class="font-medium">{{ entry.inventoryName }}</span>
          <SizeBadge>{{ entry.sizeLabel || t('common.unisize') }}</SizeBadge>
          <SuccessBadge v-if="entry.fulfilledAt">{{ t('procurement.fulfilled') }}</SuccessBadge>
          <ErrorBadge v-else>{{ t('procurement.open') }}</ErrorBadge>
        </div>
        <div class="text-sm text-(--text-muted)">
          <MemberName :identity="entry.memberIdentity ?? null"/> &mdash; {{ formatDate(entry.requestedAt) }}
        </div>
        <div v-if="entry.fulfilledAt" class="text-xs text-(--text-muted)">
          {{ t('procurement.fulfilledAt') }}: {{ formatDate(entry.fulfilledAt) }}
        </div>
        <div v-if="entry.notes" class="text-sm">{{ entry.notes }}</div>
      </div>

      <div v-if="canManageProcurement" class="flex items-center gap-2 shrink-0">
        <PrimaryButton :icon="['fas', 'check']" v-if="!entry.fulfilledAt" @click="$emit('fulfill', entry.id)">
          {{ t('procurement.markFulfilled') }}
        </PrimaryButton>
        <DeleteButton @click="$emit('delete', entry.id)" />
      </div>
    </div>
  </NeutralContainer>
</template>
