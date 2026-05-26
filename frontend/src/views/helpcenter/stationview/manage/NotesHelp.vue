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
  <HelpArticle :title="t('helpCenter.notes.title')" :subtitle="t('helpCenter.notes.subtitle')">
    <HelpSection :title="t('helpCenter.notes.whatIs')">
      <p>{{ t('helpCenter.notes.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.notes.versioningTitle')">
      <p>{{ t('helpCenter.notes.versioningText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.notes.whereTitle')">
      <p>{{ t('helpCenter.notes.whereText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.notes.tip') }}</HelpTip>
  </HelpArticle>
</template>
