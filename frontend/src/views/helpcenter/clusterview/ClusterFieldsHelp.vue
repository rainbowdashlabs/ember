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
  <HelpArticle :subtitle="t('helpCenter.clusterFields.subtitle')" :title="t('helpCenter.clusterFields.title')">
    <HelpSection :title="t('helpCenter.clusterFields.whatTitle')">
      <p>{{ t('helpCenter.clusterFields.whatText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterFields.readonlyTitle')">
      <p>{{ t('helpCenter.clusterFields.readonlyText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterFields.limitsTitle')">
      <p>{{ t('helpCenter.clusterFields.limitsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterFields.releaseTitle')">
      <p>{{ t('helpCenter.clusterFields.releaseText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.clusterFields.tip') }}</HelpTip>
  </HelpArticle>
</template>
