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
  <HelpArticle :subtitle="t('helpCenter.clusterLookAndFeel.subtitle')" :title="t('helpCenter.clusterLookAndFeel.title')">
    <HelpSection :title="t('helpCenter.clusterLookAndFeel.whatTitle')">
      <p>{{ t('helpCenter.clusterLookAndFeel.whatText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterLookAndFeel.locksTitle')">
      <p>{{ t('helpCenter.clusterLookAndFeel.locksText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterLookAndFeel.membersTitle')">
      <p>{{ t('helpCenter.clusterLookAndFeel.membersText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.clusterLookAndFeel.tip') }}</HelpTip>
  </HelpArticle>
</template>
