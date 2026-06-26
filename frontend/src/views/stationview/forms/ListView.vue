/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import { FormStatus, StationPermission } from '@/api/types'
import type { Form, FormListEntry, FormPurposeName } from '@/api/types'
import { forms } from '@/api'
import { useSession } from '@/composables/useSession'
import ManagedFormsSection from './listview/ManagedFormsSection.vue'
import AvailableFormsSection from './listview/AvailableFormsSection.vue'
import ConfirmActionModal from './listview/ConfirmActionModal.vue'

const props = defineProps<{
  /** When set, filters the list to forms of this purpose and pre-selects the same purpose for newly created forms. */
  purpose?: FormPurposeName
  /** Whether to show the "available forms to fill" section below the management list. Defaults to true for INTERNAL forms. */
  showAvailableSection?: boolean
  /** i18n key for the management-section heading. Defaults to the generic {@code forms.title}. */
  titleKey?: string
  /**
   * Router route name used by the "View analytics" button. Defaults to {@code 'forms-analytics'}
   * (the global, POLL_VIEW_RESULTS-gated analytics surface). The page-editor surfaces pass
   * {@code 'pages-forms-analytics'} / {@code 'pages-polls-analytics'} so page editors who do not
   * hold POLL_VIEW_RESULTS still land on a route that calls the PAGE_EDIT-gated analytics API.
   */
  analyticsRouteName?: string
}>()

const { t } = useI18n()
const router = useRouter()
const { hasPermission, loaded } = useSession()
const canViewResults = computed(() => hasPermission(StationPermission.POLL_VIEW_RESULTS))
const canCreatePolls = computed(() => hasPermission(StationPermission.POLL_CREATE))
const showAvailable = computed(() => props.showAvailableSection ?? true)

const managedForms = ref<Form[]>([])
const availableForms = ref<FormListEntry[]>([])

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

const { loading, error, reload } = useAsyncLoader(async () => {
  if (canViewResults.value) {
    managedForms.value = await forms.listForms(props.purpose)
  }
  if (showAvailable.value) {
    availableForms.value = await forms.listAvailableForms()
  } else {
    availableForms.value = []
  }
}, { autoLoad: false })
loading.value = true

function statusLabel(status: string) {
  if (status === FormStatus.OPEN) return t('forms.statusOpen')
  if (status === FormStatus.CLOSED) return t('forms.statusClosed')
  return t('forms.statusDraft')
}

function publishForm(form: Form) {
  showConfirm(t('forms.confirmPublish'), async () => {
    await forms.publishForm(form.id)
    await reload()
  })
}

function closeForm(form: Form) {
  showConfirm(t('forms.confirmClose'), async () => {
    await forms.closeForm(form.id)
    await reload()
  })
}

function deleteForm(form: Form) {
  showConfirm(t('forms.confirmDelete'), async () => {
    await forms.deleteForm(form.id)
    await reload()
  })
}

function goCreate() {
  router.push({ name: 'forms-create', query: props.purpose ? { purpose: props.purpose } : undefined })
}

function goEdit(form: Form) {
  router.push({ name: 'forms-edit', params: { id: form.id } })
}

function goAnalytics(form: Form) {
  router.push({ name: props.analyticsRouteName ?? 'forms-analytics', params: { id: form.id } })
}

function goFill(form: FormListEntry) {
  router.push({ name: 'forms-fill', params: { id: form.id } })
}

onMounted(() => {
  if (loaded.value) reload()
})

watch(loaded, (isLoaded) => {
  if (isLoaded) reload()
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <ManagedFormsSection
          v-if="canViewResults"
          :forms="managedForms"
          :can-create-polls="canCreatePolls"
          :title-key="props.titleKey"
          :status-label="statusLabel"
          @create="goCreate"
          @publish="publishForm"
          @close="closeForm"
          @edit="goEdit"
          @analytics="goAnalytics"
          @delete="deleteForm"
        />

        <AvailableFormsSection
          v-if="showAvailable"
          :forms="availableForms"
          :show-heading="canViewResults"
          @fill="goFill"
        />
      </template>

      <ConfirmActionModal
        v-model="confirmModalOpen"
        :message="confirmModalMessage"
        @confirm="executeConfirm"
      />
    </div>
  </ViewContent>
</template>
