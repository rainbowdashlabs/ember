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
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FormShareLink from '@/components/public/FormShareLink.vue'
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
  /**
   * Where writing a form happens. A contact form and a public poll are edited on their own screens
   * under the public pages, because that is where they belong and where their link lives; the
   * station's own surveys keep the screens they always had.
   *
   * <p>Without this the two shared one address, so editing a contact form lit up the surveys entry
   * in the menu and put the reader at an address that said the form lived somewhere it does not.
   */
  createRouteName?: string
  editRouteName?: string
}>(), {
  analyticsRouteName: 'forms-analytics',
  createRouteName: 'forms-create',
  editRouteName: 'forms-edit',
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
  /** Whatever the act answers with is ignored; the list is caught up separately afterwards. */
  action: () => Promise<unknown>
}

const { loading, failure, reload } = useAsyncLoader(async () => {
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

/**
 * The act and the list refresh that follows it, kept apart on purpose. Sharing one attempt meant a
 * form that really was deleted, followed by a list that failed to come back, read as a deletion that
 * had failed, and the reader deleted it again.
 */
const confirmAction = useConfirmAction<PendingConfirm>({
  onConfirm: async (pending) => { await pending.action() },
  onSuccess: () => reload(),
})

function showConfirm(message: string, action: () => Promise<unknown>) {
  confirmAction.request({message, action})
}

function statusLabel(status: string) {
  if (status === FormStatus.OPEN) return t('forms.statusOpen')
  if (status === FormStatus.CLOSED) return t('forms.statusClosed')
  return t('forms.statusDraft')
}

function publishForm(form: Form) {
  showConfirm(t('forms.confirmPublish'), () => forms.publishForm(form.id))
}

function closeForm(form: Form) {
  showConfirm(t('forms.confirmClose'), () => forms.closeForm(form.id))
}

function deleteForm(form: Form) {
  showConfirm(t('forms.confirmDelete'), () => forms.deleteForm(form.id))
}

function goCreate() {
  router.push({ name: props.createRouteName, query: props.purpose ? { purpose: props.purpose } : undefined })
}

function editPage(form: Form) {
  return { name: props.editRouteName, params: { id: form.id } }
}

function analyticsPage(form: Form) {
  return { name: props.analyticsRouteName, params: { id: form.id } }
}

/**
 * Where a tile leads. A form nobody may answer yet is still being written, so it opens where it is
 * written; one that is out opens on what came back.
 */
function formPage(form: Form) {
  return form.status === FormStatus.DRAFT ? editPage(form) : analyticsPage(form)
}

function goEdit(form: Form) {
  router.push(editPage(form))
}

const sharedForm = ref<Form | null>(null)
const shareOpen = ref(false)

function openShareLink(form: Form) {
  sharedForm.value = form
  shareOpen.value = true
}

function goAnalytics(form: Form) {
  router.push(analyticsPage(form))
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
      <FailureAlert :failure="confirmAction.failure.value ?? failure"/>

      <template v-if="!loading">
        <ManagedFormsSection
          v-if="canViewResults"
          :forms="managedForms"
          :can-create-polls="canCreatePolls"
          :title-key="props.titleKey"
          :status-label="statusLabel"
          :form-page="formPage"
          @create="goCreate"
          @publish="publishForm"
          @close="closeForm"
          @edit="goEdit"
          @analytics="goAnalytics"
          @share="openShareLink"
          @delete="deleteForm"
        />

        <Modal v-model="shareOpen">
          <div class="space-y-4">
            <SubHeader>{{ t('forms.share') }}</SubHeader>
            <FormShareLink v-if="sharedForm" :form="sharedForm"/>
          </div>
        </Modal>

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
