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
  <HelpArticle :title="t('helpCenter.stationMoved.deleteTitle')" :subtitle="t('helpCenter.stationMoved.subtitle')">
    <HelpSection :title="t('helpCenter.stationMoved.deleteTitle')">
      <p>{{ t('helpCenter.stationMoved.deleteText') }}</p>
    </HelpSection>
  </HelpArticle>
</template>
