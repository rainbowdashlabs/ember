/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import { FormStatus } from '@/api/types'
import type { Form, FormListEntry } from '@/api/types'
import { forms } from '@/api'
import { useSession } from '@/composables/useSession'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'

const { t } = useI18n()
const router = useRouter()
const { canManagePolls, loaded } = useSession()

const managedForms = ref<Form[]>([])
const availableForms = ref<FormListEntry[]>([])
const loading = ref(true)
const error = ref('')

// Confirmation modal state
const confirmModalOpen = ref(false)
const confirmModalMessage = ref('')
const confirmModalAction = ref<(() => Promise<void>) | null>(null)

function showConfirm(message: string, action: () => Promise<void>) {
  confirmModalMessage.value = message
  confirmModalAction.value = action
  confirmModalOpen.value = true
}

async function executeConfirm() {
  confirmModalOpen.value = false
  if (confirmModalAction.value) {
    try { await confirmModalAction.value() } catch { /* ignore */ }
  }
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    if (canManagePolls()) {
      managedForms.value = await forms.listForms()
    }
    availableForms.value = await forms.listAvailableForms()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function statusLabel(status: string) {
  if (status === FormStatus.OPEN) return t('forms.statusOpen')
  if (status === FormStatus.CLOSED) return t('forms.statusClosed')
  return t('forms.statusDraft')
}

function publishForm(form: Form) {
  showConfirm(t('forms.confirmPublish'), async () => {
    await forms.publishForm(form.id)
    await loadData()
  })
}

function closeForm(form: Form) {
  showConfirm(t('forms.confirmClose'), async () => {
    await forms.closeForm(form.id)
    await loadData()
  })
}

function deleteForm(form: Form) {
  showConfirm(t('forms.confirmDelete'), async () => {
    await forms.deleteForm(form.id)
    await loadData()
  })
}

onMounted(() => {
  if (loaded.value) loadData()
})

watch(loaded, (isLoaded) => {
  if (isLoaded && loading.value) loadData()
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <!-- Management Section -->
        <div v-if="canManagePolls()" class="space-y-4">
          <div class="flex items-center justify-between">
            <SectionHeader>{{ t('forms.title') }}</SectionHeader>
            <PrimaryButton :icon="['fas', 'plus']" @click="router.push({ name: 'forms-create' })">
              {{ t('forms.create') }}
            </PrimaryButton>
          </div>

          <EmptyState compact v-if="managedForms.length === 0">{{ t('forms.noForms') }}</EmptyState>

          <div class="space-y-2">
            <NeutralContainer v-for="form in managedForms" :key="form.id">
              <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
                <div class="space-y-1">
                  <div class="flex items-center gap-2 flex-wrap">
                    <span class="font-medium">{{ form.title }}</span>
                    <MutedIcon v-if="form.restricted" :icon="['fas', 'lock']" class="ml-1"/>
                    <SuccessBadge v-if="form.status === FormStatus.OPEN">{{ statusLabel(form.status) }}</SuccessBadge>
                    <ErrorBadge v-else-if="form.status === FormStatus.CLOSED">{{ statusLabel(form.status) }}</ErrorBadge>
                    <InfoBadge v-else>{{ statusLabel(form.status) }}</InfoBadge>
                  </div>
                  <p v-if="form.description" class="text-xs text-(--text-muted)">{{ form.description }}</p>
                </div>
                <div class="flex items-center gap-2 flex-wrap">
                  <SecondaryButton v-if="form.status === FormStatus.DRAFT" @click="publishForm(form)">
                    {{ t('forms.publish') }}
                  </SecondaryButton>
                  <SecondaryButton v-if="form.status === FormStatus.OPEN" @click="closeForm(form)">
                    {{ t('forms.close') }}
                  </SecondaryButton>
                  <SecondaryButton v-if="form.status !== FormStatus.CLOSED"
                                   @click="router.push({ name: 'forms-edit', params: { id: form.id } })">
                    {{ t('forms.edit') }}
                  </SecondaryButton>
                  <SecondaryButton v-if="form.status !== FormStatus.DRAFT"
                                   @click="router.push({ name: 'forms-analytics', params: { id: form.id } })">
                    {{ t('forms.viewAnalytics') }}
                  </SecondaryButton>
                  <ErrorButton @click="deleteForm(form)">
                    {{ t('forms.delete') }}
                  </ErrorButton>
                </div>
              </div>
            </NeutralContainer>
          </div>
        </div>

        <!-- Available Forms for User -->
        <div class="space-y-4">
          <SectionHeader v-if="canManagePolls()" class="mt-6">{{ t('forms.fillForm') }}</SectionHeader>

          <EmptyState compact v-if="availableForms.length === 0">{{ t('forms.noAvailableForms') }}</EmptyState>

          <div class="space-y-2">
            <NeutralContainer v-for="form in availableForms" :key="form.id">
              <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
                <div class="space-y-1">
                  <span class="font-medium">{{ form.title }}</span>
                  <MutedIcon v-if="form.restricted" :icon="['fas', 'lock']" class="ml-1"/>
                  <p v-if="form.description" class="text-xs text-(--text-muted)">{{ form.description }}</p>
                  <p class="text-xs text-(--text-muted)">{{ form.responseCount }} {{ t('forms.responses') }}</p>
                </div>
                <div>
                  <PrimaryButton v-if="!form.hasResponded"
                                 @click="router.push({ name: 'forms-fill', params: { id: form.id } })">
                    {{ t('forms.fillForm') }}
                  </PrimaryButton>
                  <SecondaryButton v-else
                                   @click="router.push({ name: 'forms-fill', params: { id: form.id } })">
                    {{ t('forms.editResponse') }}
                  </SecondaryButton>
                </div>
              </div>
            </NeutralContainer>
          </div>
        </div>
      </template>

      <!-- Confirmation Modal -->
      <Modal v-model="confirmModalOpen">
        <div class="space-y-4">
          <p class="text-sm">{{ confirmModalMessage }}</p>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="confirmModalOpen = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton @click="executeConfirm">{{ t('forms.confirm') }}</PrimaryButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
