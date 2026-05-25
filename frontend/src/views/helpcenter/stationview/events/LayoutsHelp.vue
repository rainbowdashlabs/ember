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
</script>

<template>
  <HelpArticle :title="t('eventLayouts.title')" :subtitle="t('helpCenter.eventLayouts.subtitle')">
    <HelpSection :title="t('helpCenter.eventLayouts.whatIs')">
      <p>{{ t('helpCenter.eventLayouts.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventLayouts.howTo')">
      <p>{{ t('helpCenter.eventLayouts.howToText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.eventLayouts.tip') }}</HelpTip>
  </HelpArticle>
</template>
