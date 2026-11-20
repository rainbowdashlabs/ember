/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {describeFailure} from '@/util/failure'
import {partnerEventCommentSource} from '@/api/comments'
import {
  RegistrationStatus,
  StationPermission,
  type PartnerAgreementOffer,
  type PartnerDocumentToSign,
  type PartnerEventDetail,
  type EventRegistrationFieldValue,
  type RemoteMemberRegistration,
} from '@/api/generated/schema'
import {events} from '@/api'
import {UNDO_WINDOW_MS} from '@/api/events'
import {showToast} from '@/util/toast'
import {useSession} from '@/composables/useSession'
import {stationDayOf, stationToday} from '@/util/format'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import AttachmentsCard from './federatedeventdetailview/AttachmentsCard.vue'
import EventGroupFrame from './eventshared/EventGroupFrame.vue'
import HeaderCard from './federatedeventdetailview/HeaderCard.vue'
import RegistrationCard from './federatedeventdetailview/RegistrationCard.vue'
import AgreementOfferCard from './federatedeventdetailview/AgreementOfferCard.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import CommentSection from '@/components/comment/CommentSection.vue'
import PartnerSigningStep from './eventshared/PartnerSigningStep.vue'
import RegistrationFieldsModal from './eventshared/RegistrationFieldsModal.vue'
import {usePartnerSignUp} from './federatedeventdetailview/usePartnerSignUp'
import {partnerDateState} from './federatedeventdetailview/partnerDatePlaces'
import {ourPartnerPlaces} from './eventshared/ourPartnerPlaces'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {sessionInfo, hasPermission, stationTimezone} = useSession()

const stationUid = ref(route.params.stationUid as string)
const eventId = ref(Number(route.params.eventId))

const detail = ref<PartnerEventDetail | null>(null)
const myRegistrations = ref<RemoteMemberRegistration[]>([])
const selectedMemberUid = ref('')

/** What an appointment without registrations offers to sign on its page, where signing says one will come. */
const agreementOffers = ref<PartnerAgreementOffer[]>([])
const offerMemberUid = ref('')

/** The documents the appointment asks the people just registered to sign here, which the registration ends on. */
const toSign = ref<PartnerDocumentToSign[]>([])
const signingStepOpen = computed({
  get: () => toSign.value.length > 0,
  set: (shown: boolean) => {
    if (!shown) toSign.value = []
  },
})

const currentMemberUid = computed(() => sessionInfo.value?.member?.uid ?? '')
const managedMembers = computed(() => sessionInfo.value?.managedMembers ?? [])

const eligibleMembers = computed(() => {
  const result: { uid: string; name: string }[] = []
  if (currentMemberUid.value) result.push({uid: currentMemberUid.value, name: t('eventsUpcoming.myself')})
  for (const m of managedMembers.value) {
    if (m.uid) result.push({uid: m.uid, name: m.name ?? m.email ?? `#${m.id}`})
  }
  return result
})

const eventData = computed(() => detail.value?.event ?? null)
const publicFields = computed(() => detail.value?.publicFields ?? [])

/**
 * The appointment's own name at the head of the page. The kind of thing it is stands there only
 * until the partner has answered, and where the partner could not be reached at all.
 */
const pageTitle = computed(() => eventData.value?.name || t('pages.federated-event-detail.title'))

/** What the station holding this appointment has given us, and whether the reader chooses who of us comes. */
const ourPlaces = computed(() =>
    ourPartnerPlaces(detail.value?.places, hasPermission(StationPermission.EVENT_REGISTRATION), t))

/**
 * Gives one of our own a place, which only works where the other station handed us the choosing.
 * They count, so a refusal means their places are full rather than that anything broke.
 */
async function confirmOwn(uid: string) {
  try {
    await events.confirmOwnFederatedMember(stationUid.value, eventId.value, getEventDate(), uid)
  } catch {
    showToast(t('eventsUpcoming.noPlacesLeft'), 'error')
    return
  }
  await refreshMyRegistrations()
}

/**
 * Reads our own places back after one was taken or given up.
 *
 * <p>Separate from the act itself. The place is already given, so a refresh that fails is a stale
 * screen and never a reason to press the button a second time.
 */
async function refreshMyRegistrations() {
  try {
    myRegistrations.value = await events.listMyFederatedRegistrations()
  } catch (e) {
    failure.value = {...describeFailure(e, t), message: t('failure.staleAfterAction')}
  }
}

/**
 * The day the partner's appointment falls on, which is the day a registration is filed under.
 *
 * <p>Read on this station's clock, because the partner's own is not handed over: stations that work
 * together keep the same one. The day of the stored moment in UTC is the day before for an
 * appointment that starts in the first hours after midnight.
 */
function getEventDate(): string {
  const start = eventData.value?.startTime
  return start ? stationDayOf(new Date(start), stationTimezone.value) : stationToday(stationTimezone.value)
}

function selectedUidForRegister(): string | null {
  const without = eligibleMembers.value.filter(m => !myRegistrations.value.some(r => r.eventId === eventId.value && r.remoteMemberId === m.uid))
  const single = without.length === 1 ? without[0] : undefined
  if (single) return single.uid
  return selectedMemberUid.value || null
}

const {running: registering, failure: registrationFailure, run: runRegistration, runOrReject} = useAsyncAction(
    async (kind: 'register' | 'withdraw', uid: string, answers: EventRegistrationFieldValue[] = []) => {
      if (kind === 'register') {
        const answer = await events.registerForFederatedEvent(stationUid.value, eventId.value, getEventDate(), uid, answers)
        myRegistrations.value.push({
          eventId: eventId.value, remoteMemberId: uid,
          eventDate: getEventDate(), status: answer.status, partnerId: 0,
        })
        toSign.value = answer.toSign
        if (answer.status === RegistrationStatus.QUEUED) showToast(t('eventsUpcoming.queuedAtPartner'), 'info')
      } else {
        await events.withdrawFederatedRegistration(stationUid.value, eventId.value, getEventDate(), uid)
        myRegistrations.value = myRegistrations.value.filter(r => !(r.eventId === eventId.value && r.remoteMemberId === uid))
        showToast(t('eventsUpcoming.signedOff'), 'info', UNDO_WINDOW_MS, {
          label: t('eventsUpcoming.undoSignOff'),
          run: () => undoWithdrawal(uid),
        })
      }
    })

/**
 * Asking the other station to put a place back. Theirs is the clock that decides, so a refusal here
 * means the few minutes have passed rather than that anything went wrong.
 */
async function undoWithdrawal(uid: string) {
  try {
    await events.undoFederatedWithdrawal(stationUid.value, eventId.value, getEventDate(), uid)
  } catch {
    showToast(t('eventsUpcoming.undoTooLate'), 'error')
    return
  }
  await refreshMyRegistrations()
}

const {open: askingQuestions, start: startSignUp, answer: answerQuestions} = usePartnerSignUp(
    computed(() => detail.value?.questions ?? []),
    (uid, answers) => runOrReject('register', uid, answers))

/** How a sign-up for the date would land: on a full date the button offers the waiting list. */
const dateState = computed(() => partnerDateState(detail.value, getEventDate()))

function registerForEvent() {
  const uid = selectedUidForRegister()
  if (!uid) return
  return startSignUp(uid)
}

function withdrawRegistration(uid: string) {
  return runRegistration('withdraw', uid)
}

const {running: takingOn, failure: takeOnFailure, run: takeOnAgreement} = useAsyncAction(async (uid: string) => {
  toSign.value = await events.takeOnFederatedAgreement(stationUid.value, eventId.value, getEventDate(), uid)
})

const commentSource = computed(() => partnerEventCommentSource(stationUid.value, eventId.value))

const {loading, failure, reload} = useAsyncLoader(async () => {
  const [eventDetail, regs] = await Promise.all([
    events.getFederatedEvent(stationUid.value, eventId.value),
    events.listMyFederatedRegistrations().catch(() => []),
  ])
  detail.value = eventDetail
  myRegistrations.value = regs
  agreementOffers.value = eventDetail.event.requiresRegistration
    ? []
    : await events.listFederatedAgreementOffers(stationUid.value, eventId.value, getEventDate()).catch(() => [])
})

watch(() => [route.params.stationUid, route.params.eventId], () => {
  stationUid.value = route.params.stationUid as string
  eventId.value = Number(route.params.eventId)
  reload()
})
</script>

<template>
  <ViewContent
      :title="pageTitle"
      :subtitle="t('pages.federated-event-detail.subtitle')"
  >
    <div class="space-y-4">
      <SecondaryButton :icon="['fas', 'arrow-left']" compact @click="router.push({ name: 'events-upcoming' })">
        {{ t('common.back') }}
      </SecondaryButton>

      <Spinner v-if="loading" size="lg"/>
      <FailureAlert :failure="failure ?? registrationFailure ?? takeOnFailure"/>

      <EventGroupFrame
          v-if="eventData && !loading"
          :event-group="eventData.eventGroup"
          :message="t('eventGroups.partnerPartNotice')"
          :to="eventData.eventGroup ? {name: 'federated-event-group', params: {stationUid, eventGroupId: eventData.eventGroup.eventGroupId}} : undefined">
        <HeaderCard :event="eventData" :public-fields="publicFields"/>

        <AttachmentsCard :station-uid="stationUid" :event-id="eventId"/>

        <RegistrationCard
            v-if="!eventData.eventGroup && eventData.requiresRegistration"
            v-model:selected-member-uid="selectedMemberUid"
            :eligible-members="eligibleMembers"
            :registrations="myRegistrations"
            :event-id="eventId"
            :registering="registering"
            :we-decide="ourPlaces.weDecide"
            :places-summary="ourPlaces.summary"
            :full="dateState.full"
            :joins-queue="dateState.joinsQueue"
            @register="registerForEvent"
            @withdraw="withdrawRegistration"
            @confirm="confirmOwn"
        />

        <AgreementOfferCard
            v-else-if="agreementOffers.length > 0"
            v-model:selected-member-uid="offerMemberUid"
            :offers="agreementOffers"
            :eligible-members="eligibleMembers"
            :busy="takingOn"
            @sign="takeOnAgreement"
        />

        <NeutralContainer>
          <CommentSection :source="commentSource"/>
        </NeutralContainer>
      </EventGroupFrame>
    </div>
    <PartnerSigningStep v-if="signingStepOpen" v-model="signingStepOpen" :documents="toSign"/>
    <RegistrationFieldsModal
        v-model="askingQuestions"
        :fields="detail?.questions ?? []"
        :busy="registering"
        :failure="registrationFailure"
        @confirm="answerQuestions"
    />
  </ViewContent>
</template>
