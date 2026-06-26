/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import DummyHeader from '@/views/helpcenter/stationview/forms/analyticshelp/DummyHeader.vue'
import DummyChartsTab from '@/views/helpcenter/stationview/forms/analyticshelp/DummyChartsTab.vue'
import DummyIndividualTab from '@/views/helpcenter/stationview/forms/analyticshelp/DummyIndividualTab.vue'

const {t} = useI18n()

const activeTab = ref('charts')
const tabs = [
  {key: 'charts', label: t('forms.analytics.tabCharts')},
  {key: 'individual', label: t('forms.analytics.tabIndividual')},
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.formsAnalytics.title')" :subtitle="t('helpCenter.formsAnalytics.subtitle')">
    <HelpSection :title="t('helpCenter.formsAnalytics.whatIs')">
      <p>{{ t('helpCenter.formsAnalytics.whatIsText') }}</p>
    </HelpSection>

    <DummyHeader/>

    <TabBar v-model="activeTab" :tabs="tabs"/>

    <div class="mt-4">
      <DummyChartsTab v-if="activeTab === 'charts'"/>
      <DummyIndividualTab v-if="activeTab === 'individual'"/>
    </div>

    <HelpSection :title="t('helpCenter.formsAnalytics.chartsTitle')">
      <p>{{ t('helpCenter.formsAnalytics.chartsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.formsAnalytics.responsesTitle')">
      <p>{{ t('helpCenter.formsAnalytics.responsesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.formsAnalytics.exportTitle')">
      <p>{{ t('helpCenter.formsAnalytics.exportText') }}</p>
      <p>{{ t('helpCenter.formsAnalytics.exportFieldsText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.formsAnalytics.tip') }}</HelpTip>
  </HelpArticle>
</template>
