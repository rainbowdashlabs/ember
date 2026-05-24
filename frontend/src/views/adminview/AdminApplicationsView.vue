/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import {stationApplications} from '@/api'
import type {StationApplication} from '@/api/stationApplications'
import {useBreakpoint} from '@/composables/useBreakpoint'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'

const {isMobile} = useBreakpoint()

const {t} = useI18n()

const applications = ref<StationApplication[]>([])
const loading = ref(true)
const error = ref('')
const activeTab = ref<'pending' | 'all'>('pending')

// Deny modal
const showDenyModal = ref(false)
const denyTarget = ref<StationApplication | null>(null)
const denyReason = ref('')
const processing = ref(false)

const filteredApplications = computed(() => {
  if (activeTab.value === 'pending') {
    return applications.value.filter(a => a.status === 'pending')
  }
  return applications.value
})

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    applications.value = await stationApplications.listAll()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function acceptApplication(app: StationApplication) {
  processing.value = true
  error.value = ''
  try {
    await stationApplications.accept(app.id)
    await loadData()
  } catch {
    error.value = t('common.error')
  } finally {
    processing.value = false
  }
}

function openDeny(app: StationApplication) {
  denyTarget.value = app
  denyReason.value = ''
  showDenyModal.value = true
}

async function submitDeny() {
  if (!denyTarget.value) return
  processing.value = true
  error.value = ''
  try {
    await stationApplications.deny(denyTarget.value.id, denyReason.value)
    showDenyModal.value = false
    await loadData()
  } catch {
    error.value = t('common.error')
  } finally {
    processing.value = false
  }
}

function formatDate(dateStr?: string | null): string {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('de-DE')
}


onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <SectionHeader>{{ t('adminApplications.title') }}</SectionHeader>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <!-- Tabs -->
        <div class="flex gap-2 border-b border-bg-light-accent dark:border-bg-dark-accent">
          <button
              :class="activeTab === 'pending' ? 'border-b-2 border-primary text-primary' : 'text-(--text-muted) hover:text-(--text)'"
              class="px-4 py-2 text-sm font-medium transition-colors"
              @click="activeTab = 'pending'"
          >
            {{ t('adminApplications.pending') }}
          </button>
          <button
              :class="activeTab === 'all' ? 'border-b-2 border-primary text-primary' : 'text-(--text-muted) hover:text-(--text)'"
              class="px-4 py-2 text-sm font-medium transition-colors"
              @click="activeTab = 'all'"
          >
            {{ t('adminApplications.all') }}
          </button>
        </div>

        <EmptyState v-if="filteredApplications.length === 0">{{ t('adminApplications.empty') }}</EmptyState>

        <!-- Mobile card layout -->
        <div v-if="isMobile && filteredApplications.length > 0" class="space-y-3">
          <NeutralContainer v-for="app in filteredApplications" :key="app.id" class="space-y-2">
            <div class="flex items-center justify-between">
              <div class="font-medium">{{ app.firstName }} {{ app.lastName }}</div>
              <SuccessBadge v-if="app.status === 'accepted'">{{ t('adminApplications.accepted') }}</SuccessBadge>
              <ErrorBadge v-else-if="app.status === 'denied'">{{ t('adminApplications.denied') }}</ErrorBadge>
              <SecondaryBadge v-else>{{ t('adminApplications.pendingBadge') }}</SecondaryBadge>
            </div>
            <div class="grid grid-cols-1 gap-1 text-xs">
              <div class="text-(--text-muted)">{{ app.email }}</div>
              <div><span class="text-(--text-muted)">{{ t('adminApplications.station') }}:</span> {{ app.stationName }}</div>
              <div class="text-(--text-muted)">{{ formatDate(app.createdAt) }}</div>
              <div v-if="app.introduction" class="text-(--text-muted) truncate">{{ app.introduction }}</div>
            </div>
            <div v-if="app.status === 'pending'" class="flex gap-1 pt-1 border-t border-bg-light-accent/50 dark:border-bg-dark-accent/50">
              <PrimaryButton :disabled="processing" @click="acceptApplication(app)">{{ t('adminApplications.accept') }}</PrimaryButton>
              <ErrorButton :disabled="processing" @click="openDeny(app)">{{ t('adminApplications.deny') }}</ErrorButton>
            </div>
            <div v-else-if="app.status === 'denied' && app.denyReason" class="text-xs text-(--text-muted)">{{ app.denyReason }}</div>
          </NeutralContainer>
        </div>

        <!-- Desktop table layout -->
        <NeutralContainer v-else-if="filteredApplications.length > 0" class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
            <THead>
              <Th>{{ t('adminApplications.name') }}</Th>
              <Th>{{ t('adminApplications.email') }}</Th>
              <Th>{{ t('adminApplications.station') }}</Th>
              <Th>{{ t('adminApplications.date') }}</Th>
              <Th>{{ t('adminApplications.status') }}</Th>
              <th class="px-3 py-2"></th>
            </THead>
            </thead>
            <tbody>
            <TRow v-for="app in filteredApplications" :key="app.id">
              <Td>
                <div class="font-medium">{{ app.firstName }} {{ app.lastName }}</div>
                <div v-if="app.introduction" :title="app.introduction"
                     class="text-xs text-(--text-muted) mt-0.5 max-w-xs truncate">{{ app.introduction }}
                </div>
              </Td>
              <Td muted>{{ app.email }}</Td>
              <Td>{{ app.stationName }}</Td>
              <Td muted>{{ formatDate(app.createdAt) }}</Td>
              <Td>
                <SuccessBadge v-if="app.status === 'accepted'">{{ t('adminApplications.accepted') }}</SuccessBadge>
                <ErrorBadge v-else-if="app.status === 'denied'">{{ t('adminApplications.denied') }}</ErrorBadge>
                <SecondaryBadge v-else>{{ t('adminApplications.pendingBadge') }}</SecondaryBadge>
              </Td>
              <Td align="right">
                <div v-if="app.status === 'pending'" class="flex items-center justify-end gap-1">
                  <PrimaryButton :disabled="processing" @click="acceptApplication(app)">
                    {{ t('adminApplications.accept') }}
                  </PrimaryButton>
                  <ErrorButton :disabled="processing" @click="openDeny(app)">
                    {{ t('adminApplications.deny') }}
                  </ErrorButton>
                </div>
                <div v-else-if="app.status === 'denied' && app.denyReason" class="text-xs text-(--text-muted)">
                  {{ app.denyReason }}
                </div>
              </Td>
            </TRow>
            </tbody>
          </table>
        </NeutralContainer>
      </template>

      <!-- Deny modal -->
      <Modal v-model="showDenyModal">
        <form class="space-y-4" @submit.prevent="submitDeny">
          <SectionHeader>{{ t('adminApplications.denyTitle') }}</SectionHeader>
          <p class="text-sm text-(--text-muted)">
            {{ denyTarget?.firstName }} {{ denyTarget?.lastName }} — {{ denyTarget?.stationName }}
          </p>
          <div class="space-y-1">
            <FieldLabel>{{ t('adminApplications.denyReasonLabel') }}</FieldLabel>
            <TextInput v-model="denyReason" :placeholder="t('adminApplications.denyReasonPlaceholder')"/>
          </div>
          <div class="flex justify-end gap-3">
            <SecondaryButton type="button" @click="showDenyModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <ErrorButton :disabled="processing" type="submit">
              {{ processing ? t('common.loading') : t('adminApplications.deny') }}
            </ErrorButton>
          </div>
        </form>
      </Modal>
    </div>
  </ViewContent>
</template>
