/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import DataTable from '@/components/table/DataTable.vue'
import TRow from '@/components/table/TRow.vue'
import type { ProcurementEntry } from '@/api/procurement'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { inventoryTypeBadge, inventoryTypeLabel as toInventoryTypeLabel } from '@/util/inventoryType'
import { formatDate } from '@/util/format'

const routes = useInventoryRoutes()

const { t } = useI18n()
const router = useRouter()
const { isMobile } = useBreakpoint()

defineProps<{
  entries: ProcurementEntry[]
  inventoryTypeMap: Map<number, string>
}>()

function inventoryTypeLabel(type?: string | null): string {
  return toInventoryTypeLabel(t, type)
}
</script>

<template>
  <div class="space-y-3">
    <SubHeader>
      <font-awesome-icon :icon="['fas', 'folder-plus']" class="mr-2" />
      {{ t('inventory.overview.procurement') }} ({{ entries.length }})
    </SubHeader>
    <div v-if="isMobile" class="space-y-2">
      <NeutralContainer v-for="p in entries" :key="p.id" class="space-y-1 cursor-pointer" @click="router.push({ name: routes.procurement })">
        <div class="flex items-center justify-between">
          <span class="text-sm font-medium">{{ p.inventoryName }}</span>
          <SizeBadge>{{ p.sizeLabel || t('common.unisize') }}</SizeBadge>
        </div>
        <div class="flex flex-wrap items-center gap-1">
          <component :is="inventoryTypeBadge(inventoryTypeMap.get(p.inventoryId))">{{ inventoryTypeLabel(inventoryTypeMap.get(p.inventoryId)) }}</component>
        </div>
        <div class="text-xs text-(--text-muted)"><MemberName :identity="p.memberIdentity ?? null"/></div>
        <div v-if="p.notes" class="text-xs text-(--text-muted)">{{ p.notes }}</div>
        <div class="text-xs text-(--text-muted)">{{ t('inventory.overview.requestedAt') }}: {{ formatDate(p.requestedAt) }}</div>
      </NeutralContainer>
    </div>
    <DataTable v-else>
      <template #head>
        <Th>{{ t('inventory.overview.colItem') }}</Th>
        <Th>{{ t('inventory.overview.colType') }}</Th>
        <Th>{{ t('inventory.overview.colOwner') }}</Th>
        <Th>{{ t('inventory.overview.colNotes') }}</Th>
        <Th>{{ t('inventory.overview.colRequested') }}</Th>
      </template>
      <TRow v-for="p in entries" :key="p.id"
          class="cursor-pointer hover:bg-(--bg-accent)"
          @click="router.push({ name: routes.procurement })">
        <Td>
          {{ p.inventoryName }}
          <SizeBadge>{{ p.sizeLabel || t('common.unisize') }}</SizeBadge>
        </Td>
        <Td>
          <component :is="inventoryTypeBadge(inventoryTypeMap.get(p.inventoryId))">{{ inventoryTypeLabel(inventoryTypeMap.get(p.inventoryId)) }}</component>
        </Td>
        <Td><MemberName :identity="p.memberIdentity ?? null"/></Td>
        <Td muted>{{ p.notes || '-' }}</Td>
        <Td muted>{{ formatDate(p.requestedAt) }}</Td>
      </TRow>
    </DataTable>
  </div>
</template>
