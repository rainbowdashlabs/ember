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
  <HelpArticle :subtitle="t('helpCenter.clusterApplications.subtitle')"
               :title="t('helpCenter.clusterApplications.title')">
    <HelpSection :title="t('helpCenter.clusterApplications.whoTitle')">
      <p>{{ t('helpCenter.clusterApplications.whoText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterApplications.decideTitle')">
      <p>{{ t('helpCenter.clusterApplications.decideText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterApplications.afterTitle')">
      <p>{{ t('helpCenter.clusterApplications.afterText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.clusterApplications.tip') }}</HelpTip>
  </HelpArticle>
</template>
