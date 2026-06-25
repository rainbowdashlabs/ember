/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import PageHeroIcon from '@/components/typography/PageHeroIcon.vue'
import StatusDetails from './waitingliststatusview/StatusDetails.vue'
import StatusActions from './waitingliststatusview/StatusActions.vue'
import RemoveConfirmationModal from './waitingliststatusview/RemoveConfirmationModal.vue'
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

onMounted(loadStatus)
</script>

<template>
  <div class="flex items-center justify-center px-4 py-12">
    <div class="w-full max-w-lg space-y-6">
      <div class="text-center">
        <PageHeroIcon :icon="['fas', 'clipboard-list']"/>
        <PageHeader class="text-2xl font-bold">{{ t('waitingList.publicStatus.title') }}</PageHeader>
      </div>

      <Spinner v-if="loading" size="md" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <template v-if="removed">
        <Alert variant="info">{{ t('waitingList.publicStatus.removed') }}</Alert>
      </template>

      <template v-if="!loading && status && !removed">
        <StatusDetails :status="status" />
        <StatusActions
          v-if="status.status === 'WAITING'"
          :confirming="confirming"
          @confirm="confirmInterest"
          @remove="showRemoveModal = true"
        />
      </template>

      <template v-if="!loading && !status && !removed">
        <div class="text-center">
          <router-link class="text-sm text-primary hover:underline" to="/login">{{ t('waitingList.publicStatus.backToLogin') }}</router-link>
        </div>
      </template>

      <RemoveConfirmationModal
        v-model="showRemoveModal"
        :removing="removing"
        @confirm="removeFromList"
      />
    </div>
  </div>
</template>
