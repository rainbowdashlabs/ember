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
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryLendingBrowse.title')" :subtitle="t('helpCenter.inventoryLendingBrowse.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryLendingBrowse.whatIs')">
      <p>{{ t('helpCenter.inventoryLendingBrowse.whatIsText') }}</p>
      <p>{{ t('helpCenter.inventoryLendingBrowse.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLendingBrowse.howTo')">
      <p>{{ t('helpCenter.inventoryLendingBrowse.step1') }}</p>
      <p>{{ t('helpCenter.inventoryLendingBrowse.step2') }}</p>
      <p>{{ t('helpCenter.inventoryLendingBrowse.step3') }}</p>
    </HelpSection>

    <!-- Dummy: Browse available items -->
    <NeutralContainer class="space-y-3">
      <SectionHeader>{{ t('helpCenter.inventoryLendingBrowse.dummyTitle') }}</SectionHeader>
      <NeutralContainer class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <THead>
              <Th>{{ t('helpCenter.inventoryLendingBrowse.colStation') }}</Th>
              <Th>{{ t('helpCenter.inventoryLendingBrowse.colItem') }}</Th>
              <Th>{{ t('helpCenter.inventoryLendingBrowse.colQuantity') }}</Th>
              <Th>{{ t('helpCenter.inventoryLendingBrowse.colAvailable') }}</Th>
              <th class="px-3 py-2"></th>
            </THead>
          </thead>
          <tbody>
            <TRow>
              <Td class="font-medium">DLRG Musterstadt</Td>
              <Td>Rettungsring</Td>
              <Td muted>5</Td>
              <Td><SuccessBadge>{{ t('helpCenter.inventoryLendingBrowse.available') }}</SuccessBadge></Td>
              <Td align="right">
                <PrimaryButton :icon="['fas', 'paper-plane']">
                  {{ t('helpCenter.inventoryLendingBrowse.dummyRequest') }}
                </PrimaryButton>
              </Td>
            </TRow>
            <TRow>
              <Td class="font-medium">DLRG Musterstadt</Td>
              <Td>Taucheranzug</Td>
              <Td muted>2</Td>
              <Td><InfoBadge>{{ t('helpCenter.inventoryLendingBrowse.partiallyAvailable') }}</InfoBadge></Td>
              <Td align="right">
                <PrimaryButton :icon="['fas', 'paper-plane']">
                  {{ t('helpCenter.inventoryLendingBrowse.dummyRequest') }}
                </PrimaryButton>
              </Td>
            </TRow>
            <TRow>
              <Td class="font-medium">DLRG Beispielburg</Td>
              <Td>Erste-Hilfe-Koffer</Td>
              <Td muted>3</Td>
              <Td><SuccessBadge>{{ t('helpCenter.inventoryLendingBrowse.available') }}</SuccessBadge></Td>
              <Td align="right">
                <PrimaryButton :icon="['fas', 'paper-plane']">
                  {{ t('helpCenter.inventoryLendingBrowse.dummyRequest') }}
                </PrimaryButton>
              </Td>
            </TRow>
          </tbody>
        </table>
      </NeutralContainer>
    </NeutralContainer>

    <HelpTip>{{ t('helpCenter.inventoryLendingBrowse.tip') }}</HelpTip>
  </HelpArticle>
</template>
