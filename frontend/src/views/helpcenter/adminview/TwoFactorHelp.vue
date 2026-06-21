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
      :title="t('helpCenter.adminTwoFactor.title')"
      :subtitle="t('helpCenter.adminTwoFactor.subtitle')">
    <HelpSection :title="t('helpCenter.adminTwoFactor.whatIs')">
      <p>{{ t('helpCenter.adminTwoFactor.whatIsText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.adminTwoFactor.reset')">
      <p>{{ t('helpCenter.adminTwoFactor.resetText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.adminTwoFactor.audit')">
      <p>{{ t('helpCenter.adminTwoFactor.auditText') }}</p>
    </HelpSection>
    <HelpTip>{{ t('helpCenter.adminTwoFactor.tip') }}</HelpTip>
  </HelpArticle>
</template>
