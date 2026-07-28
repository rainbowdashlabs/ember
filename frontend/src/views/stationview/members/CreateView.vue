/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import StepDispatcher from './createview/StepDispatcher.vue'
import type {ProfileField} from '@/api/profileFields'
import type {MemberGroup, StationMember} from '@/api/types'
import {parseFieldConfig} from '@/api/profileFields'
import {StationUserType} from '@/api/types'
import {memberGroups, members, profileFields, stationMembers} from '@/api'
import {setFieldValue as writeFieldValue} from '@/util/profileFields'
import {useStations} from '@/composables/useStations'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'

const {t} = useI18n()
const router = useRouter()
const {currentStationId} = useStations()

const step = ref<'userType' | 'identity' | 'fields' | 'groups' | 'manager' | 'done'>('userType')
const selectedUserType = ref<'TRIAL' | 'MEMBER' | 'GUARDIAN' | 'TEAM'>(StationUserType.MEMBER)
const firstName = ref('')
const lastName = ref('')
const email = ref('')
const canLogin = ref(true)
const allFields = ref<ProfileField[]>([])
const fieldValues = ref<Map<number, string>>(new Map())
const allGroups = ref<MemberGroup[]>([])
const selectedGroupIds = ref<Set<number>>(new Set())
const allMembers = ref<StationMember[]>([])
const selectedManagerIds = ref<Set<number>>(new Set())
const createdManagers = ref<Array<{
  id: number;
  memberId: number;
  firstName: string;
  lastName: string;
  email: string
}>>([])

const scopeFields = computed(() => allFields.value.filter(f => f.scope === selectedUserType.value))

const {loading, error} = useAsyncLoader(async () => {
  const [fields, groups, mems] = await Promise.all([
    profileFields.listFields(),
    memberGroups.listGroups(),
    stationMembers.listMembers(),
  ])
  allFields.value = fields
  allGroups.value = groups
  allMembers.value = mems
})

function nextFromIdentity() {
  for (const field of scopeFields.value) {
    const cfg = parseFieldConfig(field.config)
    if (cfg.defaultValue !== undefined && cfg.defaultValue !== null && !fieldValues.value.has(field.id)) {
      if (cfg.defaultValue === '__TODAY__') {
        setFieldValue(field.id, new Date().toISOString().slice(0, 10))
      } else {
        setFieldValue(field.id, String(cfg.defaultValue))
      }
    }
  }
  step.value = 'fields'
}

function setFieldValue(fieldId: number, val: string) {
  writeFieldValue(fieldValues, fieldId, val)
}

function nextFromGroups() {
  if (selectedUserType.value === StationUserType.MEMBER) {
    step.value = 'manager'
  } else {
    createAccount()
  }
}

function toggleGroup(id: number) {
  const newSet = new Set(selectedGroupIds.value)
  if (newSet.has(id)) {
    newSet.delete(id)
  } else {
    newSet.add(id)
  }
  selectedGroupIds.value = newSet
}

function toggleManager(id: number) {
  const newSet = new Set(selectedManagerIds.value)
  if (newSet.has(id)) {
    newSet.delete(id)
  } else {
    newSet.add(id)
  }
  selectedManagerIds.value = newSet
}

async function createNewManager(data: { firstName: string; lastName: string; email: string }) {
  error.value = ''
  try {
    const invited = await members.invite(data)
    const membersList = await stationMembers.listMembers()
    const newMember = membersList.find(m => m.accountId === invited.id)
    if (newMember) {
      createdManagers.value = [...createdManagers.value, {
        id: invited.id,
        memberId: newMember.id,
        firstName: data.firstName,
        lastName: data.lastName,
        email: data.email,
      }]
      selectedManagerIds.value = new Set([...selectedManagerIds.value, newMember.id])
      allMembers.value = membersList
    }
  } catch {
    error.value = t('common.error')
  }
}

const {running: saving, error: createError, run: createAccount, clearError: clearCreateError} = useAsyncAction(async () => {
  error.value = ''
  const inviteEmail = canLogin.value
      ? email.value
      : `${firstName.value.toLowerCase()}.${lastName.value.toLowerCase()}@${currentStationId.value}.local`
  const invited = await members.invite({
    email: inviteEmail,
    firstName: firstName.value,
    lastName: lastName.value,
  })

  const membersList = await stationMembers.listMembers()
  const newMember = membersList.find(m => m.accountId === invited.id)
  if (!newMember) throw new Error('Member not found after invite')

  if (selectedUserType.value !== StationUserType.MEMBER) {
    await stationMembers.setUserType(newMember.id, selectedUserType.value)
  }

  const entries = [...fieldValues.value.entries()]
      .filter(([_, val]) => val.trim())
      .map(([fieldId, value]) => ({fieldId, value: JSON.stringify(value)}))
  if (entries.length > 0) {
    await profileFields.setValues(newMember.id, {values: entries})
  }

  for (const groupId of selectedGroupIds.value) {
    const currentMembers = await memberGroups.getGroupMembers(groupId)
    const memberIds = [...currentMembers.map(m => m.id), newMember.id]
    await memberGroups.setGroupMembers(groupId, {memberIds})
  }

  if (selectedUserType.value === StationUserType.MEMBER && selectedManagerIds.value.size > 0) {
    await stationMembers.setManagers(newMember.id, {managerIds: [...selectedManagerIds.value]})
  }

  step.value = 'done'
}, {formatError: () => t('common.error')})

function startOver() {
  step.value = 'userType'
  selectedUserType.value = StationUserType.MEMBER
  firstName.value = ''
  lastName.value = ''
  email.value = ''
  canLogin.value = true
  fieldValues.value = new Map()
  selectedGroupIds.value = new Set()
  selectedManagerIds.value = new Set()
  createdManagers.value = []
  error.value = ''
  clearCreateError()
}

</script>

<template>
  <ViewContent
      :title="t('pages.members-create.title')"
      :subtitle="t('pages.members-create.subtitle')"
  >
    <div class="space-y-6">
      <div class="flex justify-end">
        <SecondaryButton :icon="['fas', 'upload']" @click="router.push({ name: 'members-import' })">
          {{ t('memberImport.linkFromCreate') }}
        </SecondaryButton>
      </div>
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error || createError" variant="error">{{ error || createError }}</Alert>

      <StepDispatcher
          v-if="!loading"
          v-model:step="step"
          v-model:selected-user-type="selectedUserType"
          v-model:can-login="canLogin"
          v-model:email="email"
          v-model:first-name="firstName"
          v-model:last-name="lastName"
          :scope-fields="scopeFields"
          :field-values="fieldValues"
          :all-groups="allGroups"
          :selected-group-ids="selectedGroupIds"
          :all-members="allMembers"
          :selected-manager-ids="selectedManagerIds"
          :created-managers="createdManagers"
          :saving="saving"
          @next-from-identity="nextFromIdentity"
          @next-from-groups="nextFromGroups"
          @set-field-value="setFieldValue"
          @toggle-group="toggleGroup"
          @toggle-manager="toggleManager"
          @create-manager="createNewManager"
          @create-account="createAccount"
          @start-over="startOver"
          @to-list="router.push({ name: 'members-list' })"
      />
    </div>
  </ViewContent>
</template>
