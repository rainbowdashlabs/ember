/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Modal from '@/components/feedback/Modal.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import type { WaitingListPublicStatus } from '@/api/types'
import { waitingList } from '@/api'

const { t } = useI18n()
const route = useRoute()

const token = ref('')
const status = ref<WaitingListPublicStatus | null>(null)
const loading = ref(true)
const error = ref('')
const success = ref('')

const showRemoveModal = ref(false)
const removing = ref(false)
const removed = ref(false)

const confirming = ref(false)

async function loadStatus() {
  token.value = (route.query.token as string) ?? ''
  if (!token.value) {
    error.value = t('waitingList.publicStatus.noToken')
    loading.value = false
    return
  }
  try {
    status.value = await waitingList.getEntryStatus(token.value)
  } catch {
    error.value = t('waitingList.publicStatus.invalidToken')
  } finally {
    loading.value = false
  }
}

async function confirmInterest() {
  confirming.value = true
  error.value = ''
  try {
    await waitingList.confirmInterest(token.value)
    status.value = await waitingList.getEntryStatus(token.value)
    success.value = t('waitingList.publicStatus.confirmed')
    setTimeout(() => { success.value = '' }, 5000)
  } catch {
    error.value = t('common.error')
  } finally {
    confirming.value = false
  }
}

async function removeFromList() {
  removing.value = true
  error.value = ''
  try {
    await waitingList.removeEntry(token.value)
    removed.value = true
    showRemoveModal.value = false
  } catch {
    error.value = t('common.error')
  } finally {
    removing.value = false
  }
}

function statusBadgeComponent(statusStr: string) {
  if (statusStr === 'JOINED') return SuccessBadge
  if (statusStr === 'WITHDRAWN') return ErrorBadge
  if (statusStr === 'TESTING') return PrimaryBadge
  if (statusStr === 'INVITED') return InfoBadge
  return SecondaryBadge
}

function formatDateTime(dateStr: string | undefined | null): string {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString()
}

onMounted(loadStatus)
</script>

<template>
  <div class="flex items-center justify-center px-4 py-12">
    <div class="w-full max-w-lg space-y-6">
      <div class="text-center">
        <font-awesome-icon :icon="['fas', 'clipboard-list']" class="text-4xl text-primary mb-3" />
        <h1 class="text-2xl font-bold">{{ t('waitingList.publicStatus.title') }}</h1>
      </div>

      <Spinner v-if="loading" size="md" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <!-- Removed confirmation -->
      <template v-if="removed">
        <Alert variant="info">{{ t('waitingList.publicStatus.removed') }}</Alert>
      </template>

      <!-- Status display -->
      <template v-if="!loading && status && !removed">
        <NeutralContainer class="space-y-4">
          <div class="text-center space-y-1">
            <h2 class="text-lg font-semibold">{{ status.listName }}</h2>
          </div>

          <div class="grid gap-3 sm:grid-cols-2">
            <div class="text-sm">
              <span class="text-(--text-muted)">{{ t('waitingList.firstname') }}:</span>
              <span class="ml-1 font-medium">{{ status.firstname }}</span>
            </div>
            <div class="text-sm">
              <span class="text-(--text-muted)">{{ t('waitingList.lastname') }}:</span>
              <span class="ml-1 font-medium">{{ status.lastname || '-' }}</span>
            </div>
            <div class="text-sm">
              <span class="text-(--text-muted)">{{ t('waitingList.parentName') }}:</span>
              <span class="ml-1 font-medium">{{ status.parentName || '-' }}</span>
            </div>
            <div class="text-sm">
              <span class="text-(--text-muted)">{{ t('waitingList.status') }}:</span>
              <component :is="statusBadgeComponent(status.status)" class="ml-1">{{ t('waitingList.status_' + status.status) }}</component>
            </div>
            <div v-if="status.status === 'WAITING'" class="text-sm">
              <span class="text-(--text-muted)">{{ t('waitingList.position') }}:</span>
              <span class="ml-1 font-medium text-lg">{{ status.position }}</span>
            </div>
            <div class="text-sm sm:col-span-2">
              <span class="text-(--text-muted)">{{ t('waitingList.confirmedAt') }}:</span>
              <span class="ml-1 font-medium">{{ formatDateTime(status.confirmedAt) }}</span>
            </div>
          </div>

          <!-- Field values -->
          <div v-if="status.fields && status.fields.length > 0" class="border-t border-bg-light-accent dark:border-bg-dark-accent pt-3 space-y-2">
            <div v-for="field in status.fields" :key="field.id" class="text-sm">
              <span class="text-(--text-muted)">{{ field.name }}:</span>
              <span class="ml-1 font-medium">
                {{ status.values?.find(v => v.fieldId === field.id)?.value || '-' }}
              </span>
            </div>
          </div>
        </NeutralContainer>

        <!-- Actions -->
        <div v-if="status.status === 'WAITING'" class="flex flex-col sm:flex-row gap-3">
          <PrimaryButton :disabled="confirming" class="flex-1" @click="confirmInterest">
            <font-awesome-icon :icon="['fas', 'check']" class="mr-2" />
            {{ confirming ? t('common.loading') : t('waitingList.publicStatus.confirmInterest') }}
          </PrimaryButton>
          <ErrorButton class="flex-1" @click="showRemoveModal = true">
            <font-awesome-icon :icon="['fas', 'xmark']" class="mr-2" />
            {{ t('waitingList.publicStatus.removeFromList') }}
          </ErrorButton>
        </div>
      </template>

      <!-- Invalid token state -->
      <template v-if="!loading && !status && !removed">
        <div class="text-center">
          <router-link class="text-sm text-primary hover:underline" to="/login">{{ t('waitingList.publicStatus.backToLogin') }}</router-link>
        </div>
      </template>

      <!-- Remove confirmation modal -->
      <Modal v-model="showRemoveModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('waitingList.publicStatus.removeTitle') }}</SectionHeader>
          <p class="text-sm">{{ t('waitingList.publicStatus.removeConfirm') }}</p>
          <p class="text-xs text-(--text-muted)">{{ t('waitingList.publicStatus.removeHint') }}</p>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showRemoveModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <ErrorButton :disabled="removing" @click="removeFromList">
              {{ removing ? t('common.loading') : t('waitingList.publicStatus.removeFromList') }}
            </ErrorButton>
          </div>
        </div>
      </Modal>
    </div>
  </div>
</template>
