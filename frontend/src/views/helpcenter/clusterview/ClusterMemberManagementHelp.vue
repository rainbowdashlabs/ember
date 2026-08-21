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
  <HelpArticle :subtitle="t('helpCenter.clusterMemberManagement.subtitle')" :title="t('helpCenter.clusterMemberManagement.title')">
    <HelpSection :title="t('helpCenter.clusterMemberManagement.whatTitle')">
      <p>{{ t('helpCenter.clusterMemberManagement.whatText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterMemberManagement.guardrailTitle')">
      <p>{{ t('helpCenter.clusterMemberManagement.guardrailText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterMemberManagement.ceilingTitle')">
      <p>{{ t('helpCenter.clusterMemberManagement.ceilingText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.clusterMemberManagement.tip') }}</HelpTip>
  </HelpArticle>
</template>
