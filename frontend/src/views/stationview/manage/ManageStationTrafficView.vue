/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import HelpCenterHint from '@/components/help/HelpCenterHint.vue'
import {traffic} from '@/api'
import type {AuthBucketName, HourlyTrafficRow} from '@/api/traffic'
import TrafficChart from '@/views/adminview/admintrafficview/TrafficChart.vue'
import TrafficTotals from '@/views/adminview/admintrafficview/TrafficTotals.vue'
import TrafficWindowSelector from '@/views/adminview/admintrafficview/TrafficWindowSelector.vue'
import {useConfigPanel} from '@/composables/useConfigPanel'

const {t} = useI18n()

const windowHours = ref(72)
const metric = ref<'ingressBytes' | 'egressBytes' | 'requests' | 'inout'>('egressBytes')
const authFilter = ref<AuthBucketName | ''>('')

const {config: rows, loading, reload: load} = useConfigPanel<HourlyTrafficRow[]>({
  initial: [],
  fetch: async () => {
    const to = new Date()
    const from = new Date(to.getTime() - windowHours.value * 3600_000)
    const res = await traffic.getStationHourly({
      from: from.toISOString(),
      to: to.toISOString(),
      auth: authFilter.value === '' ? undefined : authFilter.value,
    })
    return res.rows
  },
  formatError: () => '',
})

watch([windowHours, authFilter], load)
</script>

<template>
  <ViewContent>
    <div class="space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <PageHeader>{{ t('traffic.station.title') }}</PageHeader>
          <MutedText tag="p" size="sm">{{ t('traffic.station.subtitle') }}</MutedText>
        </div>
        <HelpCenterHint :to="{name: 'help-station-traffic'}">
          {{ t('traffic.help') }}
        </HelpCenterHint>
      </div>

      <TrafficWindowSelector
          v-model:window-hours="windowHours"
          v-model:metric="metric"
          v-model:auth-filter="authFilter"/>

      <Spinner v-if="loading"/>
      <template v-else>
        <TrafficTotals :rows="rows"/>

        <NeutralContainer class="space-y-2">
          <SectionHeader>{{ t('traffic.chart.title') }}</SectionHeader>
          <MutedText tag="p" size="sm">{{ t('traffic.chart.stationHint') }}</MutedText>
          <TrafficChart v-if="rows.length > 0" :rows="rows" :metric="metric"/>
          <MutedText v-else tag="div" size="sm">{{ t('traffic.noData') }}</MutedText>
        </NeutralContainer>
      </template>
    </div>
  </ViewContent>
</template>
