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
      :title="t('helpCenter.adminSecurityHibp.title')"
      :subtitle="t('helpCenter.adminSecurityHibp.subtitle')">
    <HelpSection :title="t('helpCenter.adminSecurityHibp.whatIs')">
      <p>{{ t('helpCenter.adminSecurityHibp.whatIsText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.adminSecurityHibp.fields')">
      <p>{{ t('helpCenter.adminSecurityHibp.fieldsText') }}</p>
    </HelpSection>
    <HelpTip>{{ t('helpCenter.adminSecurityHibp.tip') }}</HelpTip>
  </HelpArticle>
</template>
