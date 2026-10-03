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
  <HelpArticle :title="t('helpCenter.adminDocumentFonts.title')" :subtitle="t('helpCenter.adminDocumentFonts.subtitle')">
    <HelpSection :title="t('helpCenter.adminDocumentFonts.whatIs')">
      <p>{{ t('helpCenter.adminDocumentFonts.whatIsText') }}</p>
      <p>{{ t('helpCenter.adminDocumentFonts.reachText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminDocumentFonts.uploadTitle')">
      <p>{{ t('helpCenter.adminDocumentFonts.uploadText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminDocumentFonts.deleteTitle')">
      <p>{{ t('helpCenter.adminDocumentFonts.deleteText') }}</p>
    </HelpSection>
  </HelpArticle>
</template>
