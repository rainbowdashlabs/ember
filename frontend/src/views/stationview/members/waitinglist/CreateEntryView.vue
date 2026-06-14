/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import type {GuardianInput, WaitingListField} from '@/api/types'
import {waitingList} from '@/api'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()

const listId = computed(() => Number(route.params.id))

const fields = ref<WaitingListField[]>([])
const loading = ref(true)
const firstname = ref('')
const lastname = ref('')
const guardians = ref<GuardianInput[]>([{firstname: '', lastname: '', email: '', phone: ''}])
const notes = ref('')
const fieldValues = ref<Record<number, string>>({})
const saving = ref(false)
const error = ref('')

async function loadFields() {
  loading.value = true
  try {
    fields.value = await waitingList.listFields(listId.value)
  } catch { /* ignore */ } finally {
    loading.value = false
  }
}

function addGuardian() {
  guardians.value = [...guardians.value, {firstname: '', lastname: '', email: '', phone: ''}]
}

function removeGuardian(index: number) {
  guardians.value = guardians.value.filter((_, i) => i !== index)
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
  saving.value = true
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
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push({name: 'waiting-list-detail', params: {id: listId.value}})
}

onMounted(loadFields)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <SecondaryButton :icon="['fas', 'chevron-left']" @click="goBack">
          {{ t('waitingList.backToList') }}
        </SecondaryButton>
      </div>

      <SectionHeader>{{ t('waitingList.addEntry') }}</SectionHeader>

      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Spinner v-if="loading" size="lg"/>

      <template v-if="!loading">
        <NeutralContainer class="space-y-4">
          <div class="grid gap-4 sm:grid-cols-2">
            <div class="space-y-1">
              <FieldLabel>{{ t('waitingList.firstname') }} <span class="text-error">*</span></FieldLabel>
              <TextInput v-model="firstname" :placeholder="t('waitingList.firstnamePlaceholder')"/>
            </div>
            <div class="space-y-1">
              <FieldLabel>{{ t('waitingList.lastname') }}</FieldLabel>
              <TextInput v-model="lastname" :placeholder="t('waitingList.lastnamePlaceholder')"/>
            </div>
          </div>

          <!-- Guardians -->
          <div class="space-y-3">
            <FieldLabel>{{ t('waitingList.guardians') }} <span class="text-error">*</span></FieldLabel>
            <NeutralContainer v-for="(g, i) in guardians" :key="i" class="space-y-2">
              <div class="flex items-center justify-between">
                <span class="text-sm font-medium">{{ t('waitingList.guardian') }} {{ i + 1 }}</span>
                <DeleteButton v-if="guardians.length > 1" @click="removeGuardian(i)"/>
              </div>
              <div class="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
                <TextInput v-model="g.firstname" :placeholder="t('waitingList.firstnamePlaceholder')"/>
                <TextInput v-model="g.lastname" :placeholder="t('waitingList.lastnamePlaceholder')"/>
                <TextInput v-model="g.email" :placeholder="t('waitingList.guardianEmailPlaceholder')"/>
                <TextInput v-model="g.phone" :placeholder="t('waitingList.guardianPhonePlaceholder')"/>
              </div>
            </NeutralContainer>
            <SecondaryButton :icon="['fas', 'plus']" @click="addGuardian">{{ t('waitingList.addGuardian') }}
            </SecondaryButton>
          </div>

          <!-- Custom fields -->
          <template v-if="fields.length > 0">
            <SubHeader>{{ t('waitingList.customFields') }}</SubHeader>
            <div v-for="field in fields" :key="field.id" class="space-y-1">
              <FieldLabel>{{ field.name }}{{ field.required ? ' *' : '' }}</FieldLabel>
              <TextInput v-if="field.fieldType === 'TEXT'" :model-value="fieldValues[field.id] ?? ''"
                         @update:model-value="fieldValues[field.id] = $event"/>
              <NumberInput v-else-if="field.fieldType === 'NUMBER'"
                           :model-value="Number(fieldValues[field.id]) || 0"
                           @update:model-value="fieldValues[field.id] = String($event)"/>
              <DateInput v-else-if="field.fieldType === 'DATE'" :model-value="fieldValues[field.id] ?? ''"
                         @update:model-value="fieldValues[field.id] = $event"/>
              <ToggleInput v-else-if="field.fieldType === 'BOOLEAN'"
                           :model-value="fieldValues[field.id] === 'true'"
                           @update:model-value="fieldValues[field.id] = String($event)"/>
              <SelectInput v-else-if="field.fieldType === 'ENUM'" :model-value="fieldValues[field.id] ?? ''"
                           @update:model-value="fieldValues[field.id] = $event">
                <option value="">{{ t('waitingList.selectOption') }}</option>
                <option v-for="opt in (parseConfig(field.config) as {options?: string[]}).options ?? []" :key="opt"
                        :value="opt">{{ opt }}
                </option>
              </SelectInput>
            </div>
          </template>

          <div class="space-y-1">
            <FieldLabel>{{ t('waitingList.notes') }}</FieldLabel>
            <TextAreaInput v-model="notes" :placeholder="t('waitingList.notesPlaceholder')"/>
          </div>
        </NeutralContainer>

        <div class="flex justify-end gap-2">
          <SecondaryButton @click="goBack">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton :disabled="saving || !canSave" @click="save">
            {{ saving ? t('common.loading') : t('waitingList.addEntry') }}
          </PrimaryButton>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
