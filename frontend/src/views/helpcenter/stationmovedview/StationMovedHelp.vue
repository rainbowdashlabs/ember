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
  <HelpArticle :title="t('helpCenter.stationMoved.title')" :subtitle="t('helpCenter.stationMoved.subtitle')">
    <HelpSection :title="t('helpCenter.stationMoved.whatIs')">
      <p>{{ t('helpCenter.stationMoved.whatIsText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.stationMoved.whereToGo')">
      <p>{{ t('helpCenter.stationMoved.whereToGoText') }}</p>
    </HelpSection>
  </HelpArticle>
</template>
