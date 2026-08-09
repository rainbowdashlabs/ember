/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import DataTable from '@/components/table/DataTable.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import TRow from '@/components/table/TRow.vue'
import type { LentOutItem } from '@/api/lending'
import { formatDate } from '@/util/format'

const props = defineProps<{
  lentOutItems: LentOutItem[]
  lentOutCount: number
}>()

const { t } = useI18n()
</script>

<template>
  <template v-if="props.lentOutItems.length > 0">
    <SubHeader>
      <font-awesome-icon :icon="['fas', 'arrow-right-arrow-left']" class="mr-2" />
      {{ t('inventory.detail.lentOut') }} ({{ props.lentOutCount }})
    </SubHeader>
    <DataTable>
      <template #head>
        <Th>{{ t('inventory.detail.lentToStation') }}</Th>
        <Th align="center">{{ t('inventory.detail.lentQuantity') }}</Th>
        <Th>{{ t('inventory.detail.lentUntil') }}</Th>
        <Th>{{ t('inventory.detail.lentStatus') }}</Th>
        <th class="px-3 py-2"></th>
      </template>
      <TRow v-for="lent in props.lentOutItems" :key="lent.requestItemId">
        <Td class="font-medium">{{ lent.requestingStationName }}</Td>
        <Td align="center">{{ lent.quantity }}</Td>
        <Td>
          <template v-if="lent.dateTo">{{ formatDate(lent.dateTo) }}</template>
          <MutedText v-else size="base">&#x2013;</MutedText>
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
    </DataTable>
  </template>
</template>
