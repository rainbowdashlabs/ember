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
  <HelpArticle :title="t('helpCenter.eventTemplates.title')" :subtitle="t('helpCenter.eventTemplates.subtitle')">
    <HelpSection :title="t('helpCenter.eventTemplates.whatIs')">
      <p>{{ t('helpCenter.eventTemplates.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventTemplates.createTitle')">
      <p>{{ t('helpCenter.eventTemplates.createText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventTemplates.applyTitle')">
      <p>{{ t('helpCenter.eventTemplates.applyText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.eventTemplates.tip') }}</HelpTip>
  </HelpArticle>
</template>
