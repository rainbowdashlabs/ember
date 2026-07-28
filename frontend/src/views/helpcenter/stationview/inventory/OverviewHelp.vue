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
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import DataTable from '@/components/table/DataTable.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TRow from '@/components/table/TRow.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryOverview.title')" :subtitle="t('helpCenter.inventoryOverview.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryOverview.whatShown')">
      <p>{{ t('helpCenter.inventoryOverview.whatShownText') }}</p>
      <p>{{ t('helpCenter.inventoryOverview.lostItems') }}</p>
      <p>{{ t('helpCenter.inventoryOverview.openExchanges') }}</p>
      <p>{{ t('helpCenter.inventoryOverview.openProcurement') }}</p>
    </HelpSection>

    <!-- Dummy: Open exchanges table -->
    <NeutralContainer class="space-y-3">
      <SubHeader>
        <font-awesome-icon :icon="['fas', 'rotate']" class="mr-2" />
        {{ t('inventory.overview.exchanges') }} (2)
      </SubHeader>
      <DataTable>
        <template #head>
          <Th>{{ t('inventory.overview.colItem') }}</Th>
          <Th>{{ t('inventory.overview.colOwner') }}</Th>
          <Th>{{ t('inventory.overview.colStatus') }}</Th>
        </template>
        <TRow>
          <Td>Helme <MutedText size="base">[M &rarr; L]</MutedText></Td>
          <Td>Max Mustermann</Td>
          <Td><InfoBadge>{{ t('exchanges.status.ANNOUNCED') }}</InfoBadge></Td>
        </TRow>
        <TRow>
          <Td>Jacken <MutedText size="base">[S &rarr; M]</MutedText></Td>
          <Td>Erika Musterfrau</Td>
          <Td><SecondaryBadge>{{ t('exchanges.status.RECEIVED') }}</SecondaryBadge></Td>
        </TRow>
      </DataTable>
    </NeutralContainer>

    <!-- Dummy: Open procurement -->
    <NeutralContainer class="space-y-3">
      <SubHeader>
        <font-awesome-icon :icon="['fas', 'folder-plus']" class="mr-2" />
        {{ t('inventory.overview.procurement') }} (1)
      </SubHeader>
      <DataTable>
        <template #head>
          <Th>{{ t('inventory.overview.colItem') }}</Th>
          <Th>{{ t('inventory.overview.colOwner') }}</Th>
          <Th>{{ t('inventory.overview.colNotes') }}</Th>
        </template>
        <TRow>
          <Td>Stiefel <MutedText size="base">[42]</MutedText></Td>
          <Td>Max Mustermann</Td>
          <Td muted>Alter Stiefel gerissen</Td>
        </TRow>
      </DataTable>
    </NeutralContainer>

    <!-- Dummy: Lost items -->
    <NeutralContainer class="space-y-3">
      <SubHeader>{{ t('inventory.overview.lost') }}</SubHeader>
      <DataTable>
        <template #head>
          <Th>{{ t('inventory.overview.colItem') }}</Th>
          <Th>{{ t('inventory.overview.colOwner') }}</Th>
          <Th>{{ t('inventory.overview.colLostSince') }}</Th>
        </template>
        <TRow>
          <Td>
            <div class="font-medium">Helm <MutedText size="base" class="font-normal">[M]</MutedText></div>
            <MutedText tag="div">INV-0042</MutedText>
          </Td>
          <Td>Erika Musterfrau</Td>
          <Td><ErrorBadge>01.05.2026</ErrorBadge></Td>
        </TRow>
      </DataTable>
    </NeutralContainer>

    <HelpTip>{{ t('helpCenter.inventoryOverview.tip') }}</HelpTip>
  </HelpArticle>
</template>
