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
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import StepDispatcher from './createview/StepDispatcher.vue'
import {parseFieldConfig, type ProfileField} from '@/api/profileFields'
import {StationUserType, type MemberGroup, type StationMember} from '@/api/types'
import {memberGroups, members, profileFields, stationMembers} from '@/api'
import {setFieldValue as writeFieldValue} from '@/util/profileFields'
import {todayIsoDate} from '@/util/format'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useFieldAudiences} from '@/composables/useFieldAudiences'
import {describeFailure, FailureKind} from '@/util/failure'

const {t} = useI18n()
const router = useRouter()

const step = ref<'userType' | 'identity' | 'fields' | 'groups' | 'manager' | 'done'>('userType')
const selectedUserType = ref<'TRIAL' | 'MEMBER' | 'GUARDIAN' | 'TEAM'>(StationUserType.MEMBER)
const firstName = ref('')
const lastName = ref('')
const email = ref('')
const canLogin = ref(true)
const sendSetupMail = ref(true)
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

const audiences = useFieldAudiences()

/** The questions this kind of member is asked, on the form they will be asked them. */
const scopeFields = computed(() => audiences.fieldsFor(allFields.value, selectedUserType.value))

const {loading, failure} = useAsyncLoader(async () => {
  const [fields, groups, mems] = await Promise.all([
    profileFields.listFields(),
    memberGroups.listGroups(),
    stationMembers.listMembers(),
    audiences.load(),
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
        setFieldValue(field.id, todayIsoDate())
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

function setManagers(ids: number[]) {
  selectedManagerIds.value = new Set(ids)
}

/**
 * A guardian entered beside the member they will look after, who is made a guardian rather than an
 * ordinary member: the member kind is what carries the right to sign in, and looking after somebody
 * is done by signing in. The person being created in the wizard's own steps is unaffected and keeps
 * whatever kind was chosen for them.
 */
async function createNewManager(data: { firstName: string; lastName: string; email: string }) {
  failure.value = null

  let invitedId: number
  try {
    invitedId = (await members.invite({...data, sendSetupMail: sendSetupMail.value})).id
  } catch (e) {
    failure.value = describeFailure(e, t)
    return
  }

  try {
    const membersList = await stationMembers.listMembers()
    const newMember = membersList.find(m => m.accountId === invitedId)
    if (!newMember) {
      failure.value = {
        kind: FailureKind.UNKNOWN,
        message: t('memberDetail.invitedButNotLinked'),
        guidance: t('memberDetail.invitedButNotLinkedGuidance'),
        reportable: true,
      }
      return
    }
    await stationMembers.setUserType(newMember.id, StationUserType.GUARDIAN)
    createdManagers.value = [...createdManagers.value, {
      id: invitedId,
      memberId: newMember.id,
      firstName: data.firstName,
      lastName: data.lastName,
      email: data.email,
    }]
    selectedManagerIds.value = new Set([...selectedManagerIds.value, newMember.id])
    allMembers.value = membersList
  } catch (e) {
    failure.value = {...describeFailure(e, t), message: t('memberDetail.invitedButNotLinked')}
  }
}

/** Whether the account is already in, which is what decides how a later failure has to be worded. */
let accountMade = false

/**
 * Creates the member from everything the wizard collected, which is one button and several writes.
 *
 * <p>The account comes first, and once it is there the rest, the kind, the answers, the groups and the
 * guardians, is added to a person who already exists. A reader told plainly that creating failed starts
 * the wizard again and is refused for an address that is now taken, with a half-filled member left in
 * the roll and nothing said about it. Where the account is in, the screen says so and sends them to the
 * member's own page to finish it.
 */
const {running: saving, failure: createFailure, run: createAccount, clearError: clearCreateError} = useAsyncAction(async () => {
  failure.value = null
  accountMade = false
  const invited = await members.invite({
    email: canLogin.value ? email.value : undefined,
    firstName: firstName.value,
    lastName: lastName.value,
    sendSetupMail: sendSetupMail.value,
  })

  accountMade = true

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
}, {
  formatError: e => (accountMade
      ? t('membersCreate.createdButIncomplete')
      : describeFailure(e, t).message),
})

function startOver() {
  step.value = 'userType'
  selectedUserType.value = StationUserType.MEMBER
  firstName.value = ''
  lastName.value = ''
  email.value = ''
  canLogin.value = true
  sendSetupMail.value = true
  fieldValues.value = new Map()
  selectedGroupIds.value = new Set()
  selectedManagerIds.value = new Set()
  createdManagers.value = []
  failure.value = null
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
      <FailureAlert :failure="failure ?? createFailure"/>

      <StepDispatcher
          v-if="!loading"
          v-model:step="step"
          v-model:selected-user-type="selectedUserType"
          v-model:can-login="canLogin"
          v-model:send-setup-mail="sendSetupMail"
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
          @set-managers="setManagers"
          @create-manager="createNewManager"
          @create-account="createAccount"
          @start-over="startOver"
          @to-list="router.push({ name: 'members-list' })"
      />
    </div>
  </ViewContent>
</template>
