/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import ReportCard from './adminproblemreportsview/ReportCard.vue'
import type {ProblemReport} from '@/api/problemReports'
import {listReports, acknowledgeReport, acknowledgeAllReports, deleteReport} from '@/api/problemReports'
import {useConfigPanel} from '@/composables/useConfigPanel'

const {t} = useI18n()

const includeAcknowledged = ref(false)
const expandedId = ref<number | null>(null)

const {config: reports, loading, reload: loadData} = useConfigPanel<ProblemReport[]>({
  initial: [],
  fetch: () => listReports(includeAcknowledged.value),
  formatError: () => '',
})

async function ack(id: number) {
  await acknowledgeReport(id)
  reports.value = reports.value.map(r => r.id === id ? {...r, acknowledged: true} : r)
  if (!includeAcknowledged.value) {
    reports.value = reports.value.filter(r => r.id !== id)
  }
}

async function ackAll() {
  await acknowledgeAllReports()
  if (!includeAcknowledged.value) {
    reports.value = []
  } else {
    reports.value = reports.value.map(r => ({...r, acknowledged: true}))
  }
}

async function remove(id: number) {
  await deleteReport(id)
  reports.value = reports.value.filter(r => r.id !== id)
}

function toggle(id: number) {
  expandedId.value = expandedId.value === id ? null : id
}
</script>

<template>
  <ViewContent>
    <div class="space-y-4">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-3">
          <label class="flex items-center gap-2 text-sm">
            <ToggleInput v-model="includeAcknowledged" @update:model-value="loadData"/>
            {{ t('problemReport.showAcknowledged') }}
          </label>
          <SecondaryButton v-if="reports.some(r => !r.acknowledged)" :icon="['fas', 'check-double']" @click="ackAll">
            {{ t('problemReport.acknowledgeAll') }}
          </SecondaryButton>
        </div>
      </div>

      <Spinner v-if="loading" size="lg"/>
      <EmptyState v-if="!loading && reports.length === 0">{{ t('problemReport.empty') }}</EmptyState>

      <ReportCard
        v-for="r in reports"
        :key="r.id"
        :report="r"
        :expanded="expandedId === r.id"
        @toggle="toggle"
        @ack="ack"
        @remove="remove"
      />
    </div>
  </ViewContent>
</template>
