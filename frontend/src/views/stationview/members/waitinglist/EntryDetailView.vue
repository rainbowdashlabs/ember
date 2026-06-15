/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import type { GuardianInput, WaitingListEntryWithScore, WaitingListField } from '@/api/types'
import { waitingList } from '@/api'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const listId = computed(() => Number(route.params.id))
const entryId = computed(() => Number(route.params.entryId))

const entry = ref<WaitingListEntryWithScore | null>(null)
const fields = ref<WaitingListField[]>([])
const loading = ref(true)
const error = ref('')

// Edit form
const editFirstname = ref('')
const editLastname = ref('')
const editGuardians = ref<GuardianInput[]>([])
const editNotes = ref('')
const editValues = ref<Map<number, string>>(new Map())
const editingCreatedAt = ref(false)
const editCreatedAtValue = ref('')

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [entries, fieldData] = await Promise.all([
      waitingList.listEntries(listId.value),
      waitingList.listFields(listId.value),
    ])
    const found = entries.find(e => e.entry.id === entryId.value)
    if (!found) {
      error.value = t('waitingList.entryNotFound')
      loading.value = false
      return
    }
    entry.value = found
    fields.value = fieldData

    editFirstname.value = found.entry.firstname
    editLastname.value = found.entry.lastname
    editGuardians.value = found.guardians.length > 0
      ? found.guardians.map(g => ({ firstname: g.firstname, lastname: g.lastname, email: g.email, phone: g.phone }))
      : [{ firstname: '', lastname: '', email: found.entry.email || '', phone: '' }]
    editNotes.value = found.entry.notes ?? ''

    const valMap = new Map<number, string>()
    for (const v of found.values) {
      valMap.set(v.fieldId, v.value == null ? '' : String(v.value))
    }
    editValues.value = valMap
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function getFieldValue(fieldId: number): string {
  return editValues.value.get(fieldId) ?? ''
}

function setFieldValue(fieldId: number, value: string | undefined) {
  const newMap = new Map(editValues.value)
  newMap.set(fieldId, value ?? '')
  editValues.value = newMap
}

function getFieldValueAsNumber(fieldId: number): number {
  const val = getFieldValue(fieldId)
  return val ? Number(val) : 0
}

function setFieldValueFromNumber(fieldId: number, value: number | undefined) {
  setFieldValue(fieldId, String(value ?? 0))
}

function getFieldValueAsBoolean(fieldId: number): boolean {
  return getFieldValue(fieldId) === 'true'
}

function setFieldValueFromBoolean(fieldId: number, value: boolean) {
  setFieldValue(fieldId, String(value))
}

function parseConfig(configStr: string | undefined | null): Record<string, unknown> {
  if (!configStr) return {}
  try { return JSON.parse(configStr) } catch { return {} }
}

function addGuardian() {
  editGuardians.value = [...editGuardians.value, { firstname: '', lastname: '', email: '', phone: '' }]
}

function removeGuardian(index: number) {
  editGuardians.value = editGuardians.value.filter((_, i) => i !== index)
}

const canSave = computed(() =>
  editFirstname.value.trim() && editGuardians.value.some(g => g.email.trim()),
)

async function save() {
  if (!canSave.value) return
  error.value = ''
  try {
    const values: Record<number, unknown> = {}
    for (const [fieldId, value] of editValues.value) {
      values[fieldId] = value
    }
    await waitingList.updateEntry(listId.value, entryId.value, {
      firstname: editFirstname.value.trim(),
      lastname: editLastname.value.trim(),
      guardians: editGuardians.value.map(g => ({ firstname: g.firstname.trim(), lastname: g.lastname.trim(), email: g.email.trim(), phone: g.phone.trim() })),
      notes: editNotes.value.trim(),
      values,
    })
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}

function startEditCreatedAt() {
  if (!entry.value) return
  // Convert to local datetime-local format (YYYY-MM-DDTHH:mm)
  const d = new Date(entry.value.entry.createdAt)
  editCreatedAtValue.value = d.toISOString().slice(0, 16)
  editingCreatedAt.value = true
}

async function saveCreatedAt() {
  if (!entry.value || !editCreatedAtValue.value) return
  try {
    await waitingList.updateCreatedAt(listId.value, entryId.value, new Date(editCreatedAtValue.value).toISOString())
    await loadData()
    editingCreatedAt.value = false
  } catch {
    error.value = t('common.error')
  }
}

function statusBadgeComponent(status: string) {
  if (status === 'JOINED') return SuccessBadge
  if (status === 'WITHDRAWN') return ErrorBadge
  if (status === 'TESTING') return PrimaryBadge
  if (status === 'INVITED') return InfoBadge
  return SecondaryBadge
}

function formatDateTime(dateStr: string | undefined | null): string {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString()
}

function entryFullName(): string {
  if (!entry.value) return ''
  const e = entry.value.entry
  return e.lastname ? `${e.firstname} ${e.lastname}` : e.firstname
}

function goBack() {
  router.push({ name: 'waiting-list-detail', params: { id: listId.value } })
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <SecondaryButton :icon="['fas', 'chevron-left']" @click="goBack">
          {{ t('waitingList.backToList') }}
        </SecondaryButton>
      </div>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && entry">
        <div class="flex items-center gap-2">
          <SectionHeader>{{ entryFullName() }}</SectionHeader>
          <component :is="statusBadgeComponent(entry.entry.status)">{{ t('waitingList.status_' + entry.entry.status) }}</component>
        </div>

        <div class="text-sm text-(--text-muted) flex flex-wrap gap-4">
          <span>{{ t('waitingList.score') }}: <span class="font-mono font-medium">{{ entry.score }}</span></span>
          <span>{{ t('waitingList.confirmedAt') }}: {{ formatDateTime(entry.entry.confirmedAt) }}</span>
          <span class="inline-flex items-center gap-1">
            {{ t('waitingList.createdAt') }}:
            <template v-if="editingCreatedAt">
              <DateTimeInput v-model="editCreatedAtValue" />
              <IconButton :icon="['fas', 'check']" label="Speichern" @click="saveCreatedAt" />
              <IconButton :icon="['fas', 'xmark']" label="Abbrechen" @click="editingCreatedAt = false" />
            </template>
            <template v-else>
              {{ formatDateTime(entry.entry.createdAt) }}
              <EditButton :label="t('common.edit')" @click="startEditCreatedAt" />
            </template>
          </span>
          <span v-if="entry.entry.invitedAt">{{ t('waitingList.invitedAt') }}: {{ formatDateTime(entry.entry.invitedAt) }}</span>
          <span v-if="entry.entry.testingAt">{{ t('waitingList.testingAt') }}: {{ formatDateTime(entry.entry.testingAt) }}</span>
          <span v-if="entry.entry.joinedAt">{{ t('waitingList.joinedAt') }}: {{ formatDateTime(entry.entry.joinedAt) }}</span>
          <span v-if="entry.entry.withdrawnAt">{{ t('waitingList.withdrawnAt') }}: {{ formatDateTime(entry.entry.withdrawnAt) }}</span>
          <span v-if="entry.entry.status === 'TESTING'">{{ t('waitingList.attendanceCount') }}: <span class="font-mono font-medium">{{ entry.entry.attendanceCount }}</span></span>
        </div>

        <!-- Core fields -->
        <NeutralContainer class="space-y-4">
          <SubHeader>{{ t('waitingList.entryDetails') }}</SubHeader>
          <div class="grid gap-4 sm:grid-cols-2">
            <div class="space-y-1">
              <FieldLabel>{{ t('waitingList.firstname') }}</FieldLabel>
              <TextInput v-model="editFirstname" />
            </div>
            <div class="space-y-1">
              <FieldLabel>{{ t('waitingList.lastname') }}</FieldLabel>
              <TextInput v-model="editLastname" />
            </div>
          </div>
          <div class="space-y-3">
            <FieldLabel>{{ t('waitingList.guardians') }}</FieldLabel>
            <NeutralContainer v-for="(g, i) in editGuardians" :key="i" class="space-y-2">
              <div class="flex items-center justify-between">
                <span class="text-sm font-medium">{{ t('waitingList.guardian') }} {{ i + 1 }}</span>
                <DeleteButton v-if="editGuardians.length > 1" @click="removeGuardian(i)" />
              </div>
              <div class="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
                <TextInput v-model="g.firstname" :placeholder="t('waitingList.firstnamePlaceholder')" />
                <TextInput v-model="g.lastname" :placeholder="t('waitingList.lastnamePlaceholder')" />
                <TextInput v-model="g.email" :placeholder="t('waitingList.guardianEmailPlaceholder')" />
                <TextInput v-model="g.phone" :placeholder="t('waitingList.guardianPhonePlaceholder')" />
              </div>
            </NeutralContainer>
            <SecondaryButton :icon="['fas', 'plus']" @click="addGuardian">{{ t('waitingList.addGuardian') }}</SecondaryButton>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('waitingList.notes') }}</FieldLabel>
            <TextAreaInput v-model="editNotes" />
          </div>
        </NeutralContainer>

        <!-- Custom fields -->
        <NeutralContainer v-if="fields.length > 0" class="space-y-4">
          <SubHeader>{{ t('waitingList.customFields') }}</SubHeader>
          <div class="grid gap-4 sm:grid-cols-2">
            <div v-for="field in fields" :key="field.id" class="space-y-1">
              <FieldLabel>
                {{ field.name }}
                <span v-if="field.required" class="text-error text-xs ml-1">*</span>
              </FieldLabel>

              <TextInput
                v-if="field.fieldType === 'TEXT'"
                :model-value="getFieldValue(field.id)"
                @update:model-value="setFieldValue(field.id, $event)"
              />
              <NumberInput
                v-else-if="field.fieldType === 'NUMBER'"
                :model-value="getFieldValueAsNumber(field.id)"
                @update:model-value="setFieldValueFromNumber(field.id, $event)"
              />
              <DateInput
                v-else-if="field.fieldType === 'DATE'"
                :model-value="getFieldValue(field.id)"
                @update:model-value="setFieldValue(field.id, $event)"
              />
              <ToggleInput
                v-else-if="field.fieldType === 'BOOLEAN'"
                :model-value="getFieldValueAsBoolean(field.id)"
                @update:model-value="setFieldValueFromBoolean(field.id, $event)"
              />
              <SelectInput
                v-else-if="field.fieldType === 'ENUM'"
                :model-value="getFieldValue(field.id)"
                @update:model-value="setFieldValue(field.id, $event)"
              >
                <option value="" disabled>{{ t('waitingList.selectOption') }}</option>
                <option
                  v-for="opt in (parseConfig(field.config).options as string[] ?? [])"
                  :key="opt"
                  :value="opt"
                >{{ opt }}</option>
              </SelectInput>
            </div>
          </div>
        </NeutralContainer>

        <div class="flex justify-end">
          <SaveButton :disabled="!canSave" :action="save"/>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
