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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryLending.title')" :subtitle="t('helpCenter.inventoryLending.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryLending.whatIs')">
      <p>{{ t('helpCenter.inventoryLending.whatIsText') }}</p>
      <p>{{ t('helpCenter.inventoryLending.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLending.tabsTitle')">
      <p>{{ t('helpCenter.inventoryLending.tabsIncoming') }}</p>
      <p>{{ t('helpCenter.inventoryLending.tabsOutgoing') }}</p>
    </HelpSection>

    <!-- Dummy: Lending overview -->
    <NeutralContainer class="space-y-3">
      <div class="flex items-center justify-between flex-wrap gap-2">
        <SectionHeader>{{ t('helpCenter.inventoryLending.title') }}</SectionHeader>
        <PrimaryButton>
          <font-awesome-icon :icon="['fas', 'plus']" class="mr-2" />
          {{ t('helpCenter.inventoryLending.dummyNewRequest') }}
        </PrimaryButton>
      </div>
      <NeutralContainer class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <THead>
              <Th>{{ t('helpCenter.inventoryLending.colStation') }}</Th>
              <Th>{{ t('helpCenter.inventoryLending.colItem') }}</Th>
              <Th>{{ t('helpCenter.inventoryLending.colPeriod') }}</Th>
              <Th>{{ t('helpCenter.inventoryLending.colStatus') }}</Th>
            </THead>
          </thead>
          <tbody>
            <TRow>
              <Td class="font-medium">DLRG Musterstadt</Td>
              <Td>Rettungsring (3×)</Td>
              <Td muted>01.06.2026 – 15.06.2026</Td>
              <Td><InfoBadge>{{ t('helpCenter.inventoryLending.statusPending') }}</InfoBadge></Td>
            </TRow>
            <TRow>
              <Td class="font-medium">DLRG Beispielburg</Td>
              <Td>Taucheranzug (1×)</Td>
              <Td muted>10.05.2026 – 20.05.2026</Td>
              <Td><SuccessBadge>{{ t('helpCenter.inventoryLending.statusLent') }}</SuccessBadge></Td>
            </TRow>
            <TRow>
              <Td class="font-medium">DLRG Neustadt</Td>
              <Td>Erste-Hilfe-Koffer (2×)</Td>
              <Td muted>01.04.2026 – 05.04.2026</Td>
              <Td><ErrorBadge>{{ t('helpCenter.inventoryLending.statusDeclined') }}</ErrorBadge></Td>
            </TRow>
          </tbody>
        </table>
      </NeutralContainer>
    </NeutralContainer>

    <HelpTip>{{ t('helpCenter.inventoryLending.tip') }}</HelpTip>
  </HelpArticle>
</template>
