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
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

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

    <!-- Dummy: Analytics header -->
    <div class="flex items-center justify-between mb-4">
      <div>
        <SectionHeader class="text-xl font-semibold">Zufriedenheitsumfrage</SectionHeader>
        <p class="text-(--text-muted) text-sm">{{ t('forms.analytics.totalResponses') }}: 12</p>
      </div>
      <div class="flex gap-2">
        <SecondaryButton :icon="['fas', 'file-export']" disabled>
          {{ t('forms.analytics.export') }}
        </SecondaryButton>
        <SecondaryButton disabled>{{ t('common.back') }}</SecondaryButton>
      </div>
    </div>

    <TabBar v-model="activeTab" :tabs="tabs"/>

    <div class="mt-4">
      <!-- Charts tab -->
      <div v-if="activeTab === 'charts'" class="space-y-4">
        <NeutralContainer>
          <div class="space-y-3">
            <SubHeader class="font-medium">Was hat dir zuletzt besonders gut gefallen?</SubHeader>
            <p class="text-xs text-(--text-muted)">12 {{ t('forms.responses') }}</p>
            <!-- Placeholder pie chart -->
            <div class="flex items-center justify-center gap-6 py-4">
              <div class="w-24 h-24 rounded-full border-8 border-primary/40 border-t-primary border-r-secondary"></div>
              <div class="space-y-1 text-sm">
                <div class="flex items-center gap-2"><span class="w-3 h-3 rounded-full bg-primary inline-block"></span> Übungen (7)</div>
                <div class="flex items-center gap-2"><span class="w-3 h-3 rounded-full bg-secondary inline-block"></span> Gemeinschaft (3)</div>
                <div class="flex items-center gap-2"><span class="w-3 h-3 rounded-full bg-info inline-block"></span> Ausflüge (2)</div>
              </div>
            </div>
          </div>
        </NeutralContainer>

        <NeutralContainer>
          <div class="space-y-3">
            <SubHeader class="font-medium">Wie zufrieden bist du insgesamt?</SubHeader>
            <p class="text-xs text-(--text-muted)">12 {{ t('forms.responses') }}</p>
            <!-- Placeholder bar chart -->
            <div class="flex items-end gap-3 h-24 px-2">
              <div class="flex flex-col items-center gap-1 flex-1">
                <div class="w-full bg-primary rounded-t" style="height: 15%"></div>
                <span class="text-xs text-(--text-muted)">1</span>
              </div>
              <div class="flex flex-col items-center gap-1 flex-1">
                <div class="w-full bg-primary rounded-t" style="height: 10%"></div>
                <span class="text-xs text-(--text-muted)">2</span>
              </div>
              <div class="flex flex-col items-center gap-1 flex-1">
                <div class="w-full bg-primary rounded-t" style="height: 30%"></div>
                <span class="text-xs text-(--text-muted)">3</span>
              </div>
              <div class="flex flex-col items-center gap-1 flex-1">
                <div class="w-full bg-primary rounded-t" style="height: 55%"></div>
                <span class="text-xs text-(--text-muted)">4</span>
              </div>
              <div class="flex flex-col items-center gap-1 flex-1">
                <div class="w-full bg-primary rounded-t" style="height: 80%"></div>
                <span class="text-xs text-(--text-muted)">5</span>
              </div>
            </div>
          </div>
        </NeutralContainer>
      </div>

      <!-- Individual responses tab -->
      <div v-if="activeTab === 'individual'" class="space-y-4">
        <div class="flex items-center justify-between">
          <span class="text-sm text-(--text-muted)">
            {{ t('forms.analytics.responseOf', {current: 1, total: 12}) }}
          </span>
          <div class="flex items-center gap-2">
            <IconButton :icon="['fas', 'chevron-left']" label="Zurück" disabled/>
            <IconButton :icon="['fas', 'chevron-right']" label="Weiter" disabled/>
          </div>
        </div>

        <NeutralContainer>
          <div class="space-y-1 mb-4">
            <p class="font-medium"><MemberName :identity="{ stationUid: '', memberUid: '', name: 'Max Mustermann' }"/></p>
            <p class="text-xs text-(--text-muted)">15.05.2026, 14:32</p>
          </div>
          <div class="space-y-4">
            <div class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 pb-3">
              <p class="text-xs text-(--text-muted) mb-1">Was hat dir zuletzt besonders gut gefallen?</p>
              <p class="text-sm font-medium">Übungen</p>
            </div>
            <div class="pb-3">
              <p class="text-xs text-(--text-muted) mb-1">Wie zufrieden bist du insgesamt?</p>
              <p class="text-sm font-medium">4</p>
            </div>
          </div>
        </NeutralContainer>
      </div>
    </div>

    <HelpSection :title="t('helpCenter.formsAnalytics.chartsTitle')">
      <p>{{ t('helpCenter.formsAnalytics.chartsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.formsAnalytics.responsesTitle')">
      <p>{{ t('helpCenter.formsAnalytics.responsesText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.formsAnalytics.tip') }}</HelpTip>
  </HelpArticle>
</template>
