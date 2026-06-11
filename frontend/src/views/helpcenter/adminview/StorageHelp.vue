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
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import StatValue from '@/components/typography/StatValue.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminStorage.title')" :subtitle="t('helpCenter.adminStorage.subtitle')">
    <HelpSection :title="t('helpCenter.adminStorage.overview')">
      <p>{{ t('helpCenter.adminStorage.overviewText') }}</p>
      <div class="grid grid-cols-2 gap-3 mt-3">
        <NeutralContainer class="text-center">
          <StatValue>1.2 GiB</StatValue>
          <p class="text-sm text-(--text-muted)">{{ t('storageMonitoring.totalUsage') }}</p>
        </NeutralContainer>
        <NeutralContainer class="text-center">
          <StatValue>3</StatValue>
          <p class="text-sm text-(--text-muted)">{{ t('storageMonitoring.totalStations') }}</p>
        </NeutralContainer>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStorage.presetsTitle')">
      <p>{{ t('helpCenter.adminStorage.presetsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStorage.stationTableTitle')">
      <p>{{ t('helpCenter.adminStorage.stationTableText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminStorage.tip') }}</HelpTip>
  </HelpArticle>
</template>
