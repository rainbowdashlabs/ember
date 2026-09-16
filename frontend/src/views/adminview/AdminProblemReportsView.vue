/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import Alert from '@/components/feedback/Alert.vue'
import BeaconPreviewModal from '@/components/log/BeaconPreviewModal.vue'
import ReportCard from './adminproblemreportsview/ReportCard.vue'
import {beacon} from '@/api'
import {acknowledgeAllReports, acknowledgeReport, deleteReport, listReports, type ProblemReport} from '@/api/problemReports'
import {useConfigPanel} from '@/composables/useConfigPanel'

const {t} = useI18n()

const includeAcknowledged = ref(false)
const expandedId = ref<number | null>(null)

/**
 * Passing a report on is offered where this instance reports anywhere at all.
 *
 * <p>Asked of the instance rather than assumed, because the button leads to a dialog that shows what
 * would leave and then sends it, and neither is worth offering where nothing can go anywhere.
 */
const reportsToABeacon = ref(false)
const showPreview = ref(false)
const forwardId = ref<number | null>(null)
const forwarded = ref('')

function forward(id: number) {
  forwardId.value = id
  forwarded.value = ''
  showPreview.value = true
}

onMounted(async () => {
  try {
    reportsToABeacon.value = (await beacon.getStatus()).enabled
  } catch {
    reportsToABeacon.value = false
  }
})

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
  <ViewContent :title="t('pages.admin-problem-reports.title')" :subtitle="t('pages.admin-problem-reports.subtitle')">
    <div class="space-y-4">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-3">
          <label class="flex items-center gap-2 text-sm">
            <ToggleInput v-model="includeAcknowledged" :aria-label="t('problemReport.showAcknowledged')"
                         @update:model-value="loadData"/>
            {{ t('problemReport.showAcknowledged') }}
          </label>
          <SecondaryButton v-if="reports.some(r => !r.acknowledged)" :icon="['fas', 'check-double']" @click="ackAll">
            {{ t('problemReport.acknowledgeAll') }}
          </SecondaryButton>
        </div>
      </div>

      <AsyncSection
        :empty="reports.length === 0"
        :empty-message="t('problemReport.empty')"
        :loading="loading"
      >
        <ReportCard
          v-for="r in reports"
          :key="r.id"
          :report="r"
          :expanded="expandedId === r.id"
          :can-forward="reportsToABeacon"
          @toggle="toggle"
          @ack="ack"
          @remove="remove"
          @forward="forward"
        />
      </AsyncSection>

      <BeaconPreviewModal
        v-model:open="showPreview"
        kind="report"
        :entry-id="forwardId"
        :has-picture="reports.some(r => r.id === forwardId && !!r.screenshotFileId)"
        @sent="forwarded = t('beacon.queued', {count: 1})"
      />
      <Alert v-if="forwarded" variant="success">{{ forwarded }}</Alert>
    </div>
  </ViewContent>
</template>
