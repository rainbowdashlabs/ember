/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'
import type { LentOutItem } from '@/api/lending'

const props = defineProps<{
  lentOutItems: LentOutItem[]
  lentOutCount: number
}>()

const { t } = useI18n()

function formatDate(dateStr?: string | null): string {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('de-DE')
}
</script>

<template>
  <template v-if="props.lentOutItems.length > 0">
    <SubHeader>
      <font-awesome-icon :icon="['fas', 'arrow-right-arrow-left']" class="mr-2" />
      {{ t('inventory.detail.lentOut') }} ({{ props.lentOutCount }})
    </SubHeader>
    <NeutralContainer class="overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
          <THead>
            <Th>{{ t('inventory.detail.lentToStation') }}</Th>
            <Th align="center">{{ t('inventory.detail.lentQuantity') }}</Th>
            <Th>{{ t('inventory.detail.lentUntil') }}</Th>
            <Th>{{ t('inventory.detail.lentStatus') }}</Th>
            <th class="px-3 py-2"></th>
          </THead>
        </thead>
        <tbody>
          <TRow v-for="lent in props.lentOutItems" :key="lent.requestItemId">
            <Td class="font-medium">{{ lent.requestingStationName }}</Td>
            <Td align="center">{{ lent.quantity }}</Td>
            <Td>
              <template v-if="lent.dateTo">{{ formatDate(lent.dateTo) }}</template>
              <span v-else class="text-(--text-muted)">&#x2013;</span>
            </Td>
            <Td>
              <InfoBadge>{{ lent.status === 'LENT' ? t('inventory.detail.statusLent') : t('inventory.detail.statusApproved') }}</InfoBadge>
            </Td>
            <Td align="right">
              <router-link :to="{ name: 'inventory-lending-request', params: { id: lent.requestId } }">
                <SecondaryButton :icon="['fas', 'eye']">
                  {{ t('inventory.detail.viewRequest') }}
                </SecondaryButton>
              </router-link>
            </Td>
          </TRow>
        </tbody>
      </table>
    </NeutralContainer>
  </template>
</template>
