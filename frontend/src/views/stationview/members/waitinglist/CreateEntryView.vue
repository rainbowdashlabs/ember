/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {GuardianInput, WaitingListField} from '@/api/waitingList'
import {waitingList} from '@/api'
import {useConfigPanel} from '@/composables/useConfigPanel'
import EntryFormCard from './createentryview/EntryFormCard.vue'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()

const listId = computed(() => Number(route.params.id))

const {config: fields, loading, error} = useConfigPanel<WaitingListField[]>({
  initial: [],
  fetch: () => waitingList.listFields(listId.value),
  formatError: () => '',
})
const firstname = ref('')
const lastname = ref('')
const guardians = ref<GuardianInput[]>([{firstname: '', lastname: '', email: '', phone: ''}])
const notes = ref('')
const fieldValues = ref<Record<number, string>>({})

function addGuardian() {
  guardians.value = [...guardians.value, {firstname: '', lastname: '', email: '', phone: ''}]
}

function removeGuardian(index: number) {
  guardians.value = guardians.value.filter((_, i) => i !== index)
}

function setFieldValue(fieldId: number, value: string) {
  fieldValues.value = {...fieldValues.value, [fieldId]: value}
}

function parseConfig(configStr: string | undefined | null): Record<string, unknown> {
  if (!configStr) return {}
  try { return JSON.parse(configStr) } catch { return {} }
}

const canSave = computed(() =>
    firstname.value.trim() && guardians.value.some(g => g.email.trim()),
)

async function save() {
  if (!canSave.value) return
  error.value = ''
  try {
    const values: Record<number, unknown> = {}
    for (const [k, v] of Object.entries(fieldValues.value)) {
      if (v) values[Number(k)] = v
    }
    await waitingList.createEntry(listId.value, {
      firstname: firstname.value.trim(),
      lastname: lastname.value.trim(),
      guardians: guardians.value.map(g => ({firstname: g.firstname.trim(), lastname: g.lastname.trim(), email: g.email.trim(), phone: g.phone.trim()})),
      values,
      notes: notes.value.trim(),
    })
    router.push({name: 'waiting-list-detail', params: {id: listId.value}})
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}

function goBack() {
  router.push({name: 'waiting-list-detail', params: {id: listId.value}})
}
</script>

<template>
  <ViewContent
      :title="t('pages.waiting-list-create-entry.title')"
      :subtitle="t('pages.waiting-list-create-entry.subtitle')"
  >
    <div class="space-y-6">
      <SecondaryButton :icon="['fas', 'chevron-left']" @click="goBack">
        {{ t('waitingList.backToList') }}
      </SecondaryButton>

      <SectionHeader>{{ t('waitingList.addEntry') }}</SectionHeader>

      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Spinner v-if="loading" size="lg"/>

      <template v-if="!loading">
        <EntryFormCard
          v-model:firstname="firstname"
          v-model:lastname="lastname"
          v-model:guardians="guardians"
          v-model:notes="notes"
          :fields="fields"
          :field-values="fieldValues"
          :parse-config="parseConfig"
          @add-guardian="addGuardian"
          @remove-guardian="removeGuardian"
          @update-field-value="setFieldValue"
        />

        <div class="flex justify-end gap-2">
          <SecondaryButton @click="goBack">{{ t('common.cancel') }}</SecondaryButton>
          <SaveButton :disabled="!canSave" :action="save">{{ t('waitingList.addEntry') }}</SaveButton>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
