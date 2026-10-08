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
import {parseFieldConfig} from '@/api/profileFields'
import {
  StationPermission,
  StationUserType,
  type IssuedOneTimePassword,
  type MemberGroup,
  type MemberWithName,
  type ProfileField,
} from '@/api/generated/schema'
import {memberGroups, members, profileFields, stationMembers} from '@/api'
import {useSession} from '@/composables/useSession'
import {setFieldValue as writeFieldValue} from '@/util/profileFields'
import {admits, groupOfSet, toggled} from '@/util/groupRules'
import {todayIsoDate} from '@/util/format'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useFieldAudiences} from '@/composables/useFieldAudiences'
import {describeFailure, type Failure, FailureKind} from '@/util/failure'

const {t} = useI18n()
const router = useRouter()

const step = ref<'userType' | 'identity' | 'fields' | 'groups' | 'manager' | 'done'>('userType')
const selectedUserType = ref<'TRIAL' | 'MEMBER' | 'GUARDIAN' | 'TEAM'>(StationUserType.MEMBER)
const firstName = ref('')
const lastName = ref('')
const email = ref('')
const canLogin = ref(true)
const sendSetupMail = ref(true)
const issueOneTimePassword = ref(true)
const oneTimePassword = ref<IssuedOneTimePassword | null>(null)
const oneTimePasswordFailure = ref<Failure | null>(null)
const linkPending = ref(false)

const {sessionInfo, hasPermission} = useSession()

/**
 * Whether the wizard offers a one-time password in place of the setup mail: only where no mail can
 * go out, and only to the station's administration, who alone may issue one.
 */
const offerOneTimePassword = computed(() =>
  sessionInfo.value?.canSendMail === false && hasPermission(StationPermission.STATION_ADMINISTRATOR))

/** Whether this member is handed a one-time password, in which case no setup mail is queued for them. */
const handsOverOneTimePassword = computed(() =>
  canLogin.value && offerOneTimePassword.value && issueOneTimePassword.value)
const allFields = ref<ProfileField[]>([])
const fieldValues = ref<Map<number, string>>(new Map())
const allGroups = ref<MemberGroup[]>([])
const selectedGroupIds = ref<Set<number>>(new Set())
const allMembers = ref<MemberWithName[]>([])
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

/** The groups that take the kind of member being created; a group bound to other kinds is not offered. */
const offeredGroups = computed(() => allGroups.value.filter(group => admits(group, selectedUserType.value)))

/** Picks or drops a group, and drops the other group of its set, since a member is in only one of those. */
function toggleGroup(id: number) {
  const group = allGroups.value.find(candidate => candidate.id === id)
  const sibling = groupOfSet(selectedGroupIds.value, allGroups.value, group?.groupSetId, id)
  const next = toggled(selectedGroupIds.value, id)
  if (sibling && next.has(id)) next.delete(sibling.id)
  selectedGroupIds.value = next
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
    invitedId = (await members.invite({...data, sendSetupMail: sendSetupMail.value})).memberId
  } catch (e) {
    failure.value = describeFailure(e, t)
    return
  }

  try {
    const membersList = await stationMembers.listMembers()
    const newMember = membersList.find(m => m.id === invitedId)
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

/**
 * Issues the one-time password for the account just made. A refusal is kept for the last step
 * rather than failing the whole wizard: the member is in either way, and the refusal names why the
 * password was not made (an address that already belonged to somebody elsewhere, for one).
 */
async function handOverOneTimePassword(accountId: number) {
  try {
    oneTimePassword.value = await members.issueOneTimePassword(accountId)
  } catch (e) {
    oneTimePasswordFailure.value = describeFailure(e, t)
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
    sendSetupMail: sendSetupMail.value && !handsOverOneTimePassword.value,
  })

  accountMade = true
  linkPending.value = invited.linkPending

  const membersList = await stationMembers.listMembers()
  const newMember = membersList.find(m => m.id === invited.memberId)
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

  const groupIds = offeredGroups.value.map(group => group.id).filter(id => selectedGroupIds.value.has(id))
  if (groupIds.length > 0) {
    await memberGroups.setMemberGroups(newMember.id, groupIds)
  }

  if (selectedUserType.value === StationUserType.MEMBER && selectedManagerIds.value.size > 0) {
    await stationMembers.setManagers(newMember.id, {managerIds: [...selectedManagerIds.value]})
  }

  if (handsOverOneTimePassword.value && invited.id !== null) {
    await handOverOneTimePassword(invited.id)
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
  issueOneTimePassword.value = true
  oneTimePassword.value = null
  oneTimePasswordFailure.value = null
  linkPending.value = false
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
          v-model:issue-one-time-password="issueOneTimePassword"
          v-model:email="email"
          v-model:first-name="firstName"
          v-model:last-name="lastName"
          :scope-fields="scopeFields"
          :field-values="fieldValues"
          :all-groups="offeredGroups"
          :selected-group-ids="selectedGroupIds"
          :all-members="allMembers"
          :selected-manager-ids="selectedManagerIds"
          :created-managers="createdManagers"
          :saving="saving"
          :offer-one-time-password="offerOneTimePassword"
          :one-time-password="oneTimePassword"
          :one-time-password-failure="oneTimePasswordFailure"
          :link-pending="linkPending"
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
