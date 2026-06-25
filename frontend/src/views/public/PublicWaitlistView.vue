/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import WaitlistSuccessPanel from './publicwaitlistview/WaitlistSuccessPanel.vue'
import WaitlistSelector from './publicwaitlistview/WaitlistSelector.vue'
import WaitlistFormHeader from './publicwaitlistview/WaitlistFormHeader.vue'
import WaitlistRegistrationForm from './publicwaitlistview/WaitlistRegistrationForm.vue'
import type {GuardianInput, PublicWaitlistSummary, PublicWaitlistFormResponse, WaitingListField} from '@/api/types'
import {waitingList} from '@/api'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

const {t} = useI18n()
const route = useRoute()
const stationUid = computed(() => route.params.stationUid as string)

const lists = ref<PublicWaitlistSummary[]>([])
const selectedListId = ref<number | null>(null)
const form = ref<PublicWaitlistFormResponse | null>(null)
const loadingForm = ref(false)

const firstname = ref('')
const lastname = ref('')
const email = ref('')
const notes = ref('')
const guardians = ref<GuardianInput[]>([{firstname: '', lastname: '', email: '', phone: ''}])
const fieldValues = ref<Record<number, string>>({})
const submitting = ref(false)
const submitted = ref(false)

function addGuardian() {
  guardians.value = [...guardians.value, {firstname: '', lastname: '', email: '', phone: ''}]
}

function removeGuardian(index: number) {
  guardians.value = guardians.value.filter((_, i) => i !== index)
}

const {loading, error} = useAsyncLoader(async () => {
  lists.value = await waitingList.listPublicWaitlists(stationUid.value)
  if (lists.value.length === 1) {
    selectedListId.value = lists.value[0].id
    await loadForm()
  }
})

async function loadForm() {
  if (!selectedListId.value) return
  loadingForm.value = true
  try {
    form.value = await waitingList.getPublicWaitlistForm(stationUid.value, selectedListId.value)
    fieldValues.value = {}
  } catch {
    error.value = t('common.error')
  } finally {
    loadingForm.value = false
  }
}

async function selectList(id: number) {
  selectedListId.value = id
  await loadForm()
}

function setFieldValue(field: WaitingListField, value: string) {
  fieldValues.value[field.id] = value
}

function clearSelection() {
  form.value = null
  selectedListId.value = null
}

const consentAccepted = ref(false)
const consentVersion = ref('')
const privacyVersion = ref('')
const tosVersion = ref('')

const canSubmit = computed(() => {
  if (!firstname.value.trim() || !email.value.trim()) return false
  if (!form.value) return false
  if (!consentAccepted.value) return false
  for (const f of form.value.fields) {
    if (f.required && !fieldValues.value[f.id]?.trim()) return false
  }
  return true
})

async function submit() {
  if (!selectedListId.value || !canSubmit.value) return
  submitting.value = true
  error.value = ''
  try {
    const guardianData = guardians.value
        .filter(g => g.firstname.trim() || g.email.trim())
        .map(g => ({firstname: g.firstname.trim(), lastname: g.lastname.trim(), email: g.email.trim(), phone: g.phone.trim()}))
    const values: Record<number, unknown> = {}
    for (const [k, v] of Object.entries(fieldValues.value)) {
      if (v) values[Number(k)] = v
    }
    await waitingList.submitPublicRegistration(stationUid.value, selectedListId.value, {
      firstname: firstname.value.trim(),
      lastname: lastname.value.trim(),
      email: email.value.trim(),
      guardians: guardianData,
      values,
      notes: notes.value.trim() || undefined,
      consentVersion: consentVersion.value,
      privacyVersion: privacyVersion.value,
      tosVersion: tosVersion.value,
    })
    submitted.value = true
  } catch {
    error.value = t('common.error')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <ViewContent>
    <div class="max-w-2xl mx-auto space-y-6">
      <SectionHeader>{{ t('waitingList.publicRegistration.title') }}</SectionHeader>
      <p class="text-(--text-muted)">{{ t('waitingList.publicRegistration.subtitle') }}</p>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <WaitlistSuccessPanel v-if="submitted"/>

      <template v-if="!loading && !submitted">
        <EmptyState v-if="lists.length === 0">{{ t('waitingList.publicRegistration.noLists') }}</EmptyState>

        <WaitlistSelector
            v-if="lists.length > 1 && !form"
            :lists="lists"
            @select="selectList"
        />

        <Spinner v-if="loadingForm" size="lg"/>
        <template v-if="form && !loadingForm">
          <WaitlistFormHeader
              :list-name="form.listName"
              :list-description="form.listDescription"
              :show-back="lists.length > 1"
              @back="clearSelection"
          />
          <WaitlistRegistrationForm
              :form="form"
              v-model:firstname="firstname"
              v-model:lastname="lastname"
              v-model:email="email"
              v-model:notes="notes"
              :guardians="guardians"
              :field-values="fieldValues"
              v-model:consent-accepted="consentAccepted"
              v-model:consent-version="consentVersion"
              v-model:privacy-version="privacyVersion"
              v-model:tos-version="tosVersion"
              :can-submit="canSubmit"
              :submitting="submitting"
              @add-guardian="addGuardian"
              @remove-guardian="removeGuardian"
              @set-field="setFieldValue"
              @submit="submit"
          />
        </template>
      </template>
    </div>
  </ViewContent>
</template>
