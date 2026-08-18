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
  <HelpArticle :title="t('helpCenter.adminLog.title')" :subtitle="t('helpCenter.adminLog.subtitle')">
    <HelpSection :title="t('helpCenter.adminLog.whatIs')">
      <p>{{ t('helpCenter.adminLog.whatIsText') }}</p>
      <p>{{ t('helpCenter.adminLog.consoleText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminLog.switchOnTitle')">
      <p>{{ t('helpCenter.adminLog.switchOnText') }}</p>
      <p>{{ t('helpCenter.adminLog.retentionText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminLog.searchTitle')">
      <p>{{ t('helpCenter.adminLog.searchText') }}</p>
      <p>{{ t('helpCenter.adminLog.levelsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminLog.gapsTitle')">
      <p>{{ t('helpCenter.adminLog.gapsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminLog.privacyTitle')">
      <p>{{ t('helpCenter.adminLog.privacyText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminLog.tip') }}</HelpTip>
  </HelpArticle>
</template>
