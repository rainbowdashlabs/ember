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

const {t} = useI18n()

const STEPS = [
  'address',
  'modules',
  'member-types',
  'groups',
  'mail',
  'branding',
  'first-event',
  'kb-seed',
  'federation',
  'invites',
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.setup.stepsTitle')" :subtitle="t('helpCenter.setup.stepsSubtitle')">
    <HelpSection v-for="step in STEPS" :key="step" :title="t(`setup.steps.${step}.title`)">
      <p>{{ t(`setup.steps.${step}.hint`) }}</p>
      <p class="text-sm text-(--text-muted)">{{ t(`helpCenter.setup.stepNotes.${step}`) }}</p>
    </HelpSection>
    <HelpTip>{{ t('helpCenter.setup.stepsTip') }}</HelpTip>
  </HelpArticle>
</template>
