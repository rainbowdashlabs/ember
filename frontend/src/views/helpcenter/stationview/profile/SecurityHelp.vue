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
  <HelpArticle :title="t('helpCenter.security.title')" :subtitle="t('helpCenter.security.subtitle')">
    <HelpSection :title="t('helpCenter.security.whatIs')">
      <p>{{ t('helpCenter.security.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.security.twoFactorTitle')">
      <p>{{ t('helpCenter.security.twoFactorText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.security.backupTitle')">
      <p>{{ t('helpCenter.security.backupText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.security.stepUpTitle')">
      <p>{{ t('helpCenter.security.stepUpText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.security.tip') }}</HelpTip>
  </HelpArticle>
</template>
