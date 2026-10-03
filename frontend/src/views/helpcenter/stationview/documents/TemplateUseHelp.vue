/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.documentTemplateUse.title')" :subtitle="t('helpCenter.documentTemplateUse.subtitle')">
    <HelpSection :title="t('helpCenter.documentTemplateUse.whatIs')">
      <p>{{ t('helpCenter.documentTemplateUse.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentTemplateUse.selfServiceTitle')">
      <p>{{ t('helpCenter.documentTemplateUse.selfServiceText') }}</p>
      <p>{{ t('helpCenter.documentTemplateUse.offeredText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentTemplateUse.issuerTitle')">
      <p>{{ t('helpCenter.documentTemplateUse.issuerText') }}</p>
    </HelpSection>
  </HelpArticle>
</template>
