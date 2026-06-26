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
import ProblemsExample from './problemshelp/ProblemsExample.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminProblems.title')" :subtitle="t('helpCenter.adminProblems.subtitle')">
    <HelpSection :title="t('helpCenter.adminProblems.whatIs')">
      <p>{{ t('helpCenter.adminProblems.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminProblems.listTitle')">
      <p>{{ t('helpCenter.adminProblems.listText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminProblems.acknowledgeTitle')">
      <p>{{ t('helpCenter.adminProblems.acknowledgeText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminProblems.groupingTitle')">
      <p>{{ t('helpCenter.adminProblems.groupingText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminProblems.exampleTitle')">
      <ProblemsExample/>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminProblems.tip') }}</HelpTip>
  </HelpArticle>
</template>
