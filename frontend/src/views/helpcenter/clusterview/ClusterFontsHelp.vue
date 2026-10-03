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
  <HelpArticle :title="t('helpCenter.clusterDocumentFonts.title')" :subtitle="t('helpCenter.clusterDocumentFonts.subtitle')">
    <HelpSection :title="t('helpCenter.clusterDocumentFonts.whatIs')">
      <p>{{ t('helpCenter.clusterDocumentFonts.whatIsText') }}</p>
      <p>{{ t('helpCenter.clusterDocumentFonts.reachText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterDocumentFonts.uploadTitle')">
      <p>{{ t('helpCenter.clusterDocumentFonts.uploadText') }}</p>
      <p>{{ t('helpCenter.clusterDocumentFonts.roomText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterDocumentFonts.deleteTitle')">
      <p>{{ t('helpCenter.clusterDocumentFonts.deleteText') }}</p>
    </HelpSection>
  </HelpArticle>
</template>
