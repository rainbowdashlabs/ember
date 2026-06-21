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
  <HelpArticle :title="t('helpCenter.stationSecurity.title')" :subtitle="t('helpCenter.stationSecurity.subtitle')">
    <HelpSection :title="t('helpCenter.stationSecurity.whatIs')">
      <p>{{ t('helpCenter.stationSecurity.whatIsText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.stationSecurity.policiesTitle')">
      <p>{{ t('helpCenter.stationSecurity.policiesText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.stationSecurity.membersTitle')">
      <p>{{ t('helpCenter.stationSecurity.membersText') }}</p>
    </HelpSection>
    <HelpTip>{{ t('helpCenter.stationSecurity.tip') }}</HelpTip>
  </HelpArticle>
</template>
