/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type { WaitingListInviteInfo } from '@/api/types'
import { waitingList } from '@/api'

const { t } = useI18n()
const route = useRoute()

const code = ref('')
const inviteInfo = ref<WaitingListInviteInfo | null>(null)
const loading = ref(true)
const error = ref('')
const submitting = ref(false)
const submitted = ref(false)
const accessToken = ref('')

// Form fields
const firstname = ref('')
const lastname = ref('')
const parentName = ref('')
const email = ref('')
const notes = ref('')
const fieldValues = ref<Map<number, string>>(new Map())

function getFieldValue(fieldId: number): string {
  return fieldValues.value.get(fieldId) ?? ''
}

function setFieldValue(fieldId: number, value: string | undefined) {
  const newMap = new Map(fieldValues.value)
  newMap.set(fieldId, value ?? '')
  fieldValues.value = newMap
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

async function loadInviteInfo() {
  code.value = (route.query.code as string) ?? ''
  if (!code.value) {
    error.value = t('waitingList.register.noCode')
    loading.value = false
    return
  }
  try {
    inviteInfo.value = await waitingList.getInviteInfo(code.value)
  } catch {
    error.value = t('waitingList.register.invalidCode')
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!firstname.value.trim() || !email.value.trim()) {
    error.value = t('waitingList.register.requiredFields')
    return
  }

  // Validate required custom fields
  if (inviteInfo.value) {
    for (const field of inviteInfo.value.fields) {
      if (field.required && !getFieldValue(field.id).trim()) {
        error.value = t('waitingList.register.requiredField', { name: field.name })
        return
      }
    }
  }

  submitting.value = true
  error.value = ''
  try {
    const values: Record<number, string> = {}
    for (const [fieldId, value] of fieldValues.value) {
      if (value.trim()) {
        values[fieldId] = value.trim()
      }
    }
    const result = await waitingList.register({
      inviteCode: code.value,
      firstname: firstname.value.trim(),
      lastname: lastname.value.trim(),
      parentName: parentName.value.trim(),
      email: email.value.trim(),
      notes: notes.value.trim(),
      values,
    })
    accessToken.value = result.accessToken
    submitted.value = true
  } catch {
    error.value = t('common.error')
  } finally {
    submitting.value = false
  }
}

function statusLink(): string {
  return `${window.location.origin}/waiting-list/status?token=${accessToken.value}`
}

async function copyStatusLink() {
  await navigator.clipboard.writeText(statusLink())
}

onMounted(loadInviteInfo)
</script>

<template>
  <div class="flex items-center justify-center px-4 py-12">
    <div class="w-full max-w-lg space-y-6">
      <div class="text-center">
        <font-awesome-icon :icon="['fas', 'clipboard-list']" class="text-4xl text-primary mb-3" />
        <PageHeader class="text-2xl font-bold">{{ t('waitingList.register.title') }}</PageHeader>
      </div>

      <Spinner v-if="loading" size="md" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <!-- Invite info header -->
      <template v-if="!loading && inviteInfo && !submitted">
        <NeutralContainer class="text-center space-y-2">
          <SectionHeader class="text-lg font-semibold">{{ inviteInfo.listName }}</SectionHeader>
          <p v-if="inviteInfo.listDescription" class="text-sm text-(--text-muted)">{{ inviteInfo.listDescription }}</p>
        </NeutralContainer>

        <form class="space-y-4" @submit.prevent="submit">
          <div class="space-y-1">
            <FieldLabel>{{ t('waitingList.firstname') }} <span class="text-error">*</span></FieldLabel>
            <TextInput v-model="firstname" :placeholder="t('waitingList.register.firstnamePlaceholder')" />
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('waitingList.lastname') }}</FieldLabel>
            <TextInput v-model="lastname" :placeholder="t('waitingList.register.lastnamePlaceholder')" />
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('waitingList.parentName') }}</FieldLabel>
            <TextInput v-model="parentName" :placeholder="t('waitingList.register.parentNamePlaceholder')" />
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('waitingList.email') }} <span class="text-error">*</span></FieldLabel>
            <TextInput v-model="email" :placeholder="t('waitingList.register.emailPlaceholder')" />
          </div>

          <!-- Dynamic fields -->
          <div v-for="field in inviteInfo.fields" :key="field.id" class="space-y-1">
            <FieldLabel>
              {{ field.name }}
              <span v-if="field.required" class="text-error">*</span>
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
            <div v-else-if="field.fieldType === 'BOOLEAN'" class="flex items-center gap-2 pt-1">
              <ToggleInput
                :model-value="getFieldValueAsBoolean(field.id)"
                @update:model-value="setFieldValueFromBoolean(field.id, $event)"
              />
            </div>
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

          <div class="space-y-1">
            <FieldLabel>{{ t('waitingList.notes') }}</FieldLabel>
            <TextAreaInput v-model="notes" :placeholder="t('waitingList.register.notesPlaceholder')" />
          </div>

          <PrimaryButton :disabled="submitting" class="w-full" type="submit">
            {{ submitting ? t('common.loading') : t('waitingList.register.submit') }}
          </PrimaryButton>
        </form>
      </template>

      <!-- Success state -->
      <template v-if="submitted">
        <Alert variant="success">{{ t('waitingList.register.success') }}</Alert>

        <NeutralContainer class="space-y-3">
          <p class="text-sm font-medium">{{ t('waitingList.register.saveLink') }}</p>
          <p class="text-xs text-(--text-muted)">{{ t('waitingList.register.saveLinkHint') }}</p>
          <div class="flex items-center gap-2">
            <code class="flex-1 text-xs font-mono bg-bg-light-accent dark:bg-bg-dark-accent px-3 py-2 rounded break-all select-all">{{ statusLink() }}</code>
            <PrimaryButton @click="copyStatusLink">
              <font-awesome-icon :icon="['fas', 'copy']" />
            </PrimaryButton>
          </div>
        </NeutralContainer>
      </template>

      <!-- Invalid code state -->
      <template v-if="!loading && !inviteInfo && !submitted">
        <div class="text-center">
          <router-link class="text-sm text-primary hover:underline" to="/login">{{ t('waitingList.register.backToLogin') }}</router-link>
        </div>
      </template>
    </div>
  </div>
</template>
