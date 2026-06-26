/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import EndpointSummaryCards from './EndpointSummaryCards.vue'
import EndpointChartPlaceholders from './EndpointChartPlaceholders.vue'
import EndpointRecentRequestsTable from './EndpointRecentRequestsTable.vue'

const {t} = useI18n()
</script>

<template>
  <div class="flex items-center gap-3 mb-4">
    <SecondaryButton :icon="['fas', 'arrow-left']">
      {{ t('common.back') }}
    </SecondaryButton>
    <span class="text-lg font-bold">
      <span class="font-mono text-green-600">GET</span>
      <span class="font-mono ml-2">/api/v1/members</span>
    </span>
  </div>

  <EndpointSummaryCards/>
  <EndpointChartPlaceholders/>

  <NeutralContainer>
    <SectionHeader>{{ t('apiStatus.recentRequests') }}</SectionHeader>
    <EndpointRecentRequestsTable/>
  </NeutralContainer>
</template>
