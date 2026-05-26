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
  <HelpArticle :title="t('helpCenter.sessions.title')" :subtitle="t('helpCenter.sessions.subtitle')">
    <HelpSection :title="t('helpCenter.sessions.whatIs')">
      <p>{{ t('helpCenter.sessions.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.sessions.gdprTitle')">
      <p>{{ t('helpCenter.sessions.gdprText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.sessions.tip') }}</HelpTip>
  </HelpArticle>
</template>
