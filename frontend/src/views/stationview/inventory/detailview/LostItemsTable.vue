/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import DataTable from '@/components/table/DataTable.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import TRow from '@/components/table/TRow.vue'
import type { InventoryItem, InventorySize } from '@/api/inventory'
import type { StationMember } from '@/api/types'
import { formatDate } from '@/util/format'

const props = defineProps<{
  items: InventoryItem[]
  sizes: InventorySize[]
  memberMap: Map<number, StationMember>
}>()

const { t } = useI18n()


function getMemberIdentity(memberId: number | null | undefined) {
  if (!memberId) return undefined
  const m = props.memberMap.get(memberId)
  return m?.identity
}

function sizeName(sizeId: number | null | undefined): string {
  if (!sizeId) return ''
  return props.sizes.find(s => s.id === sizeId)?.label ?? ''
}

</script>

<template>
  <template v-if="props.items.length > 0">
    <SubHeader>{{ t('inventory.detail.lostItems') }}</SubHeader>
    <DataTable>
      <template #head>
        <Th>{{ t('inventory.detail.item') }}</Th>
        <Th>{{ t('inventory.detail.owner') }}</Th>
        <Th>{{ t('inventory.detail.lostSince') }}</Th>
      </template>
      <TRow v-for="item in props.items" :key="item.id">
        <Td>
          <div class="font-medium">
            {{ item.name }}
            <SizeBadge v-if="sizeName(item.sizeId)" lost>{{ sizeName(item.sizeId) }}</SizeBadge>
          </div>
          <MutedText v-if="item.internalId" tag="div">{{ item.internalId }}</MutedText>
        </Td>
        <Td><MemberName :identity="getMemberIdentity(item.assignedTo)"/></Td>
        <Td>
          <ErrorBadge>{{ formatDate(item.lostAt) }}</ErrorBadge>
        </Td>
      </TRow>
    </DataTable>
  </template>
</template>
