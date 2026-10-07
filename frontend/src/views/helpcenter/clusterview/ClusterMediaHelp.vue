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
  <HelpArticle :title="t('helpCenter.clusterMedia.title')" :subtitle="t('helpCenter.clusterMedia.subtitle')">
    <HelpSection :title="t('helpCenter.clusterMedia.whatIs')">
      <p>{{ t('helpCenter.clusterMedia.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterMedia.uploadTitle')">
      <p>{{ t('helpCenter.clusterMedia.uploadText') }}</p>
      <p>{{ t('helpCenter.clusterMedia.keptText') }}</p>
    </HelpSection>
  </HelpArticle>
</template>
