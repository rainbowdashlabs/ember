/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import DataTable from '@/components/table/DataTable.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TRow from '@/components/table/TRow.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import StatsRow from './detailhelp/StatsRow.vue'
import SizeDistributionTable from './detailhelp/SizeDistributionTable.vue'
import AllItemsTable from './detailhelp/AllItemsTable.vue'
import FreeItemsGrid from './detailhelp/FreeItemsGrid.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryDetail.title')" :subtitle="t('helpCenter.inventoryDetail.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryDetail.whatShown')">
      <p>{{ t('helpCenter.inventoryDetail.whatShownText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryDetail.headerTitle')">
      <p>{{ t('helpCenter.inventoryDetail.headerText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryDetail.statsTitle')">
      <p>{{ t('helpCenter.inventoryDetail.statsText') }}</p>
      <StatsRow/>
      <SubHeader>{{ t('inventory.detail.bySize') }}</SubHeader>
      <SizeDistributionTable/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryDetail.itemsTitle')">
      <p>{{ t('helpCenter.inventoryDetail.itemsText') }}</p>
      <AllItemsTable/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryDetail.procurementTitle')">
      <p>{{ t('helpCenter.inventoryDetail.procurementText') }}</p>
      <PrimaryButton :icon="['fas', 'folder-plus']">
        {{ t('helpCenter.inventoryDetail.dummyCreateProcurement') }}
      </PrimaryButton>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryDetail.lostTitle')">
      <p>{{ t('helpCenter.inventoryDetail.lostText') }}</p>
      <DataTable>
        <template #head>
          <Th>{{ t('inventory.detail.item') }}</Th>
          <Th>{{ t('inventory.detail.owner') }}</Th>
          <Th>{{ t('inventory.detail.lostSince') }}</Th>
        </template>
        <TRow>
          <Td>
            <div class="font-medium">Helm <SizeBadge>S</SizeBadge></div>
            <MutedText tag="div">INV-0003</MutedText>
          </Td>
          <Td>Erika Musterfrau</Td>
          <Td><ErrorBadge>01.05.2026</ErrorBadge></Td>
        </TRow>
      </DataTable>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryDetail.freeTitle')">
      <p>{{ t('helpCenter.inventoryDetail.freeText') }}</p>
      <FreeItemsGrid/>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryDetail.tip') }}</HelpTip>
  </HelpArticle>
</template>
