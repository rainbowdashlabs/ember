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
import ProfileFieldsLayout, {type LaidOutField} from '@/components/profilefields/ProfileFieldsLayout.vue'
import {valueFields} from '@/components/profilefields/fieldLayout'
import {profileKey, type MergedProfileField} from '@/util/profileFields'
import type {StationMember} from '@/api/types'
import {profileFields, members, stationMembers} from '@/api'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const {sessionInfo} = useSession()

const props = defineProps<{
  member: StationMember
  memberId: number
  fields: MergedProfileField[]
  initialValues: Map<string, string>
}>()

const editFirstName = ref(props.member.name?.split(' ')[0] ?? '')
const editLastName = ref(props.member.name?.split(' ').slice(1).join(' ') ?? '')
const editEmail = ref(props.member.email ?? '')
const editUsername = ref(props.member.username ?? '')
const editValues = ref(new Map(props.initialValues))
const editJoinDate = ref(props.member.joinDate ?? '')
const error = ref('')
const notice = ref('')

/**
 * Somebody putting their own address right confirms it from both ends before it takes effect, so
 * saying "saved" would be a lie on this one screen. Doing it for somebody else takes effect at once.
 */
function ownAccount(): boolean {
  return sessionInfo.value?.account?.id === props.member.accountId
}

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

function getEditValue(field: LaidOutField): string {
  return editValues.value.get(profileKey(field.id, field.origin ?? 'STATION')) ?? ''
}

function setEditValue(field: LaidOutField, val: string) {
  const key = profileKey(field.id, field.origin ?? 'STATION')
  editValues.value = new Map([...editValues.value, [key, val]])
}

async function save() {
  error.value = ''
  notice.value = ''
  const addressChanged = editEmail.value.trim().toLowerCase() !== (props.member.email ?? '').toLowerCase()
  try {
    await members.updateAccount(props.member.accountId, {
      email: editEmail.value,
      username: editUsername.value,
      firstName: editFirstName.value,
      lastName: editLastName.value,
    })
    // A field the cluster keeps to itself has no control on this screen, so sending it back would send
    // whatever was read rather than anything anybody typed
    const entries = valueFields(props.fields)
        .filter(f => !(f as MergedProfileField).readonlyAtStation)
        .map(f => ({
          fieldId: f.id,
          value: JSON.stringify(getEditValue(f)),
          origin: (f as MergedProfileField).origin,
        }))
    await profileFields.setValues(props.memberId, {values: entries})
    if (addressChanged && ownAccount()) notice.value = t('memberEdit.emailConfirmationPending')
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}
</script>

<template>
  <div class="space-y-6">
    <Alert v-if="error" variant="error">{{ error }}</Alert>
    <Alert v-if="notice" variant="info">{{ notice }}</Alert>

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
          <p v-if="!ownAccount()" class="text-xs text-(--text-muted)">{{ t('memberEdit.emailHint') }}</p>
        </div>
      </div>
      <div class="space-y-1">
        <FieldLabel hint>{{ t('memberEdit.username') }}</FieldLabel>
        <TextInput v-model="editUsername" :placeholder="editEmail"/>
        <p class="text-xs text-(--text-muted)">{{ t('memberEdit.usernameHint') }}</p>
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
