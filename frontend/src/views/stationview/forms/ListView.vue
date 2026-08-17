/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useConfirmAction } from '@/composables/useConfirmAction'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import {FormStatus, type Form, type FormListEntry, type FormPurposeName} from '@/api/forms'
import { StationPermission } from '@/api/types'
import { forms } from '@/api'
import { useSession } from '@/composables/useSession'
import ManagedFormsSection from './listview/ManagedFormsSection.vue'
import AvailableFormsSection from './listview/AvailableFormsSection.vue'
import ConfirmActionModal from './listview/ConfirmActionModal.vue'

const props = withDefaults(defineProps<{
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
}>(), {
  /**
   * Vue gives an absent boolean prop the value {@code false} rather than leaving it undefined, so
   * a fallback written as `?? true` never applies and the section is simply off wherever nobody
   * asked for it. The default has to be stated here: without it, the forms page showed a member
   * nothing at all and never even asked the server what they may fill in.
   */
  showAvailableSection: true,
})

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const { hasPermission, loaded } = useSession()

/**
 * Three routes render this view - the general forms list and the page-editor's contact-form and
 * poll surfaces - so the header title has to follow the route rather than being fixed to the
 * general one. Each route owns a {@code pages.<route-name>} entry.
 */
const pageTitle = computed(() => t(`pages.${String(route.name)}.title`))
const pageSubtitle = computed(() => t(`pages.${String(route.name)}.subtitle`))
const canViewResults = computed(() => hasPermission(StationPermission.POLL_VIEW_RESULTS))
const canCreatePolls = computed(() => hasPermission(StationPermission.POLL_CREATE))
const showAvailable = computed(() => props.showAvailableSection)

const managedForms = ref<Form[]>([])
const availableForms = ref<FormListEntry[]>([])

interface PendingConfirm {
  message: string
  action: () => Promise<void>
}

const confirmAction = useConfirmAction<PendingConfirm>({
  onConfirm: async (pending) => {
    try {
      await pending.action()
    } catch {
      return
    }
  },
})

function showConfirm(message: string, action: () => Promise<void>) {
  confirmAction.request({message, action})
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

function openForm(form: Form) {
  if (form.status === FormStatus.DRAFT) goEdit(form)
  else goAnalytics(form)
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
  <ViewContent
      :title="pageTitle"
      :subtitle="pageSubtitle"
  >
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
          @open="openForm"
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
        v-model="confirmAction.show.value"
        :message="confirmAction.target.value?.message ?? ''"
        @confirm="confirmAction.confirm"
      />
    </div>
  </ViewContent>
</template>
