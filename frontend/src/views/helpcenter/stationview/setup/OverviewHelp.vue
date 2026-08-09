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
import HelpFeatureItem from '@/components/helpcenter/HelpFeatureItem.vue'
import {STEP_ORDER} from '@/views/stationview/setup/steps'

const {t} = useI18n()

const STEP_ICONS: Record<string, string[]> = {
  'welcome': ['fas', 'hand'],
  'address': ['fas', 'location-dot'],
  'modules': ['fas', 'puzzle-piece'],
  'member-types': ['fas', 'shield'],
  'groups': ['fas', 'users'],
  'mail': ['fas', 'envelope'],
  'branding': ['fas', 'palette'],
  'first-event': ['fas', 'calendar-days'],
  'kb-seed': ['fas', 'book'],
  'federation': ['fas', 'arrow-right-arrow-left'],
  'invites': ['fas', 'user-plus'],
  'finish': ['fas', 'circle-check'],
}
</script>

<template>
  <HelpArticle :title="t('helpCenter.setup.overviewTitle')" :subtitle="t('helpCenter.setup.overviewSubtitle')">
    <HelpSection :title="t('helpCenter.setup.whatIs')">
      <p>{{ t('helpCenter.setup.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setup.requiredVsOptional')">
      <p>{{ t('helpCenter.setup.requiredVsOptionalText') }}</p>
      <div class="grid gap-2 sm:grid-cols-2">
        <HelpFeatureItem v-for="step in STEP_ORDER" :key="step"
                         :icon="STEP_ICONS[step] ?? ['fas', 'circle-info']"
                         :title="t(`setup.steps.${step}.title`)"
                         :description="t(`setup.steps.${step}.hint`)"/>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setup.frameTitle')">
      <p>{{ t('helpCenter.setup.frameText') }}</p>
      <p>{{ t('helpCenter.setup.frameHintNote') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setup.dashboardChecklist')">
      <p>{{ t('helpCenter.setup.dashboardChecklistText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.setup.overviewTip') }}</HelpTip>
  </HelpArticle>
</template>
