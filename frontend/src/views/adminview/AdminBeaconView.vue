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
import MutedText from '@/components/typography/MutedText.vue'
import BeaconSettingsPanel from './adminbeaconview/BeaconSettingsPanel.vue'
import BeaconFaultList from './adminbeaconview/BeaconFaultList.vue'
import BeaconReportList from './adminbeaconview/BeaconReportList.vue'
import BeaconMetricsTable from './adminbeaconview/BeaconMetricsTable.vue'
import {beacon} from '@/api'
import {useMonitoringCounts} from '@/composables/useMonitoringCounts'
import type {BeaconFault, BeaconMetricsRow, BeaconReport, BeaconStatus} from '@/api/beacon'

/**
 * What other instances have reported here, which is this instance's own error log and its own
 * problem reports over again, gathered from somewhere else.
 *
 * <p>Only an instance that accepts reports has any of this, so the screen says so plainly rather than
 * showing three empty lists to somebody whose instance was never meant to be a beacon.
 *
 * <p>Which page this is comes from the address, because the sidebar is the navigation now. Setting the
 * beacon up is one of the pages rather than a panel repeated above each of the others.
 */
const props = withDefaults(
    defineProps<{section?: 'settings' | 'faults' | 'reports' | 'metrics'}>(),
    {section: 'settings'},
)

const {t} = useI18n()
const {refresh: refreshCounts} = useMonitoringCounts()

const pageKey = computed(() => ({
  settings: 'admin-beacon',
  faults: 'admin-beacon-faults',
  reports: 'admin-beacon-reports',
  metrics: 'admin-beacon-figures',
}[props.section]))

const status = ref<BeaconStatus | null>(null)
const faults = ref<BeaconFault[]>([])
const reports = ref<BeaconReport[]>([])
const metrics = ref<BeaconMetricsRow[]>([])
const loading = ref(true)
const error = ref('')
const showAcknowledged = ref(false)

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

function toggleAcknowledged() {
  showAcknowledged.value = !showAcknowledged.value
  load()
}

async function resolve(id: number, version: string | null) {
  await beacon.resolveFault(id, true, version)
  await load()
}

async function acknowledgeReport(id: number) {
  await beacon.acknowledgeBeaconReport(id)
  await load()
}

/**
 * Takes the saved settings, fills the lists the instance has just gained, and tells the navigation.
 *
 * <p>Whether the instance gathers anything is what decides which entries the sidebar carries, and it is
 * decided right here. Without saying so, somebody who switches it on sits in front of a menu that still
 * says otherwise until they reload the page.
 */
function onSaved(stored: BeaconStatus) {
  status.value = stored
  load()
  void refreshCounts()
}
</script>

<template>
  <ViewContent :subtitle="t(`pages.${pageKey}.subtitle`)" :title="t(`pages.${pageKey}.title`)">
    <Alert v-if="error" class="mb-4" variant="error">{{ error }}</Alert>
    <Spinner v-if="loading"/>

    <template v-else>
      <BeaconSettingsPanel v-if="props.section === 'settings' && status" :status="status" @saved="onSaved"/>

      <NeutralContainer v-else-if="!isBeacon">
        <MutedText size="sm" tag="div">{{ t('beacon.notABeacon') }}</MutedText>
      </NeutralContainer>

      <BeaconFaultList
          v-else-if="section === 'faults'"
          :faults="faults"
          :show-acknowledged="showAcknowledged"
          @resolve="resolve"
          @toggle-acknowledged="toggleAcknowledged"
      />

      <BeaconReportList
          v-else-if="section === 'reports'"
          :reports="reports"
          :show-acknowledged="showAcknowledged"
          @acknowledge="acknowledgeReport"
          @toggle-acknowledged="toggleAcknowledged"
      />

      <BeaconMetricsTable v-else :rows="metrics"/>
    </template>
  </ViewContent>
</template>
