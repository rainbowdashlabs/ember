/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminStationImport.title')"
               :subtitle="t('helpCenter.adminStationImport.subtitle')">
    <HelpSection :title="t('helpCenter.adminStationImport.whatIs')">
      <p>{{ t('helpCenter.adminStationImport.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStationImport.phasesTitle')">
      <p>{{ t('helpCenter.adminStationImport.phasesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStationImport.failureTitle')">
      <p>{{ t('helpCenter.adminStationImport.failureText') }}</p>
    </HelpSection>
  </HelpArticle>
</template>
