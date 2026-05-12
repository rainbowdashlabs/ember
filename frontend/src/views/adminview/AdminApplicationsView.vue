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
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import {stationApplications} from '@/api'
import type {StationApplication} from '@/api/stationApplications'

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

        <div v-if="filteredApplications.length === 0" class="text-center text-(--text-muted) py-8">
          {{ t('adminApplications.empty') }}
        </div>

        <NeutralContainer v-else class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
            <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
              <th class="px-3 py-2 font-medium">{{ t('adminApplications.name') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('adminApplications.email') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('adminApplications.station') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('adminApplications.date') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('adminApplications.status') }}</th>
              <th class="px-3 py-2"></th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="app in filteredApplications" :key="app.id"
                class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
              <td class="px-3 py-2.5">
                <div class="font-medium">{{ app.firstName }} {{ app.lastName }}</div>
                <div v-if="app.introduction" :title="app.introduction"
                     class="text-xs text-(--text-muted) mt-0.5 max-w-xs truncate">{{ app.introduction }}
                </div>
              </td>
              <td class="px-3 py-2.5 text-(--text-muted)">{{ app.email }}</td>
              <td class="px-3 py-2.5">{{ app.stationName }}</td>
              <td class="px-3 py-2.5 text-(--text-muted)">{{ formatDate(app.createdAt) }}</td>
              <td class="px-3 py-2.5">
                <SuccessBadge v-if="app.status === 'accepted'">{{ t('adminApplications.accepted') }}</SuccessBadge>
                <ErrorBadge v-else-if="app.status === 'denied'">{{ t('adminApplications.denied') }}</ErrorBadge>
                <SecondaryBadge v-else>{{ t('adminApplications.pendingBadge') }}</SecondaryBadge>
              </td>
              <td class="px-3 py-2.5 text-right">
                <div v-if="app.status === 'pending'" class="flex items-center justify-end gap-1">
                  <PrimaryButton :disabled="processing" class="text-sm" @click="acceptApplication(app)">
                    {{ t('adminApplications.accept') }}
                  </PrimaryButton>
                  <ErrorButton :disabled="processing" class="text-sm" @click="openDeny(app)">
                    {{ t('adminApplications.deny') }}
                  </ErrorButton>
                </div>
                <div v-else-if="app.status === 'denied' && app.denyReason" class="text-xs text-(--text-muted)">
                  {{ app.denyReason }}
                </div>
              </td>
            </tr>
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
            <label class="block text-sm font-medium">{{ t('adminApplications.denyReasonLabel') }}</label>
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
