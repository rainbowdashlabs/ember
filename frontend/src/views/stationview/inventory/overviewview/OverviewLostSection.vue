/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import DataTable from '@/components/table/DataTable.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import TRow from '@/components/table/TRow.vue'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { inventoryTypeBadge, inventoryTypeLabel as toInventoryTypeLabel } from '@/util/inventoryType'
import type { LostItem } from './types'
import { formatDate } from '@/util/format'

const { t } = useI18n()
const { isMobile } = useBreakpoint()

defineProps<{
  items: LostItem[]
}>()

function inventoryTypeLabel(type?: string | null): string {
  return toInventoryTypeLabel(t, type)
}
</script>

<template>
  <div class="space-y-3">
    <SubHeader>{{ t('inventory.overview.lost') }}</SubHeader>
    <div v-if="isMobile" class="space-y-2">
      <NeutralContainer v-for="a in items" :key="a.item.id" class="space-y-1">
        <div class="flex items-center justify-between">
          <span class="text-sm font-medium">{{ a.item.name }}</span>
          <ErrorBadge>{{ formatDate(a.item.lostAt) }}</ErrorBadge>
        </div>
        <div class="flex items-center gap-2">
          <component :is="inventoryTypeBadge(a.inventoryType)">{{ inventoryTypeLabel(a.inventoryType) }}</component>
          <SizeBadge lost>{{ a.sizeName || t('common.unisize') }}</SizeBadge>
          <MutedText v-if="a.item.internalId">{{ a.item.internalId }}</MutedText>
        </div>
        <MutedText tag="div"><MemberName :identity="a.ownerIdentity"/></MutedText>
      </NeutralContainer>
    </div>
    <DataTable v-else>
      <template #head>
        <Th>{{ t('inventory.overview.colItem') }}</Th>
        <Th>{{ t('inventory.overview.colType') }}</Th>
        <Th>{{ t('inventory.overview.colOwner') }}</Th>
        <Th>{{ t('inventory.overview.colLostSince') }}</Th>
      </template>
      <TRow v-for="a in items" :key="a.item.id">
        <Td>
          <div class="font-medium">
            {{ a.item.name }}
            <SizeBadge lost>{{ a.sizeName || t('common.unisize') }}</SizeBadge>
          </div>
          <MutedText v-if="a.item.internalId" tag="div">{{ a.item.internalId }}</MutedText>
        </Td>
        <Td>
          <component :is="inventoryTypeBadge(a.inventoryType)">{{ inventoryTypeLabel(a.inventoryType) }}</component>
        </Td>
        <Td><MemberName :identity="a.ownerIdentity"/></Td>
        <Td>
          <ErrorBadge>{{ formatDate(a.item.lostAt) }}</ErrorBadge>
        </Td>
      </TRow>
    </DataTable>
  </div>
</template>
