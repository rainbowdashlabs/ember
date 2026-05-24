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
  <HelpArticle :title="t('batchCreate.title')" :subtitle="t('helpCenter.batchCreate.subtitle')">
    <HelpSection :title="t('helpCenter.batchCreate.whatIs')">
      <p>{{ t('helpCenter.batchCreate.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.batchCreate.steps')">
      <p>{{ t('helpCenter.batchCreate.stepsText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.batchCreate.tip') }}</HelpTip>
  </HelpArticle>
</template>
