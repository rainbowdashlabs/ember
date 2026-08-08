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

const {t} = useI18n()

const PAGES = [
  {icon: ['fas', 'list-check'], title: 'helpCenter.procedureList.title', desc: 'helpCenter.procedureList.subtitle'},
  {icon: ['fas', 'plus'], title: 'helpCenter.procedureCreate.title', desc: 'helpCenter.procedureCreate.subtitle'},
  {icon: ['fas', 'square-check'], title: 'helpCenter.procedureDetail.title', desc: 'helpCenter.procedureDetail.subtitle'},
  {icon: ['fas', 'clone'], title: 'helpCenter.procedureTemplates.title', desc: 'helpCenter.procedureTemplates.subtitle'},
  {icon: ['fas', 'pen'], title: 'helpCenter.procedureTemplateEdit.title', desc: 'helpCenter.procedureTemplateEdit.subtitle'},
] as const
</script>

<template>
  <HelpArticle :title="t('helpCenter.procedureOverview.overviewTitle')"
               :subtitle="t('helpCenter.procedureOverview.overviewSubtitle')">
    <HelpSection :title="t('helpCenter.procedureOverview.whatIs')">
      <p>{{ t('helpCenter.procedureOverview.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.procedureOverview.pagesTitle')">
      <p>{{ t('helpCenter.procedureOverview.pagesText') }}</p>
      <div class="grid gap-2 sm:grid-cols-2">
        <HelpFeatureItem v-for="page in PAGES" :key="page.title" :icon="[...page.icon]"
                         :title="t(page.title)" :description="t(page.desc)"/>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.procedureOverview.dependencyTitle')">
      <p>{{ t('helpCenter.procedureOverview.dependencyText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.procedureOverview.tip') }}</HelpTip>
  </HelpArticle>
</template>
