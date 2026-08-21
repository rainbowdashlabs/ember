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
  <HelpArticle :subtitle="t('helpCenter.clusterEvents.subtitle')" :title="t('helpCenter.clusterEvents.title')">
    <HelpSection :title="t('helpCenter.clusterEvents.whatTitle')">
      <p>{{ t('helpCenter.clusterEvents.whatText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterEvents.reachTitle')">
      <p>{{ t('helpCenter.clusterEvents.reachText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterEvents.registrationTitle')">
      <p>{{ t('helpCenter.clusterEvents.registrationText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.clusterEvents.tip') }}</HelpTip>
  </HelpArticle>
</template>
