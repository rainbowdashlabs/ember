/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import {stationApplications} from '@/api'
import type {StationApplication} from '@/api/stationApplications'
import {useBreakpoint} from '@/composables/useBreakpoint'
import {useModalTarget} from '@/composables/useModalTarget'
import ApplicationsTabs from './adminapplicationsview/ApplicationsTabs.vue'
import ApplicationsMobileList from './adminapplicationsview/ApplicationsMobileList.vue'
import ApplicationsDesktopTable from './adminapplicationsview/ApplicationsDesktopTable.vue'
import DenyApplicationModal from './adminapplicationsview/DenyApplicationModal.vue'
import type {ApplicationTab} from './adminapplicationsview/types'
import {useConfigPanel} from '@/composables/useConfigPanel'

const {isMobile} = useBreakpoint()

const {t} = useI18n()

const activeTab = ref<ApplicationTab>('pending')

const denyReason = ref('')
const {isOpen: showDenyModal, target: denyTarget, open: openDeny} = useModalTarget<StationApplication>(() => {
  denyReason.value = ''
})
const processing = ref(false)

const {config: applications, loading, error, runWith} = useConfigPanel<StationApplication[]>({
  initial: [],
  fetch: () => stationApplications.listAll(),
})

const filteredApplications = computed(() => {
  if (activeTab.value === 'pending') {
    return applications.value.filter(a => a.status === 'pending')
  }
  return applications.value
})

async function acceptApplication(app: StationApplication) {
  await runWith(async () => {
    await stationApplications.accept(app.id)
    return stationApplications.listAll()
  }, {busy: processing})
}

async function submitDeny() {
  if (!denyTarget.value) return
  await runWith(async () => {
    await stationApplications.deny(denyTarget.value!.id, denyReason.value)
    showDenyModal.value = false
    return stationApplications.listAll()
  }, {busy: processing})
}

function formatDate(dateStr?: string | null): string {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('de-DE')
}
</script>

<template>
  <ViewContent :title="t('pages.admin-station-applications.title')" :subtitle="t('pages.admin-station-applications.subtitle')">
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <ApplicationsTabs v-model="activeTab"/>
        <EmptyState v-if="filteredApplications.length === 0">{{ t('adminApplications.empty') }}</EmptyState>
        <ApplicationsMobileList
            v-else-if="isMobile"
            :applications="filteredApplications"
            :processing="processing"
            :format-date="formatDate"
            @accept="acceptApplication"
            @deny="openDeny"/>
        <ApplicationsDesktopTable
            v-else
            :applications="filteredApplications"
            :processing="processing"
            :format-date="formatDate"
            @accept="acceptApplication"
            @deny="openDeny"/>
      </template>

      <DenyApplicationModal
          v-model="showDenyModal"
          v-model:reason="denyReason"
          :target="denyTarget"
          :processing="processing"
          @submit="submitDeny"/>
    </div>
  </ViewContent>
</template>
