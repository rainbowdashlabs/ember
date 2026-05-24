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
  <HelpArticle :title="t('helpCenter.discovery.title')" :subtitle="t('helpCenter.discovery.subtitle')">
    <HelpSection :title="t('helpCenter.discovery.whatIs')">
      <p>{{ t('helpCenter.discovery.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.discovery.connectTitle')">
      <p>{{ t('helpCenter.discovery.connectText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.discovery.settingsTitle')">
      <p>{{ t('helpCenter.discovery.settingsText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.discovery.tip') }}</HelpTip>
  </HelpArticle>
</template>
