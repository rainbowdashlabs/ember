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

    <HelpSection :title="t('helpCenter.adminProblems.resolveTitle')">
      <p>{{ t('helpCenter.adminProblems.resolveText') }}</p>
      <p>{{ t('helpCenter.adminProblems.deleteText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminProblems.countTitle')">
      <p>{{ t('helpCenter.adminProblems.countText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminProblems.refusalsTitle')">
      <p>{{ t('helpCenter.adminProblems.refusalsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminProblems.groupingTitle')">
      <p>{{ t('helpCenter.adminProblems.groupingText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminProblems.noiseTitle')">
      <p>{{ t('helpCenter.adminProblems.noiseText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminProblems.contextTitle')">
      <p>{{ t('helpCenter.adminProblems.contextText') }}</p>
      <p>{{ t('helpCenter.adminProblems.contextSource') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminProblems.beaconTitle')">
      <p>{{ t('helpCenter.adminProblems.beaconText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminProblems.exampleTitle')">
      <ProblemsExample/>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminProblems.tip') }}</HelpTip>
  </HelpArticle>
</template>
