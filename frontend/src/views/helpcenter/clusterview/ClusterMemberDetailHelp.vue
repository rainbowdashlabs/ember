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
  <HelpArticle :subtitle="t('helpCenter.clusterMemberDetail.subtitle')"
               :title="t('helpCenter.clusterMemberDetail.title')">
    <HelpSection :title="t('helpCenter.clusterMemberDetail.whatTitle')">
      <p>{{ t('helpCenter.clusterMemberDetail.whatText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterMemberDetail.originTitle')">
      <p>{{ t('helpCenter.clusterMemberDetail.originText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterMemberDetail.limitsTitle')">
      <p>{{ t('helpCenter.clusterMemberDetail.limitsText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.clusterMemberDetail.tip') }}</HelpTip>
  </HelpArticle>
</template>
