/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import BeaconSettingsPanel from './adminbeaconview/BeaconSettingsPanel.vue'
import BeaconFaultCard from './adminbeaconview/BeaconFaultCard.vue'
import BeaconReportCard from './adminbeaconview/BeaconReportCard.vue'
import BeaconMetricsTable from './adminbeaconview/BeaconMetricsTable.vue'
import {beacon} from '@/api'
import type {BeaconFault, BeaconMetricsRow, BeaconReport, BeaconStatus} from '@/api/beacon'

/**
 * What other instances have reported here.
 *
 * <p>Only an instance that accepts reports has any of this, so the screen says so plainly rather than
 * showing three empty lists to somebody whose instance was never meant to be a beacon.
 */
const {t} = useI18n()

const status = ref<BeaconStatus | null>(null)
const faults = ref<BeaconFault[]>([])
const reports = ref<BeaconReport[]>([])
const metrics = ref<BeaconMetricsRow[]>([])
const loading = ref(true)
const error = ref('')
const activeTab = ref('faults')
const showAcknowledged = ref(false)

const tabs = computed(() => [
  {key: 'faults', label: t('beacon.collectedFaults')},
  {key: 'reports', label: t('beacon.collectedReports')},
  {key: 'metrics', label: t('beacon.collectedMetrics')},
])

const isBeacon = computed(() => status.value?.receiving === true)

async function load() {
  loading.value = true
  error.value = ''
  try {
    status.value = await beacon.getStatus()
    if (!status.value.receiving) return
    const [f, r, m] = await Promise.all([
      beacon.listFaults(showAcknowledged.value),
      beacon.listBeaconReports(showAcknowledged.value),
      beacon.listBeaconMetrics(30),
    ])
    faults.value = f
    reports.value = r
    metrics.value = m
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function resolve(id: number, version: string | null) {
  await beacon.resolveFault(id, true, version)
  await load()
}

async function acknowledgeReport(id: number) {
  await beacon.acknowledgeBeaconReport(id)
  await load()
}

/** Switching this instance into a beacon has to fill the lists it just gained. */
function onSaved(stored: BeaconStatus) {
  status.value = stored
  load()
}
</script>

<template>
  <ViewContent :title="t('pages.admin-beacon.title')" :subtitle="t('pages.admin-beacon.subtitle')">
    <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>
    <Spinner v-if="loading"/>

    <template v-else>
      <BeaconSettingsPanel v-if="status" :status="status" class="mb-6" @saved="onSaved"/>

      <NeutralContainer v-if="!isBeacon">
        <MutedText tag="div" size="sm">{{ t('beacon.notABeacon') }}</MutedText>
      </NeutralContainer>

      <template v-else>
      <div class="flex items-center justify-between mb-4">
        <TabBar v-model="activeTab" :tabs="tabs"/>
        <SelectionToggleButton :selected="showAcknowledged" @toggle="showAcknowledged = !showAcknowledged; load()">
          {{ t('adminProblems.showAcknowledged') }}
        </SelectionToggleButton>
      </div>

      <div v-if="activeTab === 'faults'" class="space-y-2">
        <NeutralContainer v-if="faults.length === 0">
          <MutedText tag="div" size="sm">{{ t('beacon.noFaults') }}</MutedText>
        </NeutralContainer>
        <BeaconFaultCard v-for="fault in faults" :key="fault.id" :fault="fault" @resolve="resolve"/>
      </div>

      <div v-else-if="activeTab === 'reports'" class="space-y-2">
        <NeutralContainer v-if="reports.length === 0">
          <MutedText tag="div" size="sm">{{ t('beacon.noReports') }}</MutedText>
        </NeutralContainer>
        <BeaconReportCard
            v-for="report in reports"
            :key="report.id"
            :report="report"
            @acknowledge="acknowledgeReport"
        />
      </div>

        <BeaconMetricsTable v-else :rows="metrics"/>
      </template>
    </template>
  </ViewContent>
</template>
