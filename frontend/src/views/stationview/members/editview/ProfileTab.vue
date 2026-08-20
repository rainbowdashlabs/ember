/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Alert from '@/components/feedback/Alert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {ProfileField} from '@/api/profileFields'
import ProfileFieldsLayout from '@/components/profilefields/ProfileFieldsLayout.vue'
import {valueFields} from '@/components/profilefields/fieldLayout'
import type {StationMember} from '@/api/types'
import {profileFields, members, stationMembers} from '@/api'

const {t} = useI18n()

const props = defineProps<{
  member: StationMember
  memberId: number
  fields: ProfileField[]
  initialValues: Map<number, string>
}>()

const editFirstName = ref(props.member.name?.split(' ')[0] ?? '')
const editLastName = ref(props.member.name?.split(' ').slice(1).join(' ') ?? '')
const editEmail = ref(props.member.email ?? '')
const editValues = ref(new Map(props.initialValues))
const editJoinDate = ref(props.member.joinDate ?? '')
const error = ref('')

async function onJoinDateChange(value: string | undefined) {
  if (!value) return
  error.value = ''
  try {
    await stationMembers.setJoinDate(props.memberId, value)
    editJoinDate.value = value
  } catch {
    error.value = t('common.error')
  }
}

function getEditValue(fieldId: number): string {
  return editValues.value.get(fieldId) ?? ''
}

function setEditValue(fieldId: number, val: string) {
  editValues.value = new Map([...editValues.value, [fieldId, val]])
}

async function save() {
  error.value = ''
  try {
    await members.updateAccount(props.member.accountId, {
      email: editEmail.value,
      firstName: editFirstName.value,
      lastName: editLastName.value,
    })
    const entries = valueFields(props.fields).map(f => ({fieldId: f.id, value: JSON.stringify(getEditValue(f.id))}))
    await profileFields.setValues(props.memberId, {values: entries})
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}
</script>

<template>
  <div class="space-y-6">
    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <!-- Base fields -->
    <NeutralContainer class="space-y-4">
      <SubHeader class="text-sm">{{ t('memberEdit.baseFields') }}</SubHeader>
      <div class="grid gap-4 sm:grid-cols-3">
        <div class="space-y-1">
          <FieldLabel hint>{{ t('memberEdit.firstName') }}</FieldLabel>
          <TextInput v-model="editFirstName"/>
        </div>
        <div class="space-y-1">
          <FieldLabel hint>{{ t('memberEdit.lastName') }}</FieldLabel>
          <TextInput v-model="editLastName"/>
        </div>
        <div class="space-y-1">
          <FieldLabel hint>{{ t('memberEdit.email') }}</FieldLabel>
          <TextInput v-model="editEmail"/>
        </div>
      </div>
    </NeutralContainer>

    <!-- Join date -->
    <NeutralContainer class="space-y-3">
      <SubHeader class="text-sm">{{ t('memberEdit.joinDate') }}</SubHeader>
      <DateInput :model-value="editJoinDate" class="max-w-xs" @update:model-value="onJoinDateChange"/>
      <p class="text-xs text-(--text-muted)">{{ t('memberEdit.joinDateHint') }}</p>
    </NeutralContainer>

    <!-- Profile fields -->
    <NeutralContainer v-if="fields.length > 0" class="space-y-4">
      <SubHeader class="text-sm">{{ t('memberEdit.fields') }}</SubHeader>
      <ProfileFieldsLayout
          :fields="fields"
          :get-value="getEditValue"
          can-edit-readonly
          @update="setEditValue"
      />
    </NeutralContainer>

    <!-- Save -->
    <div class="flex items-center">
      <SaveButton :action="save"/>
    </div>
  </div>
</template>
