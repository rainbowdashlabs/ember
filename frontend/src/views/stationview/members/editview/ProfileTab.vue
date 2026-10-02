/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Alert from '@/components/feedback/Alert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import ProfileFieldsLayout from '@/components/profilefields/ProfileFieldsLayout.vue'
import NicknameSection from '@/components/member/NicknameSection.vue'
import type {MemberWithName} from '@/api/generated/schema'
import {members, stationMembers} from '@/api'
import {useSession} from '@/composables/useSession'
import type {ProfileAnswers} from '@/composables/useProfileAnswers'
import {describeFailure, type Failure} from '@/util/failure'

const {t} = useI18n()
const {sessionInfo} = useSession()

const props = defineProps<{
  member: MemberWithName
  memberId: number
  /** The member's answers as the station's member management holds them while they are changed. */
  answers: ProfileAnswers
}>()

const fields = computed(() => props.answers.fields.value)

const editFirstName = ref(props.member.firstName ?? '')
const editLastName = ref(props.member.lastName ?? '')
const editEmail = ref(props.member.email ?? '')
const editUsername = ref(props.member.username ?? '')

/**
 * The name this station calls the member by, which whoever keeps the members may put right.
 *
 * <p>Theirs to choose in the ordinary case, and set on their own profile. This is the other way in:
 * a name that is wrong, or that somebody is unhappy to have been given, has to be correctable by the
 * people who run the place. Who wrote it is recorded either way.
 */
const editNickname = ref(props.member.nickname ?? '')
const editJoinDate = ref(props.member.joinDate ?? '')
const failure = ref<Failure | null>(null)
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
  failure.value = null
  try {
    await stationMembers.setJoinDate(props.memberId, value)
    editJoinDate.value = value
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}

/**
 * Writes the account, then the answers and the name the station calls them by.
 *
 * <p>The account is written first and caught on its own, because an address or a username somebody else
 * already has is the ordinary way this fails and the server names which. What follows is caught apart:
 * by then the account is changed, and a reader told plainly that saving failed retypes an address that
 * is already in.
 */
async function save() {
  failure.value = null
  notice.value = ''
  const addressChanged = editEmail.value.trim().toLowerCase() !== (props.member.email ?? '').toLowerCase()

  try {
    await members.updateAccount(props.member.accountId, {
      email: editEmail.value,
      username: editUsername.value,
      firstName: editFirstName.value,
      lastName: editLastName.value,
    })
  } catch (e) {
    failure.value = describeFailure(e, t)
    throw e
  }

  try {
    await props.answers.save(props.memberId)
    await members.setNickname(props.memberId, editNickname.value.trim() || null)
  } catch (e) {
    failure.value = {...describeFailure(e, t), message: t('memberEdit.accountSavedRestNot')}
    throw e
  }

  if (addressChanged && ownAccount()) notice.value = t('memberEdit.emailConfirmationPending')
}
</script>

<template>
  <div class="space-y-6">
    <FailureAlert :failure="failure"/>
    <Alert v-if="notice" variant="info">{{ notice }}</Alert>

    <NeutralContainer class="space-y-4">
      <SubHeader class="text-sm">{{ t('memberEdit.baseFields') }}</SubHeader>
      <div class="grid gap-4 sm:grid-cols-3">
        <div class="space-y-1">
          <FieldLabel hint>{{ t('memberEdit.firstName') }}</FieldLabel>
          <TextInput v-model="editFirstName" data-testid="member-first-name"/>
        </div>
        <div class="space-y-1">
          <FieldLabel hint>{{ t('memberEdit.lastName') }}</FieldLabel>
          <TextInput v-model="editLastName" data-testid="member-last-name"/>
        </div>
        <div class="space-y-1">
          <FieldLabel hint>{{ t('memberEdit.email') }}</FieldLabel>
          <TextInput v-model="editEmail" data-testid="member-email"/>
          <p v-if="!ownAccount()" class="text-xs text-(--text-muted)">{{ t('memberEdit.emailHint') }}</p>
        </div>
      </div>
      <div class="space-y-1">
        <FieldLabel hint>{{ t('memberEdit.username') }}</FieldLabel>
        <TextInput v-model="editUsername" :placeholder="editEmail"/>
        <p class="text-xs text-(--text-muted)">{{ t('memberEdit.usernameHint') }}</p>
      </div>
    </NeutralContainer>

    <NicknameSection
        v-model="editNickname"
        :first-name="member.firstName ?? ''"
        :hint="t('memberEdit.nicknameHint')"
        :last-name="member.lastName ?? ''"
    />

    <NeutralContainer class="space-y-3">
      <SubHeader class="text-sm">{{ t('memberEdit.joinDate') }}</SubHeader>
      <DateInput :model-value="editJoinDate" class="max-w-xs" @update:model-value="onJoinDateChange"/>
      <p class="text-xs text-(--text-muted)">{{ t('memberEdit.joinDateHint') }}</p>
    </NeutralContainer>

    <NeutralContainer v-if="fields.length > 0" class="space-y-4">
      <SubHeader class="text-sm">{{ t('memberEdit.fields') }}</SubHeader>
      <ProfileFieldsLayout
          :fields="fields"
          :get-value="answers.valueOf"
          can-edit-readonly
          @update="answers.update"
      />
    </NeutralContainer>

    <div class="flex items-center">
      <SaveButton :action="save"/>
    </div>
  </div>
</template>
