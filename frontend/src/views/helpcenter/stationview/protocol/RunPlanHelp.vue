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
import RunPlanExampleSection from './runplanhelp/RunPlanExampleSection.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.protocolRunPlan.title')" :subtitle="t('helpCenter.protocolRunPlan.subtitle')">
    <HelpSection :title="t('helpCenter.protocolRunPlan.whatIs')">
      <p>{{ t('helpCenter.protocolRunPlan.whatIsText') }}</p>
      <p>{{ t('helpCenter.protocolRunPlan.whenText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocolRunPlan.howTo')">
      <p>{{ t('helpCenter.protocolRunPlan.step1') }}</p>
      <p>{{ t('helpCenter.protocolRunPlan.step2') }}</p>
      <p>{{ t('helpCenter.protocolRunPlan.step3') }}</p>
    </HelpSection>

    <RunPlanExampleSection />

    <HelpSection :title="t('helpCenter.protocolRunPlan.inheritTitle')">
      <p>{{ t('helpCenter.protocolRunPlan.inheritText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocolRunPlan.copyTitle')">
      <p>{{ t('helpCenter.protocolRunPlan.copyText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocolRunPlan.unassignedTitle')">
      <p>{{ t('helpCenter.protocolRunPlan.unassignedText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocolRunPlan.whoGradesTitle')">
      <p>{{ t('helpCenter.protocolRunPlan.whoGradesText') }}</p>
      <p>{{ t('helpCenter.protocolRunPlan.skipText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.protocolRunPlan.tip') }}</HelpTip>
  </HelpArticle>
</template>
