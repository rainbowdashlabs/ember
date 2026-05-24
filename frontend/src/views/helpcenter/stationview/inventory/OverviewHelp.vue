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
import THead from '@/components/table/THead.vue'
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
      <NeutralContainer class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <THead>
              <Th>{{ t('inventory.overview.colItem') }}</Th>
              <Th>{{ t('inventory.overview.colOwner') }}</Th>
              <Th>{{ t('inventory.overview.colStatus') }}</Th>
            </THead>
          </thead>
          <tbody>
            <TRow>
              <Td>Helme <span class="text-(--text-muted)">[M &rarr; L]</span></Td>
              <Td>Max Mustermann</Td>
              <Td><InfoBadge>{{ t('exchanges.status.ANNOUNCED') }}</InfoBadge></Td>
            </TRow>
            <TRow>
              <Td>Jacken <span class="text-(--text-muted)">[S &rarr; M]</span></Td>
              <Td>Erika Musterfrau</Td>
              <Td><SecondaryBadge>{{ t('exchanges.status.RECEIVED') }}</SecondaryBadge></Td>
            </TRow>
          </tbody>
        </table>
      </NeutralContainer>
    </NeutralContainer>

    <!-- Dummy: Open procurement -->
    <NeutralContainer class="space-y-3">
      <SubHeader>
        <font-awesome-icon :icon="['fas', 'folder-plus']" class="mr-2" />
        {{ t('inventory.overview.procurement') }} (1)
      </SubHeader>
      <NeutralContainer class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <THead>
              <Th>{{ t('inventory.overview.colItem') }}</Th>
              <Th>{{ t('inventory.overview.colOwner') }}</Th>
              <Th>{{ t('inventory.overview.colNotes') }}</Th>
            </THead>
          </thead>
          <tbody>
            <TRow>
              <Td>Stiefel <span class="text-(--text-muted)">[42]</span></Td>
              <Td>Max Mustermann</Td>
              <Td muted>Alter Stiefel gerissen</Td>
            </TRow>
          </tbody>
        </table>
      </NeutralContainer>
    </NeutralContainer>

    <!-- Dummy: Lost items -->
    <NeutralContainer class="space-y-3">
      <SubHeader>{{ t('inventory.overview.lost') }}</SubHeader>
      <NeutralContainer class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <THead>
              <Th>{{ t('inventory.overview.colItem') }}</Th>
              <Th>{{ t('inventory.overview.colOwner') }}</Th>
              <Th>{{ t('inventory.overview.colLostSince') }}</Th>
            </THead>
          </thead>
          <tbody>
            <TRow>
              <Td>
                <div class="font-medium">Helm <span class="font-normal text-(--text-muted)">[M]</span></div>
                <div class="text-xs text-(--text-muted)">INV-0042</div>
              </Td>
              <Td>Erika Musterfrau</Td>
              <Td><ErrorBadge>01.05.2026</ErrorBadge></Td>
            </TRow>
          </tbody>
        </table>
      </NeutralContainer>
    </NeutralContainer>

    <HelpTip>{{ t('helpCenter.inventoryOverview.tip') }}</HelpTip>
  </HelpArticle>
</template>
