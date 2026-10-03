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
  <HelpArticle :title="t('helpCenter.documentFonts.title')" :subtitle="t('helpCenter.documentFonts.subtitle')">
    <HelpSection :title="t('helpCenter.documentFonts.whatIs')">
      <p>{{ t('helpCenter.documentFonts.whatIsText') }}</p>
      <p>{{ t('helpCenter.documentFonts.defaultText') }}</p>
      <p>{{ t('helpCenter.documentFonts.reachText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentFonts.uploadTitle')">
      <p>{{ t('helpCenter.documentFonts.uploadText') }}</p>
      <p>{{ t('helpCenter.documentFonts.licenceText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentFonts.useTitle')">
      <p>{{ t('helpCenter.documentFonts.useText') }}</p>
      <p>{{ t('helpCenter.documentFonts.previewText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentFonts.deleteTitle')">
      <p>{{ t('helpCenter.documentFonts.deleteText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.documentFonts.tip') }}</HelpTip>
  </HelpArticle>
</template>
