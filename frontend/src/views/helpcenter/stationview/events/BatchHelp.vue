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
import StepProgressBar from '@/components/display/StepProgressBar.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.batchCreate.title')" :subtitle="t('helpCenter.batchCreate.subtitle')">
    <HelpSection :title="t('helpCenter.batchCreate.whatIs')">
      <p>{{ t('helpCenter.batchCreate.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.batchCreate.steps')">
      <p>{{ t('helpCenter.batchCreate.stepsText') }}</p>
    </HelpSection>

    <!-- Dummy: Step progress bar -->
    <HelpSection :title="t('batchCreate.title')">
      <SectionHeader>{{ t('batchCreate.title') }}</SectionHeader>
      <StepProgressBar
          class="mb-4"
          :steps="[
            { label: t('batchCreate.stepLayout') },
            { label: t('batchCreate.stepSchedule'), disabled: true },
            { label: t('batchCreate.stepTable'), disabled: true },
          ]"
          :current-step="0"
      />

      <div class="space-y-3">
        <NeutralContainer class="space-y-2">
          <span class="font-medium text-primary">{{ t('helpCenter.batchCreate.step1Title') }}</span>
          <p class="text-sm">{{ t('helpCenter.batchCreate.step1Text') }}</p>
        </NeutralContainer>
        <NeutralContainer class="space-y-2">
          <span class="font-medium text-primary">{{ t('helpCenter.batchCreate.step2Title') }}</span>
          <p class="text-sm">{{ t('helpCenter.batchCreate.step2Text') }}</p>
        </NeutralContainer>
        <NeutralContainer class="space-y-2">
          <span class="font-medium text-primary">{{ t('helpCenter.batchCreate.step3Title') }}</span>
          <p class="text-sm">{{ t('helpCenter.batchCreate.step3Text') }}</p>
        </NeutralContainer>
      </div>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.batchCreate.tip') }}</HelpTip>
  </HelpArticle>
</template>
