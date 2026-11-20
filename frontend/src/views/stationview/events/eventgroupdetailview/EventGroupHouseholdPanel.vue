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
import {RegistrationStatus, type EventGroupDetail, type EventGroupSignUp} from '@/api/generated/schema'
import {eventGroupPlaces, holdsOrWaitsForPlace} from '@/util/eventGroupChoice'
import {ownEventGroupPort, type EventGroupSignUpPort} from './eventGroupSignUpPort'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {hasPassed} from '../eventshared/registrationDeadline'
import type {OurPartnerPlaces} from '../eventshared/ourPartnerPlaces'

/**
 * The sign-up of the reader and of everybody they look after: whether each is signed up, which parts
 * they chose, and the buttons to sign up, change the parts or withdraw.
 *
 * <p>A full event group still takes sign-ups: they wait on the waiting list, which the button then
 * says, and whoever waits sees their place in line and can leave it. Once no part is left, a wait
 * nobody moved up from reads as no place.
 *
 * <p>On a partner's event group that handed this station the choosing, the places given stand above
 * the list, and a reader who keeps the station's sign-ups confirms a waiting one from here.
 */
const props = defineProps<{
  detail: EventGroupDetail
  /** Called after a sign-up changed, so the page reloads what it shows. */
  onChanged?: () => void | Promise<void>
  /** Where sign-ups go; an event group of this station unless a partner's event group page says otherwise. */
  port?: EventGroupSignUpPort
  /** What a partner holding the event group gave this station, where this is a partner's event group. */
  ourPlaces?: OurPartnerPlaces
}>()

const {t} = useI18n()

const dialogFor = ref<EventGroupSignUp | null>(null)
const withdrawFor = ref<EventGroupSignUp | null>(null)
const withdrawOpen = ref(false)

/** Whether sign-ups closed with the deadline. */
const deadlinePassed = computed(() => hasPassed(props.detail.eventGroup.closesAt))

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

/** Why nobody can newly sign up, or null while the event group takes sign-ups. A full one still does. */
const signUpClosed = computed(() => changeClosed.value)

/**
 * Whether a sign-up now waits on the waiting list because every place for people is taken. Where
 * sign-ups are confirmed, they wait for the decision instead. A full part is shown in the dialog.
 */
const joinsQueue = computed(() =>
    !props.detail.eventGroup.details.requiresConfirmation && !!eventGroupPlaces(props.detail.eventGroup)?.full)

function isQueued(entry: EventGroupSignUp): boolean {
  return entry.status === RegistrationStatus.QUEUED
}

function askWithdraw(entry: EventGroupSignUp) {
  withdrawFor.value = entry
  withdrawOpen.value = true
}

const {running: busy, failure, run: withdraw} = useAsyncAction(async (entry: EventGroupSignUp) => {
  await (props.port ?? ownEventGroupPort(props.detail.eventGroup.id)).withdraw(entry.memberId)
  withdrawOpen.value = false
  await props.onChanged?.()
})

/** Whether the reader may give this waiting sign-up its place here, which only a partner that handed over the choosing allows. */
function confirmable(entry: EventGroupSignUp): boolean {
  return !!props.ourPlaces?.weDecide && !!props.port?.confirm && entry.status === RegistrationStatus.PENDING
}

const {running: confirming, failure: confirmFailure, run: confirm} = useAsyncAction(async (entry: EventGroupSignUp) => {
  await props.port?.confirm?.(entry.memberId)
  await props.onChanged?.()
})

/** Withdraws the sign-up asked about and closes the dialog, whether the withdrawal worked or not. */
async function confirmWithdraw() {
  const entry = withdrawFor.value
  if (!entry) return
  await withdraw(entry)
  withdrawOpen.value = false
}
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="event-group-household">
    <SectionHeader>{{ t('eventGroups.household') }}</SectionHeader>
    <MutedText v-if="ourPlaces?.summary" tag="p" size="sm" data-testid="event-group-our-places">{{ ourPlaces.summary }}</MutedText>
    <FailureAlert v-if="failure ?? confirmFailure" :failure="failure ?? confirmFailure"/>
    <div v-for="entry in detail.household" :key="entry.memberId"
         class="flex flex-wrap items-center justify-between gap-2" data-testid="event-group-household-entry">
      <div class="flex flex-wrap items-center gap-2 min-w-0">
        <MemberName :identity="entry.member"/>
        <RegistrationStatusBadge v-if="entry.status" :status="entry.status" :queue-position="entry.queuePosition" :past="nothingLeft"/>
        <MutedText v-else tag="span" size="sm">{{ t('eventGroups.notSignedUp') }}</MutedText>
        <MutedText v-if="holdsOrWaitsForPlace(entry.status)" tag="span" size="sm">{{ t('eventGroups.chosen', {count: entry.parts.length}) }}</MutedText>
        <EventGroupShortfall v-if="holdsOrWaitsForPlace(entry.status)" :sets="entry.sets"/>
        <ErrorBadge v-if="entry.answersMissing" data-testid="event-group-answer-missing">{{ t('eventDetail.answerMissing') }}</ErrorBadge>
        <RegistrationFieldAnswers v-if="holdsOrWaitsForPlace(entry.status)" :fields="detail.questions" :values="entry.answers" class="basis-full"/>
      </div>
      <MutedText v-if="!entry.mayRegister && !holdsOrWaitsForPlace(entry.status)" tag="span" size="sm">
        {{ t('eventGroups.notMeantFor', {name: entry.member.name ?? ''}) }}
      </MutedText>
      <ButtonRow v-else-if="!detail.eventGroup.cancelled" align="end">
        <template v-if="!holdsOrWaitsForPlace(entry.status)">
          <MutedText v-if="signUpClosed" tag="span" size="sm" data-testid="event-group-closed">{{ signUpClosed }}</MutedText>
          <PrimaryButton v-else :icon="['fas', joinsQueue ? 'list-ol' : 'check']" data-testid="event-group-sign-up" @click="dialogFor = entry">
            {{ joinsQueue ? t('registrationQueue.join') : t('eventGroups.signUp') }}
          </PrimaryButton>
        </template>
        <template v-else>
          <PrimaryButton v-if="confirmable(entry)" :icon="['fas', 'check']" :disabled="confirming"
                         data-testid="event-group-confirm-own" @click="confirm(entry)">
            {{ t('common.confirm') }}
          </PrimaryButton>
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
          <SecondaryButton
              v-if="!(isQueued(entry) && nothingLeft)"
              :icon="['fas', 'xmark']"
              data-testid="event-group-withdraw"
              @click="askWithdraw(entry)"
          >
            {{ isQueued(entry) ? t('registrationQueue.leave') : t('eventGroups.withdraw') }}
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
        :confirm-label="withdrawFor && isQueued(withdrawFor) ? t('registrationQueue.leave') : t('eventGroups.withdraw')"
        :busy="busy"
        neutral
        @confirm="confirmWithdraw"
    />
  </NeutralContainer>
</template>
