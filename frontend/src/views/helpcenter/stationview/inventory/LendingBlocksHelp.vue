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
import DeleteButton from '@/components/button/DeleteButton.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryLendingBlocks.title')" :subtitle="t('helpCenter.inventoryLendingBlocks.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryLendingBlocks.whatIs')">
      <p>{{ t('helpCenter.inventoryLendingBlocks.whatIsText') }}</p>
      <p>{{ t('helpCenter.inventoryLendingBlocks.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLendingBlocks.createTitle')">
      <p>{{ t('helpCenter.inventoryLendingBlocks.createStep1') }}</p>
      <p>{{ t('helpCenter.inventoryLendingBlocks.createStep2') }}</p>
      <p>{{ t('helpCenter.inventoryLendingBlocks.createStep3') }}</p>
    </HelpSection>

    <!-- Dummy: Lending blocks list -->
    <NeutralContainer class="space-y-3">
      <div class="flex items-center justify-between flex-wrap gap-2">
        <SectionHeader>{{ t('helpCenter.inventoryLendingBlocks.dummyTitle') }}</SectionHeader>
        <PrimaryButton :icon="['fas', 'plus']">
          {{ t('helpCenter.inventoryLendingBlocks.dummyAddBlock') }}
        </PrimaryButton>
      </div>
      <NeutralContainer class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <THead>
              <Th>{{ t('helpCenter.inventoryLendingBlocks.colName') }}</Th>
              <Th>{{ t('helpCenter.inventoryLendingBlocks.colFrom') }}</Th>
              <Th>{{ t('helpCenter.inventoryLendingBlocks.colTo') }}</Th>
              <th class="px-3 py-2"></th>
            </THead>
          </thead>
          <tbody>
            <TRow>
              <Td class="font-medium">Stadtfest 2026</Td>
              <Td muted>20.06.2026</Td>
              <Td muted>22.06.2026</Td>
              <Td align="right"><DeleteButton /></Td>
            </TRow>
            <TRow>
              <Td class="font-medium">Jahreshauptübung</Td>
              <Td muted>10.09.2026</Td>
              <Td muted>11.09.2026</Td>
              <Td align="right"><DeleteButton /></Td>
            </TRow>
            <TRow>
              <Td class="font-medium">Wachenwettbewerb</Td>
              <Td muted>05.08.2026</Td>
              <Td muted>07.08.2026</Td>
              <Td align="right"><DeleteButton /></Td>
            </TRow>
          </tbody>
        </table>
      </NeutralContainer>
    </NeutralContainer>

    <HelpTip>{{ t('helpCenter.inventoryLendingBlocks.tip') }}</HelpTip>
  </HelpArticle>
</template>
