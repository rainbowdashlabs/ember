/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'
import type { InventoryItem, InventorySize, StationMember } from '@/api/types'
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
    <NeutralContainer class="overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
          <THead>
            <Th>{{ t('inventory.detail.item') }}</Th>
            <Th>{{ t('inventory.detail.owner') }}</Th>
            <Th>{{ t('inventory.detail.lostSince') }}</Th>
          </THead>
        </thead>
        <tbody>
          <TRow v-for="item in props.items" :key="item.id">
            <Td>
              <div class="font-medium">
                {{ item.name }}
                <SizeBadge v-if="sizeName(item.sizeId)" lost>{{ sizeName(item.sizeId) }}</SizeBadge>
              </div>
              <div v-if="item.internalId" class="text-xs text-(--text-muted)">{{ item.internalId }}</div>
            </Td>
            <Td><MemberName :identity="getMemberIdentity(item.assignedTo)"/></Td>
            <Td>
              <ErrorBadge>{{ formatDate(item.lostAt) }}</ErrorBadge>
            </Td>
          </TRow>
        </tbody>
      </table>
    </NeutralContainer>
  </template>
</template>
