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
  StationPermission,
  type PartnerAgreementOffer,
  type PartnerDocumentToSign,
  type PartnerEventDetail,
  type RemoteMemberRegistration,
} from '@/api/generated/schema'
import {events} from '@/api'
import {UNDO_WINDOW_MS} from '@/api/events'
import {showToast} from '@/util/toast'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import AttachmentsCard from './federatedeventdetailview/AttachmentsCard.vue'
import HeaderCard from './federatedeventdetailview/HeaderCard.vue'
import RegistrationCard from './federatedeventdetailview/RegistrationCard.vue'
import AgreementOfferCard from './federatedeventdetailview/AgreementOfferCard.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import CommentSection from '@/components/comment/CommentSection.vue'
import PartnerSigningStep from './eventshared/PartnerSigningStep.vue'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {sessionInfo, hasPermission} = useSession()

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

/**
 * What the station holding this appointment has given us, where it has given anything.
 *
 * <p>Absent is the ordinary arrangement: they decide about each of our members, as they always did.
 * Present means we choose who comes, and where they also named a number, how many.
 */
const ourPlaces = computed(() => detail.value?.places ?? null)

/**
 * What we were given, in words, without claiming how much of it is left.
 *
 * <p>Only the station holding the appointment can count that: what this one can see is its own
 * members' places and not the rest of the station's, and a number worked out from those alone would
 * read as the whole and be wrong for everybody with colleagues. They answer when the places are
 * full, and that answer is the one that counts.
 */
const placesSummary = computed(() => {
  const places = ourPlaces.value
  if (!places) return ''
  if (places.slotBudget === null) return t('eventsUpcoming.placesUncapped')
  return t('eventsUpcoming.placesGiven', {total: places.slotBudget})
})

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

function getEventDate(): string {
  if (eventData.value?.startTime) return new Date(eventData.value.startTime).toISOString().slice(0, 10)
  return new Date().toISOString().slice(0, 10)
}

function selectedUidForRegister(): string | null {
  const without = eligibleMembers.value.filter(m => !myRegistrations.value.some(r => r.eventId === eventId.value && r.remoteMemberId === m.uid))
  const single = without.length === 1 ? without[0] : undefined
  if (single) return single.uid
  return selectedMemberUid.value || null
}

const {running: registering, failure: registrationFailure, run: runRegistration} = useAsyncAction(
    async (kind: 'register' | 'withdraw', uid: string) => {
      if (kind === 'register') {
        const answer = await events.registerForFederatedEvent(stationUid.value, eventId.value, getEventDate(), uid)
        myRegistrations.value.push({
          eventId: eventId.value, remoteMemberId: uid,
          eventDate: getEventDate(), status: answer.status, partnerId: 0,
        })
        toSign.value = answer.toSign
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

function registerForEvent() {
  const uid = selectedUidForRegister()
  if (!uid) return
  return runRegistration('register', uid)
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

      <template v-if="eventData && !loading">
        <HeaderCard :event="eventData" :public-fields="publicFields"/>

        <AttachmentsCard :station-uid="stationUid" :event-id="eventId"/>

        <RegistrationCard
            v-if="eventData.requiresRegistration"
            v-model:selected-member-uid="selectedMemberUid"
            :eligible-members="eligibleMembers"
            :registrations="myRegistrations"
            :event-id="eventId"
            :registering="registering"
            :we-decide="(ourPlaces?.decidesItself ?? false) && hasPermission(StationPermission.EVENT_REGISTRATION)"
            :places-summary="placesSummary"
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
      </template>
    </div>
    <PartnerSigningStep v-if="signingStepOpen" v-model="signingStepOpen" :documents="toSign"/>
  </ViewContent>
</template>
