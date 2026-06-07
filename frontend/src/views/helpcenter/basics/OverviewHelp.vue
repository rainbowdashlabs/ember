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
import BulletList from '@/components/typography/BulletList.vue'

const {t} = useI18n()

const features = [
  {icon: ['fas', 'users'], key: 'members'},
  {icon: ['fas', 'clipboard-user'], key: 'attendance'},
  {icon: ['fas', 'boxes-stacked'], key: 'inventory'},
  {icon: ['fas', 'calendar-days'], key: 'events'},
  {icon: ['fas', 'newspaper'], key: 'news'},
  {icon: ['fas', 'square-poll-vertical'], key: 'forms'},
  {icon: ['fas', 'box-open'], key: 'lostAndFound'},
  {icon: ['fas', 'circle-question'], key: 'quiz'},
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.basics.overview.title')" :subtitle="t('helpCenter.basics.overview.subtitle')">
    <HelpSection :title="t('helpCenter.basics.overview.whatIs')">
      <p>{{ t('helpCenter.basics.overview.whatIsText') }}</p>
      <p>{{ t('helpCenter.basics.overview.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.overview.whoFor')">
      <p>{{ t('helpCenter.basics.overview.whoForText') }}</p>
      <BulletList>
        <li>{{ t('helpCenter.basics.overview.whoFor1') }}</li>
        <li>{{ t('helpCenter.basics.overview.whoFor2') }}</li>
        <li>{{ t('helpCenter.basics.overview.whoFor3') }}</li>
        <li>{{ t('helpCenter.basics.overview.whoFor4') }}</li>
      </BulletList>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.overview.features')">
      <p>{{ t('helpCenter.basics.overview.featuresText') }}</p>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-2 mt-3">
        <NeutralContainer v-for="f in features" :key="f.key" class="flex items-start gap-3 p-3">
          <font-awesome-icon :icon="f.icon" class="h-4 w-4 text-primary mt-0.5 shrink-0"/>
          <div>
            <p class="font-semibold text-sm">{{ t(`helpCenter.basics.overview.feature.${f.key}.title`) }}</p>
            <p class="text-xs text-(--text-muted)">{{ t(`helpCenter.basics.overview.feature.${f.key}.desc`) }}</p>
          </div>
        </NeutralContainer>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.overview.concepts')">
      <p>{{ t('helpCenter.basics.overview.conceptsText') }}</p>
      <BulletList>
        <li><strong>{{ t('helpCenter.basics.overview.conceptStation') }}:</strong> {{ t('helpCenter.basics.overview.conceptStationText') }}</li>
        <li><strong>{{ t('helpCenter.basics.overview.conceptAccount') }}:</strong> {{ t('helpCenter.basics.overview.conceptAccountText') }}</li>
        <li><strong>{{ t('helpCenter.basics.overview.conceptMember') }}:</strong> {{ t('helpCenter.basics.overview.conceptMemberText') }}</li>
        <li><strong>{{ t('helpCenter.basics.overview.conceptGroup') }}:</strong> {{ t('helpCenter.basics.overview.conceptGroupText') }}</li>
      </BulletList>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.overview.privacy')">
      <p>{{ t('helpCenter.basics.overview.privacyText') }}</p>
      <BulletList>
        <li>{{ t('helpCenter.basics.overview.privacy1') }}</li>
        <li>{{ t('helpCenter.basics.overview.privacy2') }}</li>
        <li>{{ t('helpCenter.basics.overview.privacy3') }}</li>
      </BulletList>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.basics.overview.tip') }}</HelpTip>
  </HelpArticle>
</template>
