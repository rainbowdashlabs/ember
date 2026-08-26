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
  <HelpArticle :subtitle="t('helpCenter.clusterMovements.subtitle')" :title="t('helpCenter.clusterMovements.title')">
    <HelpSection :title="t('helpCenter.clusterMovements.whatTitle')">
      <p>{{ t('helpCenter.clusterMovements.whatText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterMovements.issueTitle')">
      <p>{{ t('helpCenter.clusterMovements.issueText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterMovements.exchangeTitle')">
      <p>{{ t('helpCenter.clusterMovements.exchangeText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.clusterMovements.tip') }}</HelpTip>
  </HelpArticle>
</template>
