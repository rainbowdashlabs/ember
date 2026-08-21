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
  <HelpArticle :subtitle="t('helpCenter.clusterKnowledge.subtitle')" :title="t('helpCenter.clusterKnowledge.title')">
    <HelpSection :title="t('helpCenter.clusterKnowledge.whatTitle')">
      <p>{{ t('helpCenter.clusterKnowledge.whatText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterKnowledge.sharingTitle')">
      <p>{{ t('helpCenter.clusterKnowledge.sharingText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterKnowledge.publicTitle')">
      <p>{{ t('helpCenter.clusterKnowledge.publicText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.clusterKnowledge.tip') }}</HelpTip>
  </HelpArticle>
</template>
