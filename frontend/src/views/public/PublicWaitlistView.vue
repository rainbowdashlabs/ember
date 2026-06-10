/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref, computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import type {PublicWaitlistSummary, PublicWaitlistFormResponse, WaitingListField} from '@/api/types'
import {waitingList} from '@/api'

const {t} = useI18n()
const route = useRoute()
const stationUid = computed(() => route.params.stationUid as string)

const loading = ref(true)
const error = ref('')
const lists = ref<PublicWaitlistSummary[]>([])
const selectedListId = ref<number | null>(null)
const form = ref<PublicWaitlistFormResponse | null>(null)
const loadingForm = ref(false)

// Form data
const firstname = ref('')
const lastname = ref('')
const email = ref('')
const notes = ref('')
const guardianName = ref('')
const guardianEmail = ref('')
const guardianPhone = ref('')
const fieldValues = ref<Record<number, string>>({})
const submitting = ref(false)
const submitted = ref(false)

async function loadLists() {
  loading.value = true
  error.value = ''
  try {
    lists.value = await waitingList.listPublicWaitlists(stationUid.value)
    if (lists.value.length === 1) {
      selectedListId.value = lists.value[0].id
      await loadForm()
    }
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

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

function fieldValueModel(field: WaitingListField) {
  return fieldValues.value[field.id] ?? ''
}

function setFieldValue(field: WaitingListField, value: string) {
  fieldValues.value[field.id] = value
}

const canSubmit = computed(() => {
  if (!firstname.value.trim() || !email.value.trim()) return false
  if (!form.value) return false
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
    const guardians = guardianName.value.trim()
        ? [{name: guardianName.value.trim(), email: guardianEmail.value.trim(), phone: guardianPhone.value.trim()}]
        : []
    const values: Record<number, string> = {}
    for (const [k, v] of Object.entries(fieldValues.value)) {
      if (v) values[Number(k)] = JSON.stringify(v)
    }
    await waitingList.submitPublicRegistration(stationUid.value, selectedListId.value, {
      firstname: firstname.value.trim(),
      lastname: lastname.value.trim(),
      email: email.value.trim(),
      guardians,
      values,
      notes: notes.value.trim() || undefined,
    })
    submitted.value = true
  } catch {
    error.value = t('common.error')
  } finally {
    submitting.value = false
  }
}

function parseConfig(configStr: string | undefined | null): Record<string, unknown> {
  if (!configStr) return {}
  try { return JSON.parse(configStr) } catch { return {} }
}

onMounted(loadLists)
</script>

<template>
  <ViewContent>
  <div class="max-w-2xl mx-auto space-y-6">
    <SectionHeader>{{ t('waitingList.publicRegistration.title') }}</SectionHeader>
    <p class="text-(--text-muted)">{{ t('waitingList.publicRegistration.subtitle') }}</p>

    <Spinner v-if="loading" size="lg"/>
    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <!-- Success state -->
    <SuccessContainer v-if="submitted" class="space-y-3">
      <SubHeader>{{ t('waitingList.publicRegistration.successTitle') }}</SubHeader>
      <p>{{ t('waitingList.publicRegistration.successText') }}</p>
      <p class="text-sm text-(--text-muted)">{{ t('waitingList.publicRegistration.successHint') }}</p>
    </SuccessContainer>

    <!-- List selection (if multiple) -->
    <template v-if="!loading && !submitted">
      <EmptyState v-if="lists.length === 0">{{ t('waitingList.publicRegistration.noLists') }}</EmptyState>

      <div v-if="lists.length > 1 && !form" class="space-y-3">
        <SubHeader>{{ t('waitingList.publicRegistration.selectList') }}</SubHeader>
        <NeutralContainer
            v-for="l in lists"
            :key="l.id"
            class="cursor-pointer hover:ring-2 hover:ring-primary/40 transition-all p-4"
            @click="selectList(l.id)"
        >
          <SubHeader>{{ l.name }}</SubHeader>
          <p v-if="l.description" class="text-sm text-(--text-muted) mt-1">{{ l.description }}</p>
        </NeutralContainer>
      </div>

      <!-- Registration form -->
      <Spinner v-if="loadingForm" size="lg"/>
      <template v-if="form && !loadingForm">
        <NeutralContainer v-if="lists.length > 1" class="flex items-center justify-between">
          <SubHeader>{{ form.listName }}</SubHeader>
          <SecondaryButton size="sm" @click="form = null; selectedListId = null">
            {{ t('waitingList.back') }}
          </SecondaryButton>
        </NeutralContainer>

        <NeutralContainer class="space-y-4">
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div class="space-y-1">
              <FormLabel>{{ t('waitingList.publicRegistration.firstname') }} *</FormLabel>
              <TextInput v-model="firstname"/>
            </div>
            <div class="space-y-1">
              <FormLabel>{{ t('waitingList.publicRegistration.lastname') }}</FormLabel>
              <TextInput v-model="lastname"/>
            </div>
          </div>
          <div class="space-y-1">
            <FormLabel>{{ t('waitingList.publicRegistration.email') }} *</FormLabel>
            <TextInput v-model="email" type="email"/>
            <p class="text-xs text-(--text-muted)">{{ t('waitingList.publicRegistration.emailHint') }}</p>
          </div>

          <!-- Guardian -->
          <div class="space-y-3">
            <SubHeader>{{ t('waitingList.guardian') }}</SubHeader>
            <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div class="space-y-1">
                <FormLabel>{{ t('waitingList.firstname') }}</FormLabel>
                <TextInput v-model="guardianName" :placeholder="t('waitingList.guardianNamePlaceholder')"/>
              </div>
              <div class="space-y-1">
                <FormLabel>{{ t('waitingList.email') }}</FormLabel>
                <TextInput v-model="guardianEmail" :placeholder="t('waitingList.guardianEmailPlaceholder')"/>
              </div>
              <div class="space-y-1">
                <FormLabel>{{ t('waitingList.register.parentNamePlaceholder') }}</FormLabel>
                <TextInput v-model="guardianPhone" :placeholder="t('waitingList.guardianPhonePlaceholder')"/>
              </div>
            </div>
          </div>

          <!-- Custom fields -->
          <template v-if="form.fields.length > 0">
            <div v-for="field in form.fields" :key="field.id" class="space-y-1">
              <FormLabel>{{ field.name }}{{ field.required ? ' *' : '' }}</FormLabel>
              <TextInput v-if="field.fieldType === 'TEXT'" :model-value="fieldValueModel(field)" @update:model-value="setFieldValue(field, $event)"/>
              <NumberInput v-else-if="field.fieldType === 'NUMBER'" :model-value="Number(fieldValueModel(field)) || 0" @update:model-value="setFieldValue(field, String($event))"/>
              <DateInput v-else-if="field.fieldType === 'DATE'" :model-value="fieldValueModel(field)" @update:model-value="setFieldValue(field, $event)"/>
              <ToggleInput v-else-if="field.fieldType === 'BOOLEAN'" :model-value="fieldValueModel(field) === 'true'" @update:model-value="setFieldValue(field, String($event))"/>
              <SelectInput v-else-if="field.fieldType === 'ENUM'" :model-value="fieldValueModel(field)" @update:model-value="setFieldValue(field, $event)">
                <option value="">{{ t('waitingList.selectOption') }}</option>
                <option v-for="opt in (parseConfig(field.config) as {options?: string[]}).options ?? []" :key="opt" :value="opt">{{ opt }}</option>
              </SelectInput>
            </div>
          </template>

          <!-- Notes -->
          <div class="space-y-1">
            <FormLabel>{{ t('waitingList.publicRegistration.notes') }}</FormLabel>
            <TextAreaInput v-model="notes" :placeholder="t('waitingList.publicRegistration.notesPlaceholder')"/>
          </div>

          <PrimaryButton :disabled="!canSubmit || submitting" class="w-full" @click="submit">
            {{ submitting ? t('common.loading') : t('waitingList.publicRegistration.submit') }}
          </PrimaryButton>
        </NeutralContainer>
      </template>
    </template>
  </div>
  </ViewContent>
</template>
