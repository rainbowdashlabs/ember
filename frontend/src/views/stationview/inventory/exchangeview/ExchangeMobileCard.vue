/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import ExchangeStatusBadge from './ExchangeStatusBadge.vue'
import ExchangeStatusUpdatePanel from './ExchangeStatusUpdatePanel.vue'
import {ExchangeStatus, type ExchangeRequestEntry, type ExchangeStatusName} from '@/api/exchanges'
import type { InventoryItem } from '@/api/inventory'
import { inventoryTypeBadge, inventoryTypeLabel as toInventoryTypeLabel } from '@/util/inventoryType'
import { formatDate } from '@/util/format'

const { t } = useI18n()

const props = defineProps<{
  request: ExchangeRequestEntry
  showMemberColumn: boolean
  canManageExchanges: boolean
  exportMode: boolean
  selected: boolean
  isUpdating: boolean
  nextStatuses: ExchangeStatusName[]
  availableItems: InventoryItem[]
}>()

const emit = defineEmits<{
  (e: 'toggle-export'): void
  (e: 'open-log'): void
  (e: 'start-update'): void
  (e: 'delete'): void
  (e: 'status-done'): void
  (e: 'status-cancel'): void
  (e: 'status-error', msg: string): void
}>()

function inventoryTypeLabel(type?: string | null): string {
  return toInventoryTypeLabel(t, type)
}
</script>

<template>
  <NeutralContainer class="space-y-2">
    <div class="flex items-center justify-between">
      <span class="text-sm font-medium">{{ request.inventoryName }}</span>
      <ExchangeStatusBadge :status="request.status" />
    </div>
    <div v-if="canManageExchanges">
      <component :is="inventoryTypeBadge(request.inventoryType)">{{ inventoryTypeLabel(request.inventoryType) }}</component>
    </div>
    <div class="space-y-1">
      <div v-if="showMemberColumn" class="text-xs text-(--text-muted)"><MemberName :identity="request.memberIdentity ?? null"/></div>
      <div class="text-xs">{{ request.oldSizeLabel ?? t('common.unisize') }} &rarr; {{ request.newSizeLabel ?? t('common.unisize') }}</div>
      <div v-if="request.reason" class="text-xs text-(--text-muted) truncate">{{ request.reason }}</div>
      <div class="text-xs text-(--text-muted)">{{ formatDate(request.createdAt) }}</div>
    </div>
    <div class="flex items-center gap-1">
      <CheckboxInput v-if="exportMode" :model-value="selected" @update:model-value="emit('toggle-export')" />
      <SecondaryButton @click="emit('open-log')">
        <font-awesome-icon :icon="['fas', 'clock-rotate-left']" />
      </SecondaryButton>
      <SecondaryButton v-if="canManageExchanges && request.status !== ExchangeStatus.DONE" @click="emit('start-update')">
        <font-awesome-icon :icon="['fas', 'arrow-right']" />
      </SecondaryButton>
      <DeleteButton v-if="canManageExchanges" @click="emit('delete')" />
    </div>
    <div v-if="isUpdating" class="pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
      <ExchangeStatusUpdatePanel
        :request="request"
        :next-statuses="nextStatuses"
        :available-items="availableItems"
        @done="emit('status-done')"
        @cancel="emit('status-cancel')"
        @error="(msg) => emit('status-error', msg)"
      />
    </div>
  </NeutralContainer>
</template>
