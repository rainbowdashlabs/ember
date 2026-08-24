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
  <HelpArticle :subtitle="t('helpCenter.clusterStorageBackend.subtitle')"
               :title="t('helpCenter.clusterStorageBackend.title')">
    <HelpSection :title="t('helpCenter.clusterStorageBackend.reachTitle')">
      <p>{{ t('helpCenter.clusterStorageBackend.reachText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterStorageBackend.lockTitle')">
      <p>{{ t('helpCenter.clusterStorageBackend.lockText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterStorageBackend.placementsTitle')">
      <p>{{ t('helpCenter.clusterStorageBackend.placementsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterStorageBackend.credentialsTitle')">
      <p>{{ t('helpCenter.clusterStorageBackend.credentialsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterStorageBackend.joinLeaveTitle')">
      <p>{{ t('helpCenter.clusterStorageBackend.joinLeaveText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.clusterStorageBackend.tip') }}</HelpTip>
  </HelpArticle>
</template>
