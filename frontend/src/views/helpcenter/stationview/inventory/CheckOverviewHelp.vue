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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'

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
        <button class="px-4 py-2 text-sm font-medium border-b-2 border-primary text-primary">
          {{ t('inventory.check.tabTeam') }}
        </button>
        <button class="px-4 py-2 text-sm font-medium text-(--text-muted)">
          {{ t('inventory.check.tabMember') }}
        </button>
      </div>

      <NeutralContainer class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
              <th class="px-3 py-2 font-medium">{{ t('inventory.check.member') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('inventory.check.lastChecked') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('inventory.check.checkedBy') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('inventory.check.status') }}</th>
              <th class="px-3 py-2"></th>
            </tr>
          </thead>
          <tbody>
            <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
              <td class="px-3 py-2.5 font-medium">Max Mustermann</td>
              <td class="px-3 py-2.5 text-(--text-muted)">10.05.2026, 14:30</td>
              <td class="px-3 py-2.5 text-(--text-muted)">Admin User</td>
              <td class="px-3 py-2.5"></td>
              <td class="px-3 py-2.5 text-right">
                <PrimaryButton class="text-sm">{{ t('inventory.check.start') }}</PrimaryButton>
              </td>
            </tr>
            <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
              <td class="px-3 py-2.5 font-medium">Erika Musterfrau</td>
              <td class="px-3 py-2.5 text-(--text-muted)">{{ t('inventory.check.neverChecked') }}</td>
              <td class="px-3 py-2.5 text-(--text-muted)">-</td>
              <td class="px-3 py-2.5"><SecondaryBadge>{{ t('inventory.check.neverChecked') }}</SecondaryBadge></td>
              <td class="px-3 py-2.5 text-right">
                <PrimaryButton class="text-sm">{{ t('inventory.check.start') }}</PrimaryButton>
              </td>
            </tr>
            <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
              <td class="px-3 py-2.5 font-medium">Jan Schmidt</td>
              <td class="px-3 py-2.5 text-(--text-muted)">12.05.2026, 09:00</td>
              <td class="px-3 py-2.5 text-(--text-muted)">Admin User</td>
              <td class="px-3 py-2.5"><InfoBadge>{{ t('inventory.check.lockedByMe') }}</InfoBadge></td>
              <td class="px-3 py-2.5 text-right">
                <SecondaryButton class="text-sm">{{ t('inventory.check.continue') }}</SecondaryButton>
              </td>
            </tr>
            <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
              <td class="px-3 py-2.5 font-medium">Lisa Müller</td>
              <td class="px-3 py-2.5 text-(--text-muted)">05.05.2026, 16:00</td>
              <td class="px-3 py-2.5 text-(--text-muted)">Betreuer A</td>
              <td class="px-3 py-2.5"><ErrorBadge>{{ t('inventory.check.locked') }}: Betreuer B</ErrorBadge></td>
              <td class="px-3 py-2.5 text-right">
                <PrimaryButton class="text-sm" disabled>{{ t('inventory.check.start') }}</PrimaryButton>
              </td>
            </tr>
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
