/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'
import type { ExchangeRequestEntry } from '@/api/types'
import { ExchangeStatus } from '@/api/types'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { inventoryTypeBadge, inventoryTypeLabel as toInventoryTypeLabel } from '@/util/inventoryType'

const { t } = useI18n()
const router = useRouter()
const { isMobile } = useBreakpoint()

defineProps<{
  exchanges: ExchangeRequestEntry[]
}>()

function formatDate(dateStr?: string | null): string {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('de-DE')
}

function inventoryTypeLabel(type?: string | null): string {
  return toInventoryTypeLabel(t, type)
}

function exchangeStatusBadge(status: string) {
  switch (status) {
    case ExchangeStatus.DONE: return SuccessBadge
    case ExchangeStatus.ANNOUNCED: case ExchangeStatus.SHIPPED: return InfoBadge
    case ExchangeStatus.RECEIVED: case ExchangeStatus.ARRIVED: return SecondaryBadge
    default: return SecondaryBadge
  }
}
</script>

<template>
  <div class="space-y-3">
    <SubHeader>
      <font-awesome-icon :icon="['fas', 'rotate']" class="mr-2" />
      {{ t('inventory.overview.exchanges') }} ({{ exchanges.length }})
    </SubHeader>
    <div v-if="isMobile" class="space-y-2">
      <NeutralContainer v-for="ex in exchanges" :key="ex.id" class="space-y-1 cursor-pointer" @click="router.push({ name: 'inventory-exchanges' })">
        <div class="flex items-center justify-between">
          <span class="text-sm font-medium">{{ ex.inventoryName }}</span>
          <component :is="exchangeStatusBadge(ex.status)">{{ t(`exchanges.status.${ex.status}`) }}</component>
        </div>
        <div class="flex flex-wrap items-center gap-1">
          <component :is="inventoryTypeBadge(ex.inventoryType)">{{ inventoryTypeLabel(ex.inventoryType) }}</component>
          <SizeBadge>{{ ex.oldSizeLabel ?? t('common.unisize') }} &rarr; {{ ex.newSizeLabel ?? t('common.unisize') }}</SizeBadge>
        </div>
        <div class="text-xs text-(--text-muted)"><MemberName :identity="ex.memberIdentity ?? null"/></div>
        <div class="text-xs text-(--text-muted)">
          {{ t('inventory.overview.createdAt') }}: {{ formatDate(ex.createdAt) }}
          <span v-if="ex.updatedAt && ex.updatedAt !== ex.createdAt"> &middot; {{ t('inventory.overview.updatedAt') }}: {{ formatDate(ex.updatedAt) }}</span>
        </div>
      </NeutralContainer>
    </div>
    <NeutralContainer v-else class="overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
          <THead>
            <Th>{{ t('inventory.overview.colItem') }}</Th>
            <Th>{{ t('inventory.overview.colType') }}</Th>
            <Th>{{ t('inventory.overview.colOwner') }}</Th>
            <Th>{{ t('inventory.overview.colStatus') }}</Th>
            <Th>{{ t('inventory.overview.colCreated') }}</Th>
            <Th>{{ t('inventory.overview.colUpdated') }}</Th>
          </THead>
        </thead>
        <tbody>
          <TRow v-for="ex in exchanges" :key="ex.id"
              class="cursor-pointer hover:bg-(--bg-accent)"
              @click="router.push({ name: 'inventory-exchanges' })">
            <Td>
              {{ ex.inventoryName }}
              <SizeBadge>{{ ex.oldSizeLabel ?? t('common.unisize') }} &rarr; {{ ex.newSizeLabel ?? t('common.unisize') }}</SizeBadge>
            </Td>
            <Td>
              <component :is="inventoryTypeBadge(ex.inventoryType)">{{ inventoryTypeLabel(ex.inventoryType) }}</component>
            </Td>
            <Td><MemberName :identity="ex.memberIdentity ?? null"/></Td>
            <Td>
              <component :is="exchangeStatusBadge(ex.status)">{{ t(`exchanges.status.${ex.status}`) }}</component>
            </Td>
            <Td muted>{{ formatDate(ex.createdAt) }}</Td>
            <Td muted>{{ formatDate(ex.updatedAt) }}</Td>
          </TRow>
        </tbody>
      </table>
    </NeutralContainer>
  </div>
</template>
