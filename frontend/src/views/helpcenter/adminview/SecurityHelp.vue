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
  <HelpArticle :title="t('helpCenter.adminSecurity.title')" :subtitle="t('helpCenter.adminSecurity.subtitle')">
    <HelpSection :title="t('helpCenter.adminSecurity.whatIs')">
      <p>{{ t('helpCenter.adminSecurity.whatIsText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.adminSecurity.policiesTitle')">
      <p>{{ t('helpCenter.adminSecurity.policiesText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.adminSecurity.auditTitle')">
      <p>{{ t('helpCenter.adminSecurity.auditText') }}</p>
    </HelpSection>
    <HelpTip>{{ t('helpCenter.adminSecurity.tip') }}</HelpTip>
  </HelpArticle>
</template>
