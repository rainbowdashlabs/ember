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
  <HelpArticle :subtitle="t('helpCenter.clusterMovementDetail.subtitle')"
               :title="t('helpCenter.clusterMovementDetail.title')">
    <HelpSection :title="t('helpCenter.clusterMovementDetail.whatTitle')">
      <p>{{ t('helpCenter.clusterMovementDetail.whatText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterMovementDetail.stepsTitle')">
      <p>{{ t('helpCenter.clusterMovementDetail.stepsText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.clusterMovementDetail.tip') }}</HelpTip>
  </HelpArticle>
</template>
