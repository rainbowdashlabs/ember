/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DataTable from '@/components/table/DataTable.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ExchangeRowDummy from './ExchangeRowDummy.vue'

defineProps<{
  managerView: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex items-center justify-between flex-wrap gap-2">
      <SectionHeader>{{ t('exchanges.title') }}</SectionHeader>
      <PrimaryButton :icon="['fas', 'plus']">{{ t('exchanges.create') }}</PrimaryButton>
    </div>
    <DataTable>
      <template #head>
        <Th v-if="managerView">{{ t('exchanges.colMember') }}</Th>
        <Th>{{ t('exchanges.colInventory') }}</Th>
        <Th>{{ t('exchanges.colType') }}</Th>
        <Th>{{ t('exchanges.colOldSize') }}</Th>
        <Th>{{ t('exchanges.colNewSize') }}</Th>
        <Th>{{ t('exchanges.colStatus') }}</Th>
        <Th>{{ t('exchanges.colReason') }}</Th>
        <Th>{{ t('exchanges.colDate') }}</Th>
        <th class="px-3 py-2"></th>
      </template>
      <ExchangeRowDummy :manager-view="managerView" member="Max Mustermann" inventory="Helme" show-forward>
        <Td><InfoBadge>{{ t('inventory.manage.type.INTERNAL') }}</InfoBadge></Td>
        <Td>M</Td>
        <Td>L</Td>
        <Td><InfoBadge>{{ t('exchanges.status.ANNOUNCED') }}</InfoBadge></Td>
        <Td muted>Helm passt nicht mehr</Td>
        <Td muted>14.05.2026</Td>
      </ExchangeRowDummy>
      <ExchangeRowDummy :manager-view="managerView" member="Erika Musterfrau" inventory="Jacken" show-forward>
        <Td><SecondaryBadge>{{ t('inventory.manage.type.EXTERNAL') }}</SecondaryBadge></Td>
        <Td>S</Td>
        <Td>M</Td>
        <Td><PrimaryBadge>{{ t('exchanges.status.RECEIVED') }}</PrimaryBadge></Td>
        <Td muted>Neue Jacke benötigt</Td>
        <Td muted>10.05.2026</Td>
      </ExchangeRowDummy>
      <ExchangeRowDummy :manager-view="managerView" member="Jan Schmidt" inventory="Stiefel">
        <Td><InfoBadge>{{ t('inventory.manage.type.INTERNAL') }}</InfoBadge></Td>
        <Td>42</Td>
        <Td>44</Td>
        <Td><SuccessBadge>{{ t('exchanges.status.DONE') }}</SuccessBadge></Td>
        <Td muted>Gewachsen</Td>
        <Td muted>01.04.2026</Td>
      </ExchangeRowDummy>
    </DataTable>
  </NeutralContainer>
</template>
