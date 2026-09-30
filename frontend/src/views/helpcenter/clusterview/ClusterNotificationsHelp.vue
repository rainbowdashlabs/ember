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
import ClusterMailSwitch from '@/views/clusterview/clusternotificationsview/ClusterMailSwitch.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :subtitle="t('helpCenter.clusterNotifications.subtitle')" :title="t('helpCenter.clusterNotifications.title')">
    <HelpSection :title="t('helpCenter.clusterNotifications.whatIs')">
      <p>{{ t('helpCenter.clusterNotifications.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterNotifications.howTo')">
      <p>{{ t('helpCenter.clusterNotifications.howToText') }}</p>
      <ClusterMailSwitch :settings="{emailEnabled: true, mailAvailable: true}"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterNotifications.whenTitle')">
      <p>{{ t('helpCenter.clusterNotifications.whenText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.clusterNotifications.tip') }}</HelpTip>
  </HelpArticle>
</template>
