/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ProfileFieldInput from '@/components/input/ProfileFieldInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type { ProfileField } from '@/api/types'
import { parseFieldConfig } from '@/api/types'
import { managedMembers } from '@/api'
import type { ManagedMember } from '@/api/managedMembers'
import { decodeProfileValues, getFieldValue, setFieldValue } from '@/util/profileFields'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import MutedText from '@/components/typography/MutedText.vue'

const { t } = useI18n()

const members = ref<ManagedMember[]>([])
const fields = ref<ProfileField[]>([])
const selectedMemberId = ref<string>('')
const values = ref<Map<number, string>>(new Map())
const loadingProfile = ref(false)

function memberDisplayName(member: ManagedMember): string {
  if (member.name && member.name.trim()) return member.name
  return member.email ?? `#${member.id}`
}

const editableFields = computed(() => {
  return fields.value.filter(f => {
    const config = parseFieldConfig(f.config)
    return !config.computed
  })
})

function isReadonly(field: ProfileField): boolean {
  const config = parseFieldConfig(field.config)
  return !!config.readonly || !!config.computed
}

function getValue(fieldId: number): string {
  return getFieldValue(values, fieldId)
}

function setValue(fieldId: number, val: string) {
  setFieldValue(values, fieldId, val)
}

const { loading, error } = useAsyncLoader(async () => {
  members.value = await managedMembers.listManaged()
})

async function loadMemberProfile() {
  if (!selectedMemberId.value) return
  loadingProfile.value = true
  error.value = ''
  try {
    const memberId = Number(selectedMemberId.value)
    const profile = await managedMembers.getProfile(memberId)
    fields.value = profile.fields
    values.value = decodeProfileValues(profile.values)
  } catch {
    error.value = t('common.error')
  } finally {
    loadingProfile.value = false
  }
}

async function saveProfile() {
  if (!selectedMemberId.value) return
  error.value = ''
  try {
    const entries = editableFields.value
      .filter(f => !isReadonly(f))
      .map(f => ({ fieldId: f.id, value: JSON.stringify(getValue(f.id)) }))
    await managedMembers.setProfile(Number(selectedMemberId.value), entries)
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}
</script>

<template>
  <ViewContent
      :title="t('pages.profile-managed.title')"
      :subtitle="t('pages.profile-managed.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <NeutralContainer class="space-y-4">
          <SectionHeader>{{ t('profileManaged.title') }}</SectionHeader>

          <EmptyState compact v-if="members.length === 0">{{ t('profileManaged.noManaged') }}</EmptyState>

          <div v-else class="space-y-1">
            <FieldLabel>{{ t('profileManaged.selectMember') }}</FieldLabel>
            <SelectInput v-model="selectedMemberId" class="w-full" @update:model-value="loadMemberProfile">
              <option value="" disabled>{{ t('profileManaged.selectMemberPlaceholder') }}</option>
              <option v-for="member in members" :key="member.id" :value="String(member.id)">
                {{ memberDisplayName(member) }}
              </option>
            </SelectInput>
          </div>
        </NeutralContainer>

        <Spinner v-if="loadingProfile" size="md" />

        <NeutralContainer v-if="selectedMemberId && !loadingProfile && editableFields.length > 0" class="space-y-4">
          <SectionHeader>{{ t('profileManaged.fields') }}</SectionHeader>

          <div v-for="field in editableFields" :key="field.id" class="space-y-1">
            <FieldLabel>
              {{ field.name }}
              <span v-if="parseFieldConfig(field.config).required" class="text-error">*</span>
              <MutedText class="ml-1" v-if="isReadonly(field)">({{ t('profile.readonlyHint') }})</MutedText>
            </FieldLabel>

            <ProfileFieldInput
              :field-type="field.fieldType ?? 'TEXT'"
              :model-value="getValue(field.id)"
              :options="(parseFieldConfig(field.config).options as string[]) ?? []"
              :disabled="isReadonly(field)"
              @update:model-value="setValue(field.id, $event)"
            />
          </div>

          <SaveButton :action="saveProfile"/>
        </NeutralContainer>
      </template>
    </div>
  </ViewContent>
</template>
