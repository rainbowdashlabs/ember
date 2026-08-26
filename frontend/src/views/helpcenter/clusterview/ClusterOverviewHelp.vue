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
  <HelpArticle :subtitle="t('helpCenter.clusterOverview.subtitle')" :title="t('helpCenter.clusterOverview.title')">
    <HelpSection :title="t('helpCenter.clusterOverview.whatTitle')">
      <p>{{ t('helpCenter.clusterOverview.whatText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterOverview.membershipTitle')">
      <p>{{ t('helpCenter.clusterOverview.membershipText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterOverview.switchTitle')">
      <p>{{ t('helpCenter.clusterOverview.switchText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.clusterOverview.tip') }}</HelpTip>
  </HelpArticle>
</template>
