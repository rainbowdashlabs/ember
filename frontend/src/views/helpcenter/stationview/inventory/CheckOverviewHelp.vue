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
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryChecks.title')" :subtitle="t('helpCenter.inventoryChecks.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryChecks.whatIs')">
      <p>{{ t('helpCenter.inventoryChecks.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryChecks.howToTitle')">
      <p>{{ t('helpCenter.inventoryChecks.step1') }}</p>
      <p>{{ t('helpCenter.inventoryChecks.step2') }}</p>
      <p>{{ t('helpCenter.inventoryChecks.step3') }}</p>
      <p>{{ t('helpCenter.inventoryChecks.step4') }}</p>
    </HelpSection>

    <!-- Dummy: Check overview table -->
    <NeutralContainer class="space-y-3">
      <SectionHeader>{{ t('inventory.check.title') }}</SectionHeader>

      <!-- Dummy: Tabs -->
      <div class="flex gap-2 border-b border-bg-light-accent dark:border-bg-dark-accent">
        <SelectionToggleButton :selected="true">
          {{ t('inventory.check.tabTeam') }}
        </SelectionToggleButton>
        <SelectionToggleButton :selected="false">
          {{ t('inventory.check.tabMember') }}
        </SelectionToggleButton>
      </div>

      <NeutralContainer class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <THead>
              <Th>{{ t('inventory.check.member') }}</Th>
              <Th>{{ t('inventory.check.lastChecked') }}</Th>
              <Th>{{ t('inventory.check.checkedBy') }}</Th>
              <Th>{{ t('inventory.check.status') }}</Th>
              <th class="px-3 py-2"></th>
            </THead>
          </thead>
          <tbody>
            <TRow>
              <Td class="font-medium">Max Mustermann</Td>
              <Td muted>10.05.2026, 14:30</Td>
              <Td muted>Admin User</Td>
              <Td></Td>
              <Td align="right">
                <PrimaryButton>{{ t('inventory.check.start') }}</PrimaryButton>
              </Td>
            </TRow>
            <TRow>
              <Td class="font-medium">Erika Musterfrau</Td>
              <Td muted>{{ t('inventory.check.neverChecked') }}</Td>
              <Td muted>-</Td>
              <Td><SecondaryBadge>{{ t('inventory.check.neverChecked') }}</SecondaryBadge></Td>
              <Td align="right">
                <PrimaryButton>{{ t('inventory.check.start') }}</PrimaryButton>
              </Td>
            </TRow>
            <TRow>
              <Td class="font-medium">Jan Schmidt</Td>
              <Td muted>12.05.2026, 09:00</Td>
              <Td muted>Admin User</Td>
              <Td><InfoBadge>{{ t('inventory.check.lockedByMe') }}</InfoBadge></Td>
              <Td align="right">
                <SecondaryButton>{{ t('inventory.check.continue') }}</SecondaryButton>
              </Td>
            </TRow>
            <TRow>
              <Td class="font-medium">Lisa Müller</Td>
              <Td muted>05.05.2026, 16:00</Td>
              <Td muted>Betreuer A</Td>
              <Td><ErrorBadge>{{ t('inventory.check.locked') }}: Betreuer B</ErrorBadge></Td>
              <Td align="right">
                <PrimaryButton disabled>{{ t('inventory.check.start') }}</PrimaryButton>
              </Td>
            </TRow>
          </tbody>
        </table>
      </NeutralContainer>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.inventoryChecks.resultsTitle')">
      <p>{{ t('helpCenter.inventoryChecks.resultsText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryChecks.tip') }}</HelpTip>
  </HelpArticle>
</template>
