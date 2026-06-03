/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import RoleStep from './createview/RoleStep.vue'
import IdentityStep from './createview/IdentityStep.vue'
import FieldsStep from './createview/FieldsStep.vue'
import GroupsStep from './createview/GroupsStep.vue'
import ManagerStep from './createview/ManagerStep.vue'
import DoneStep from './createview/DoneStep.vue'
import type {MemberGroup, ProfileField, StationMember} from '@/api/types'
import {StationUserType, parseFieldConfig} from '@/api/types'
import {memberGroups, members, profileFields, stationMembers} from '@/api'
import {useStations} from '@/composables/useStations'

const {t} = useI18n()
const router = useRouter()
const {currentStationId} = useStations()

const step = ref<'role' | 'identity' | 'fields' | 'groups' | 'manager' | 'done'>('role')
const selectedRole = ref<'MEMBER' | 'GUARDIAN' | 'TEAM'>(StationUserType.MEMBER)
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
const loading = ref(true)
const saving = ref(false)
const error = ref('')

const scopeFields = computed(() => allFields.value.filter(f => f.scope === selectedRole.value))

async function loadData() {
  loading.value = true
  try {
    const [fields, groups, mems] = await Promise.all([
      profileFields.listFields(),
      memberGroups.listGroups(),
      stationMembers.listMembers(),
    ])
    allFields.value = fields
    allGroups.value = groups
    allMembers.value = mems
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function nextFromIdentity() {
  for (const field of scopeFields.value) {
    const cfg = parseFieldConfig(field.config)
    if (cfg.defaultValue !== undefined && !fieldValues.value.has(field.id)) {
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
  const newMap = new Map(fieldValues.value)
  newMap.set(fieldId, val)
  fieldValues.value = newMap
}

function nextFromGroups() {
  if (selectedRole.value === StationUserType.MEMBER) {
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

async function createAccount() {
  saving.value = true
  error.value = ''
  try {
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

    // Set user type for the new member — backend handles this via the invite endpoint
    // The user type is determined by the selected role on account creation
    await stationMembers.setPermissions(newMember.id, {permissionIds: []})  // permissions handled separately

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

    if (selectedRole.value === StationUserType.MEMBER && selectedManagerIds.value.size > 0) {
      await stationMembers.setManagers(newMember.id, {managerIds: [...selectedManagerIds.value]})
    }

    step.value = 'done'
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}

function startOver() {
  step.value = 'role'
  selectedRole.value = StationUserType.MEMBER
  firstName.value = ''
  lastName.value = ''
  email.value = ''
  canLogin.value = true
  fieldValues.value = new Map()
  selectedGroupIds.value = new Set()
  selectedManagerIds.value = new Set()
  createdManagers.value = []
  error.value = ''
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex justify-end">
        <SecondaryButton :icon="['fas', 'upload']" @click="router.push({ name: 'members-import' })">
          {{ t('memberImport.linkFromCreate') }}
        </SecondaryButton>
      </div>
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <RoleStep
            v-if="step === 'role'"
            v-model="selectedRole"
            @next="step = 'identity'"
        />

        <IdentityStep
            v-if="step === 'identity'"
            v-model:can-login="canLogin"
            v-model:email="email"
            v-model:first-name="firstName"
            v-model:last-name="lastName"
            @back="step = 'role'"
            @next="nextFromIdentity"
        />

        <FieldsStep
            v-if="step === 'fields'"
            :fields="scopeFields"
            :values="fieldValues"
            @back="step = 'identity'"
            @next="step = 'groups'"
            @set-value="setFieldValue"
        />

        <GroupsStep
            v-if="step === 'groups'"
            :groups="allGroups"
            :selected-ids="selectedGroupIds"
            :submit-label="selectedRole === StationUserType.MEMBER ? t('membersCreate.next') : t('membersCreate.create')"
            @back="step = 'fields'"
            @next="nextFromGroups"
            @toggle="toggleGroup"
        />

        <ManagerStep
            v-if="step === 'manager'"
            :created-managers="createdManagers"
            :members="allMembers"
            :saving="saving"
            :selected-ids="selectedManagerIds"
            @back="step = 'groups'"
            @next="createAccount"
            @toggle-manager="toggleManager"
            @create-manager="createNewManager"
        />

        <DoneStep
            v-if="step === 'done'"
            @create-another="startOver"
            @to-list="router.push({ name: 'members-list' })"
        />
      </template>
    </div>
  </ViewContent>
</template>
