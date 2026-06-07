/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.protocolEvaluation.title')" :subtitle="t('helpCenter.protocolEvaluation.subtitle')">
    <HelpSection :title="t('helpCenter.protocolEvaluation.whatIs')">
      <p>{{ t('helpCenter.protocolEvaluation.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocolEvaluation.summaryTitle')">
      <p>{{ t('helpCenter.protocolEvaluation.summaryText') }}</p>
    </HelpSection>

    <!-- Dummy: Header with export buttons -->
    <HelpSection :title="t('helpCenter.protocolEvaluation.exportTitle')">
      <p>{{ t('helpCenter.protocolEvaluation.exportText') }}</p>
      <div class="flex items-center gap-2 mb-4">
        <SecondaryButton disabled>
          <font-awesome-icon :icon="['fas', 'chevron-left']" />
        </SecondaryButton>
        <SectionHeader>{{ t('protocol.evaluation') }}</SectionHeader>
        <div class="flex gap-2 ml-auto">
          <PrimaryButton disabled>
            <font-awesome-icon :icon="['fas', 'file-pdf']" class="mr-1" /> {{ t('protocol.exportTable') }}
          </PrimaryButton>
          <SecondaryButton disabled>
            <font-awesome-icon :icon="['fas', 'download']" class="mr-1" /> {{ t('protocol.exportAll') }}
          </SecondaryButton>
        </div>
      </div>
    </HelpSection>

    <!-- Dummy: Multi-member comparison table -->
    <HelpSection :title="t('helpCenter.protocolEvaluation.tableTitle')">
      <p>{{ t('helpCenter.protocolEvaluation.tableText') }}</p>
      <NeutralContainer>
        <MutedText tag="p" size="sm" class="mb-3">
          Jugendflamme Stufe 1 — 12.04.2025
          — {{ t('protocol.threshold') }}: 25P
        </MutedText>
        <div class="overflow-x-auto">
          <table class="text-xs w-full" style="border-collapse: separate; border-spacing: 0">
            <thead>
              <tr>
                <th class="bg-[var(--bg-accent)] border border-[var(--border)] px-2 py-1.5 text-left min-w-32">{{ t('protocol.topic') }}</th>
                <th class="bg-[var(--bg-accent)] border border-[var(--border)] px-2 py-1.5 text-center min-w-12">{{ t('protocol.maxPts') }}</th>
                <th class="bg-[var(--bg-accent)] border border-[var(--border)] px-2 py-1.5 text-center min-w-12">{{ t('protocol.avg') }}</th>
                <th class="bg-[var(--bg-accent)] border border-[var(--border)] px-2 py-1.5 text-center min-w-20">
                  <div class="font-medium mb-1">Lena Mueller</div>
                  <PrimaryButton class="!text-xs !py-0.5 !px-2" disabled>
                    <font-awesome-icon :icon="['fas', 'file-pdf']" class="mr-1" /> PDF
                  </PrimaryButton>
                </th>
                <th class="bg-[var(--bg-accent)] border border-[var(--border)] px-2 py-1.5 text-center min-w-20">
                  <div class="font-medium mb-1">Max Maier</div>
                  <PrimaryButton class="!text-xs !py-0.5 !px-2" disabled>
                    <font-awesome-icon :icon="['fas', 'file-pdf']" class="mr-1" /> PDF
                  </PrimaryButton>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr class="font-semibold">
                <td class="border border-[var(--border)] px-2 py-1">Notruf</td>
                <td class="border border-[var(--border)] px-2 py-1 text-center font-mono">8</td>
                <td class="border border-[var(--border)] px-2 py-1 text-center font-mono bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300">7.5</td>
                <td class="border border-[var(--border)] px-2 py-1 text-center font-mono bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300">8</td>
                <td class="border border-[var(--border)] px-2 py-1 text-center font-mono bg-yellow-100 dark:bg-yellow-900/30 text-yellow-800 dark:text-yellow-300">7</td>
              </tr>
              <tr class="font-semibold">
                <td class="border border-[var(--border)] px-2 py-1">Knoten</td>
                <td class="border border-[var(--border)] px-2 py-1 text-center font-mono">11</td>
                <td class="border border-[var(--border)] px-2 py-1 text-center font-mono bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300">9.5</td>
                <td class="border border-[var(--border)] px-2 py-1 text-center font-mono bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300">10</td>
                <td class="border border-[var(--border)] px-2 py-1 text-center font-mono bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300">9</td>
              </tr>
              <tr class="font-semibold">
                <td class="border border-[var(--border)] px-2 py-1">Schlauche</td>
                <td class="border border-[var(--border)] px-2 py-1 text-center font-mono">15</td>
                <td class="border border-[var(--border)] px-2 py-1 text-center font-mono bg-yellow-100 dark:bg-yellow-900/30 text-yellow-800 dark:text-yellow-300">10.5</td>
                <td class="border border-[var(--border)] px-2 py-1 text-center font-mono bg-yellow-100 dark:bg-yellow-900/30 text-yellow-800 dark:text-yellow-300">12</td>
                <td class="border border-[var(--border)] px-2 py-1 text-center font-mono bg-orange-100 dark:bg-orange-900/30 text-orange-800 dark:text-orange-300">9</td>
              </tr>
              <tr class="font-bold text-sm">
                <td class="bg-[var(--bg-accent)] border border-[var(--border)] px-2 py-1.5">{{ t('protocol.total') }}</td>
                <td class="bg-[var(--bg-accent)] border border-[var(--border)] px-2 py-1.5 text-center font-mono">34</td>
                <td class="border border-[var(--border)] px-2 py-1.5 text-center font-mono bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300">27.5</td>
                <td class="border border-[var(--border)] px-2 py-1.5 text-center font-mono bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300">30</td>
                <td class="border border-[var(--border)] px-2 py-1.5 text-center font-mono bg-yellow-100 dark:bg-yellow-900/30 text-yellow-800 dark:text-yellow-300">25</td>
              </tr>
            </tbody>
          </table>
        </div>
      </NeutralContainer>
    </HelpSection>

    <!-- Color coding -->
    <HelpSection :title="t('helpCenter.protocolEvaluation.colorsTitle')">
      <p>{{ t('helpCenter.protocolEvaluation.colorsText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.protocolEvaluation.tip') }}</HelpTip>
  </HelpArticle>
</template>
