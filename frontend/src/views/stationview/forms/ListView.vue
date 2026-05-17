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
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import type { Form, FormListEntry } from '@/api/types'
import { forms } from '@/api'
import { useSession } from '@/composables/useSession'

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
  if (status === 'OPEN') return t('forms.statusOpen')
  if (status === 'CLOSED') return t('forms.statusClosed')
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
            <h2 class="text-lg font-semibold">{{ t('forms.title') }}</h2>
            <PrimaryButton @click="router.push({ name: 'forms-create' })">
              <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
              {{ t('forms.create') }}
            </PrimaryButton>
          </div>

          <div v-if="managedForms.length === 0" class="text-center text-(--text-muted) py-4">
            {{ t('forms.noForms') }}
          </div>

          <div class="space-y-2">
            <NeutralContainer v-for="form in managedForms" :key="form.id">
              <div class="flex items-center justify-between">
                <div class="space-y-1">
                  <div class="flex items-center gap-2">
                    <span class="font-medium">{{ form.title }}</span>
                    <SuccessBadge v-if="form.status === 'OPEN'">{{ statusLabel(form.status) }}</SuccessBadge>
                    <ErrorBadge v-else-if="form.status === 'CLOSED'">{{ statusLabel(form.status) }}</ErrorBadge>
                    <InfoBadge v-else>{{ statusLabel(form.status) }}</InfoBadge>
                  </div>
                  <p v-if="form.description" class="text-xs text-(--text-muted)">{{ form.description }}</p>
                </div>
                <div class="flex items-center gap-2">
                  <SecondaryButton v-if="form.status === 'DRAFT'" class="text-xs" @click="publishForm(form)">
                    {{ t('forms.publish') }}
                  </SecondaryButton>
                  <SecondaryButton v-if="form.status === 'OPEN'" class="text-xs" @click="closeForm(form)">
                    {{ t('forms.close') }}
                  </SecondaryButton>
                  <SecondaryButton v-if="form.status !== 'CLOSED'" class="text-xs"
                                   @click="router.push({ name: 'forms-edit', params: { id: form.id } })">
                    {{ t('forms.edit') }}
                  </SecondaryButton>
                  <SecondaryButton v-if="form.status !== 'DRAFT'" class="text-xs"
                                   @click="router.push({ name: 'forms-analytics', params: { id: form.id } })">
                    {{ t('forms.viewAnalytics') }}
                  </SecondaryButton>
                  <ErrorButton class="text-xs" @click="deleteForm(form)">
                    {{ t('forms.delete') }}
                  </ErrorButton>
                </div>
              </div>
            </NeutralContainer>
          </div>
        </div>

        <!-- Available Forms for User -->
        <div class="space-y-4">
          <h2 v-if="canManagePolls()" class="text-lg font-semibold mt-6">{{ t('forms.fillForm') }}</h2>

          <div v-if="availableForms.length === 0" class="text-center text-(--text-muted) py-4">
            {{ t('forms.noAvailableForms') }}
          </div>

          <div class="space-y-2">
            <NeutralContainer v-for="form in availableForms" :key="form.id">
              <div class="flex items-center justify-between">
                <div class="space-y-1">
                  <span class="font-medium">{{ form.title }}</span>
                  <p v-if="form.description" class="text-xs text-(--text-muted)">{{ form.description }}</p>
                  <p class="text-xs text-(--text-muted)">{{ form.responseCount }} {{ t('forms.responses') }}</p>
                </div>
                <div>
                  <PrimaryButton v-if="!form.hasResponded" class="text-xs"
                                 @click="router.push({ name: 'forms-fill', params: { id: form.id } })">
                    {{ t('forms.fillForm') }}
                  </PrimaryButton>
                  <SecondaryButton v-else class="text-xs"
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
