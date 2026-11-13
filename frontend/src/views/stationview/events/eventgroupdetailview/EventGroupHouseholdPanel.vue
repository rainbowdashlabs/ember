/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import RegistrationStatusBadge from '../eventshared/RegistrationStatusBadge.vue'
import EventGroupSignUpDialog from './EventGroupSignUpDialog.vue'
import EventGroupShortfall from './EventGroupShortfall.vue'
import EventGroupAnswersButton from './EventGroupAnswersButton.vue'
import RegistrationFieldAnswers from '../eventdetailview/RegistrationFieldAnswers.vue'
import type {EventGroupDetail, EventGroupSignUp} from '@/api/generated/schema'
import {RegistrationStatus} from '@/api/generated/schema'
import {ownEventGroupPort, type EventGroupSignUpPort} from './eventGroupSignUpPort'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * The sign-up of the reader and of everybody they look after: whether each is signed up, which parts
 * they chose, and the buttons to sign up, change the parts or withdraw.
 */
const props = defineProps<{
  detail: EventGroupDetail
  /** Called after a sign-up changed, so the page reloads what it shows. */
  onChanged?: () => void | Promise<void>
  /** Where sign-ups go; an event group of this station unless a partner's event group page says otherwise. */
  port?: EventGroupSignUpPort
}>()

const {t} = useI18n()

const dialogFor = ref<EventGroupSignUp | null>(null)
const withdrawFor = ref<EventGroupSignUp | null>(null)
const withdrawOpen = ref(false)
const busy = ref(false)
const failure = ref<Failure | null>(null)

/** Whether sign-ups closed with the deadline. */
const deadlinePassed = computed(() => {
  const deadline = props.detail.eventGroup.registrationDeadline
  return deadline != null && new Date(deadline).getTime() < Date.now()
})

/** Whether no part is left to choose: none yet, or every one past or called off. */
const nothingLeft = computed(() =>
    props.detail.parts.length === 0 || props.detail.parts.every(part => part.tookPlace || part.calledOff))

/** Why parts can no longer be changed, or null while they can. */
const changeClosed = computed(() => {
  if (deadlinePassed.value) return t('eventGroups.closedDeadline')
  if (props.detail.parts.length === 0) return t('eventGroups.closedNoParts')
  if (nothingLeft.value) return t('eventGroups.closedOver')
  return null
})

/** Why nobody can newly sign up, or null while the event group takes sign-ups; a full event group closes here too. */
const signUpClosed = computed(() => {
  if (changeClosed.value) return changeClosed.value
  const eventGroup = props.detail.eventGroup
  if (eventGroup.registrationLimit != null && eventGroup.holding >= eventGroup.registrationLimit) return t('eventGroups.closedFull')
  return null
})

/** Whether a sign-up holds or waits for a place. */
function active(entry: EventGroupSignUp): boolean {
  return entry.status === RegistrationStatus.PENDING || entry.status === RegistrationStatus.ACCEPTED
}

function askWithdraw(entry: EventGroupSignUp) {
  withdrawFor.value = entry
  withdrawOpen.value = true
}

async function confirmWithdraw() {
  const entry = withdrawFor.value
  if (!entry) return
  busy.value = true
  try {
    await (props.port ?? ownEventGroupPort(props.detail.eventGroup.id)).withdraw(entry.memberId)
    withdrawOpen.value = false
    await props.onChanged?.()
  } catch (e) {
    failure.value = describeFailure(e, t)
    withdrawOpen.value = false
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="event-group-household">
    <SectionHeader>{{ t('eventGroups.household') }}</SectionHeader>
    <FailureAlert v-if="failure" :failure="failure"/>
    <div v-for="entry in detail.household" :key="entry.memberId"
         class="flex flex-wrap items-center justify-between gap-2" data-testid="event-group-household-entry">
      <div class="flex flex-wrap items-center gap-2 min-w-0">
        <MemberName :identity="entry.member"/>
        <RegistrationStatusBadge v-if="entry.status" :status="entry.status"/>
        <MutedText v-else tag="span" size="sm">{{ t('eventGroups.notSignedUp') }}</MutedText>
        <MutedText v-if="active(entry)" tag="span" size="sm">{{ t('eventGroups.chosen', {count: entry.parts.length}) }}</MutedText>
        <EventGroupShortfall v-if="active(entry)" :sets="entry.sets"/>
        <ErrorBadge v-if="entry.answersMissing" data-testid="event-group-answer-missing">{{ t('eventDetail.answerMissing') }}</ErrorBadge>
        <RegistrationFieldAnswers v-if="active(entry)" :fields="detail.questions" :values="entry.answers" class="basis-full"/>
      </div>
      <MutedText v-if="!entry.mayRegister && !active(entry)" tag="span" size="sm">
        {{ t('eventGroups.notMeantFor', {name: entry.member.name ?? ''}) }}
      </MutedText>
      <ButtonRow v-else-if="!detail.eventGroup.cancelled" align="end">
        <template v-if="!active(entry)">
          <MutedText v-if="signUpClosed" tag="span" size="sm" data-testid="event-group-closed">{{ signUpClosed }}</MutedText>
          <PrimaryButton v-else :icon="['fas', 'check']" data-testid="event-group-sign-up" @click="dialogFor = entry">
            {{ t('eventGroups.signUp') }}
          </PrimaryButton>
        </template>
        <template v-else>
          <SecondaryButton
              v-if="!changeClosed"
              :icon="['fas', 'pen']"
              data-testid="event-group-change-parts"
              @click="dialogFor = entry"
          >
            {{ t('eventGroups.changeParts') }}
          </SecondaryButton>
          <EventGroupAnswersButton v-if="!changeClosed && detail.questions.length > 0" :event-group-id="detail.eventGroup.id"
                               :entry="entry" :questions="detail.questions" @saved="onChanged?.()"/>
          <SecondaryButton :icon="['fas', 'xmark']" data-testid="event-group-withdraw" @click="askWithdraw(entry)">
            {{ t('eventGroups.withdraw') }}
          </SecondaryButton>
        </template>
      </ButtonRow>
    </div>
    <EventGroupSignUpDialog
        v-if="dialogFor"
        :detail="detail"
        :entry="dialogFor"
        :port="port"
        @close="dialogFor = null"
        @saved="dialogFor = null; onChanged?.()"
    />
    <ConfirmDeleteModal
        v-model="withdrawOpen"
        :title="t('eventGroups.withdrawTitle')"
        :message="t('eventGroups.withdrawText', {name: withdrawFor?.member.name ?? ''})"
        :confirm-label="t('eventGroups.withdraw')"
        :busy="busy"
        @confirm="confirmWithdraw"
    />
  </NeutralContainer>
</template>
