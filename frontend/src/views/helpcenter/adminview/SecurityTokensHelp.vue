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
  <HelpArticle
      :title="t('helpCenter.adminSecurityTokens.title')"
      :subtitle="t('helpCenter.adminSecurityTokens.subtitle')">
    <HelpSection :title="t('helpCenter.adminSecurityTokens.whatIs')">
      <p>{{ t('helpCenter.adminSecurityTokens.whatIsText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.adminSecurityTokens.fields')">
      <p>{{ t('helpCenter.adminSecurityTokens.fieldsText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.adminSecurityTokens.pepper')">
      <p>{{ t('helpCenter.adminSecurityTokens.pepperText') }}</p>
    </HelpSection>
    <HelpTip>{{ t('helpCenter.adminSecurityTokens.tip') }}</HelpTip>
  </HelpArticle>
</template>
