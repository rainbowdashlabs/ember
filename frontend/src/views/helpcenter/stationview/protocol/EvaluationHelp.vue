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
import ComparisonTable from './evaluationhelp/ComparisonTable.vue'

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
          Jugendflamme Stufe 1 - 12.04.2025
          - {{ t('protocol.threshold') }}: 25P
        </MutedText>
        <ComparisonTable />
      </NeutralContainer>
    </HelpSection>

    <!-- Color coding -->
    <HelpSection :title="t('helpCenter.protocolEvaluation.colorsTitle')">
      <p>{{ t('helpCenter.protocolEvaluation.colorsText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.protocolEvaluation.tip') }}</HelpTip>
  </HelpArticle>
</template>
