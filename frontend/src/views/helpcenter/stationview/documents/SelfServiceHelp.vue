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
  <HelpArticle :title="t('helpCenter.documentSelfService.title')" :subtitle="t('helpCenter.documentSelfService.subtitle')">
    <HelpSection :title="t('helpCenter.documentSelfService.whatIs')">
      <p>{{ t('helpCenter.documentSelfService.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentSelfService.createTitle')">
      <p>{{ t('helpCenter.documentSelfService.createText') }}</p>
      <p>{{ t('helpCenter.documentSelfService.missingText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentSelfService.childrenTitle')">
      <p>{{ t('helpCenter.documentSelfService.childrenText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.documentSelfService.tip') }}</HelpTip>
  </HelpArticle>
</template>
