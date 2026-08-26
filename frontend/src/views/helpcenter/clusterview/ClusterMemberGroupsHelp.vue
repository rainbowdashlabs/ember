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
  <HelpArticle :subtitle="t('helpCenter.clusterMemberGroups.subtitle')" :title="t('helpCenter.clusterMemberGroups.title')">
    <HelpSection :title="t('helpCenter.clusterMemberGroups.whatTitle')">
      <p>{{ t('helpCenter.clusterMemberGroups.whatText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterMemberGroups.howToTitle')">
      <p>{{ t('helpCenter.clusterMemberGroups.howToText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterMemberGroups.threeWaysTitle')">
      <p>{{ t('helpCenter.clusterMemberGroups.threeWaysText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.clusterMemberGroups.tip') }}</HelpTip>
  </HelpArticle>
</template>
