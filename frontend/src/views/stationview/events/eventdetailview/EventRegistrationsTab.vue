/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import Alert from '@/components/feedback/Alert.vue'
import {RegistrationStatus, type EventRegistrationEntry, type EventRegistrationField, type FederatedEventRegistration, type MemberRegistrationStats, type RegistrationFieldValue, type StationEvent} from '@/api/events'
import {StationPermission, type MemberIdentity} from '@/api/types'
import {events, stationMembers as stationMembersApi} from '@/api'
import {useSession} from '@/composables/useSession'
import {useSidebarCounts} from '@/composables/useSidebarCounts'
import {useAsyncAction} from '@/composables/useAsyncAction'
import RegistrationsPanel from './RegistrationsPanel.vue'
import FederatedRegistrationsPanel from './FederatedRegistrationsPanel.vue'
import RegistrationFieldsModal from '../eventshared/RegistrationFieldsModal.vue'
import EventAnswerDialog from '../eventshared/EventAnswerDialog.vue'
import type {AnswerablePerson, PersonAnswer} from '@/util/eventAnswers'

const props = defineProps<{
  event: StationEvent
  eventId: number
  currentMemberId: number
  registrableMembers: AnswerablePerson[]
  hasManagedMembers: boolean
  nextOccurrenceDate: string | null
}>()

const {t} = useI18n()
const {canManageEvents, hasPermission} = useSession()
const {refresh: refreshSidebarCounts} = useSidebarCounts()

const registrations = ref<EventRegistrationEntry[]>([])
const registrationStats = ref<MemberRegistrationStats[]>([])
const federatedRegs = ref<FederatedEventRegistration[]>([])
interface MemberOption {
  id: number
  name: string
  email?: string | null
  /** Their face and colours, so the picker shows a person rather than a line of text. */
  identity?: MemberIdentity | null
  /** What kind of member they are, so the picker can be narrowed to one kind. */
  userType?: string | null
}

const allMembers = ref<MemberOption[]>([])
const error = ref('')
const manualRegisterMemberId = ref('')

const registrationFields = ref<EventRegistrationField[]>([])
const showFieldsModal = ref(false)
const pendingRegistrationMemberId = ref<number | null>(null)

interface StatusGroup { status: string; entries: EventRegistrationEntry[] }

const pendingRegistrations = computed(() => {
  const pending = registrations.value.filter(r => r.status === RegistrationStatus.PENDING)
  return [...pending].sort((a, b) => {
    const sa = registrationStats.value.find(s => s.memberId === a.memberId)
    const sb = registrationStats.value.find(s => s.memberId === b.memberId)
    return (sb?.fairnessScore ?? 0) - (sa?.fairnessScore ?? 0)
  })
})

const nonPendingRegistrations = computed<StatusGroup[]>(() => {
  const byStatus = new Map<string, EventRegistrationEntry[]>()
  for (const reg of registrations.value) {
    if (reg.status === RegistrationStatus.PENDING) continue
    const list = byStatus.get(reg.status) ?? []
    list.push(reg)
    byStatus.set(reg.status, list)
  }
  for (const list of byStatus.values()) {
    list.sort((a, b) => a.memberName.localeCompare(b.memberName, 'de'))
  }
  return [RegistrationStatus.ACCEPTED, RegistrationStatus.DECLINED, RegistrationStatus.DENIED]
      .filter(s => byStatus.has(s))
      .map(s => ({status: s, entries: byStatus.get(s)!}))
})

const unregisteredMembers = computed(() => {
  const regIds = new Set(registrations.value.map(r => r.memberId))
  return allMembers.value.filter(m => !regIds.has(m.id)).sort((a, b) => a.name.localeCompare(b.name))
})

function getRegistrationForMember(memberId: number): EventRegistrationEntry | undefined {
  return registrations.value.find(r => r.memberId === memberId)
}

function statusLabel(status: string): string {
  if (status === RegistrationStatus.ACCEPTED) return t('eventsUpcoming.statusAccepted')
  if (status === RegistrationStatus.PENDING) return t('eventsUpcoming.statusPending')
  if (status === RegistrationStatus.DENIED) return t('eventsUpcoming.statusDenied')
  if (status === RegistrationStatus.DECLINED) return t('eventsUpcoming.statusDeclined')
  return status
}

async function loadRegistrations() {
  try {
    registrations.value = await events.listEventRegistrations(props.eventId)
    registrationFields.value = await events.listRegistrationFields(props.eventId).catch(() => [])
    if (hasPermission(StationPermission.EVENT_REGISTRATION) && props.event.requiresRegistration) {
      registrationStats.value = await events.getRegistrationStats(
          props.eventId, props.event.categoryId ?? undefined)
    }
    if (hasPermission(StationPermission.EVENT_REGISTRATION)) {
      federatedRegs.value = await events.listFederationRegistrations(props.eventId).catch(() => [])
      const members = await stationMembersApi.listMembers().catch(() => [])
      allMembers.value = members.map(m => ({
        id: m.id,
        name: m.name ?? m.email ?? `#${m.id}`,
        email: m.email ?? null,
        identity: m.identity ?? null,
        userType: m.userType ?? null,
      }))
    }
  } catch {
    error.value = t('common.error')
  }
}

async function reloadAndRefresh() {
  await loadRegistrations()
  refreshSidebarCounts()
}

async function acceptRegistration(id: number) {
  try {
    await events.updateRegistrationStatus(id, RegistrationStatus.ACCEPTED)
    await reloadAndRefresh()
  } catch { error.value = t('common.error') }
}

async function denyRegistration(id: number) {
  try {
    await events.updateRegistrationStatus(id, RegistrationStatus.DENIED)
    await reloadAndRefresh()
  } catch { error.value = t('common.error') }
}

const {running: registering, error: registrationError, run: runRegistration} = useAsyncAction(
    async (kind: 'register' | 'decline', memberId: number, fields?: RegistrationFieldValue[]) => {
      const request = {
        eventDate: props.nextOccurrenceDate ?? undefined,
        memberId: memberId !== props.currentMemberId ? memberId : undefined,
        fields,
      }
      if (kind === 'register') {
        await events.registerForEvent(props.eventId, request)
      } else {
        await events.declineEvent(props.eventId, request)
      }
      await reloadAndRefresh()
    },
    {formatError: () => t('common.error')},
)

/**
 * Registering asks the event's questions first. Without questions the button stays a button -
 * an event that asks nothing must not gain a dialog.
 */
function registerMember(memberId: number) {
  if (registrationFields.value.length === 0) return runRegistration('register', memberId)
  pendingRegistrationMemberId.value = memberId
  showFieldsModal.value = true
}

async function confirmRegistrationFields(values: RegistrationFieldValue[]) {
  const memberId = pendingRegistrationMemberId.value
  if (memberId == null) return
  showFieldsModal.value = false
  pendingRegistrationMemberId.value = null
  if (manualRegisterMemberId.value && Number(manualRegisterMemberId.value) === memberId) {
    await manualRegister(values)
    return
  }
  await runRegistration('register', memberId, values)
}

/**
 * Gives up the place somebody was given, by deleting it.
 *
 * <p>Not a refusal written down in its place: this event has to be signed up for, so having no place
 * already says everything a refusal would, and two rows saying the same thing is one too many.
 */
async function undoAnswerFor(memberId: number) {
  const registration = getRegistrationForMember(memberId)
  if (!registration) return
  await events.withdrawRegistration(registration.id)
  await reloadAndRefresh()
}

const showAnswerDialog = ref(false)

/**
 * Everyone this reader answers for. Offered as one dialog only when there is more than one of them: a
 * member answering for themselves is a button, and turning that into a dialog would be a step for nothing.
 */
const household = computed(() => props.registrableMembers)

/**
 * Signs the household up. One of them acts at once, and asks the event's questions on the way if it
 * has any; several open the dialog, where they are ticked and each gets their own questions.
 */
function answerForHousehold() {
  if (household.value.length === 1) {
    return registerMember(household.value[0]!.key)
  }
  showAnswerDialog.value = true
}

/** Everyone in the household who has a place, which is who there is something to give up for. */
const withPlace = computed(() =>
    household.value.filter(person => getRegistrationForMember(person.key) !== undefined))

/** Everyone still to answer, which is who the sign-up button is for. */
const withoutPlace = computed(() =>
    household.value.filter(person => getRegistrationForMember(person.key) === undefined))

const answerLabel = computed(() =>
    withoutPlace.value.length > 1 ? t('events.answerForAll') : t('eventsUpcoming.register'))

const withdrawLabel = computed(() =>
    withPlace.value.length > 1 ? t('events.declineForAll') : t('eventsUpcoming.unregister'))

/** Gives up every place the household holds, which is what the one button beside them offers. */
async function withdrawHousehold() {
  for (const person of withPlace.value) {
    await undoAnswerFor(person.key)
  }
}

async function confirmHouseholdAnswer(answers: PersonAnswer[]) {
  showAnswerDialog.value = false
  for (const answer of answers) {
    await runRegistration('register', answer.key, answer.fields)
  }
}

async function acceptFederatedReg(regId: number) {
  await events.updateFederationRegistrationStatus(regId, 'ACCEPTED')
  await loadRegistrations()
}

async function denyFederatedReg(regId: number) {
  await events.updateFederationRegistrationStatus(regId, 'DENIED')
  await loadRegistrations()
}

/**
 * Adding a member by hand asks the same questions. The answers belong to the registration, not to
 * whoever typed them, so a manager fills them in on the member's behalf.
 */
async function manualRegister(values?: RegistrationFieldValue[]) {
  if (!manualRegisterMemberId.value) return
  if (registrationFields.value.length > 0 && values === undefined) {
    pendingRegistrationMemberId.value = Number(manualRegisterMemberId.value)
    showFieldsModal.value = true
    return
  }
  try {
    await events.registerForEvent(props.eventId, {
      eventDate: props.nextOccurrenceDate ?? undefined,
      memberId: Number(manualRegisterMemberId.value),
      fields: values,
    })
    manualRegisterMemberId.value = ''
    await reloadAndRefresh()
  } catch { error.value = t('common.error') }
}

/**
 * The tab loads itself. Its parent only renders it once the event is there, so mounting is the
 * first moment the load can succeed - the parent's own call runs while it is still loading and its
 * ref is therefore still null.
 */
onMounted(loadRegistrations)
</script>

<template>
  <div class="space-y-6">
    <Alert v-if="error || registrationError" variant="error">{{ error || registrationError }}</Alert>

    <NeutralContainer v-if="event.requiresRegistration && !canManageEvents()" class="space-y-3">
      <SubHeader>{{ t('eventDetail.myRegistration') }}</SubHeader>
      <div class="flex items-center gap-2 flex-wrap">
        <PrimaryButton v-if="withoutPlace.length > 0" :disabled="registering" data-testid="answer-household"
                       @click="answerForHousehold()">
          <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>{{ answerLabel }}
        </PrimaryButton>
        <SecondaryButton v-if="withPlace.length > 0" :disabled="registering" data-testid="withdraw-household"
                         @click="withdrawHousehold()">
          <font-awesome-icon :icon="['fas', 'rotate-left']" class="mr-1"/>{{ withdrawLabel }}
        </SecondaryButton>
      </div>
      <div v-for="member in registrableMembers" :key="member.key" class="flex items-center gap-3 flex-wrap">
        <span v-if="hasManagedMembers" class="text-sm font-medium min-w-24">{{ member.name }}</span>
        <component
            v-if="getRegistrationForMember(member.key)"
            :data-testid="`my-answer-${member.key}`"
            :is="getRegistrationForMember(member.key)!.status === RegistrationStatus.ACCEPTED ? SuccessBadge : getRegistrationForMember(member.key)!.status === RegistrationStatus.PENDING ? InfoBadge : ErrorBadge">
          {{ statusLabel(getRegistrationForMember(member.key)!.status) }}
        </component>
        <SecondaryBadge v-else :data-testid="`my-answer-${member.key}`">{{ t('eventDetail.noAnswerYet') }}</SecondaryBadge>
      </div>
    </NeutralContainer>

    <EventAnswerDialog
        v-model="showAnswerDialog"
        :people="household"
        :fields="registrationFields"
        :attending="true"
        :busy="registering"
        :error="registrationError"
        @confirm="confirmHouseholdAnswer"
    />

    <RegistrationsPanel
        :event="event"
        :registrations="registrations"
        :pending-registrations="pendingRegistrations"
        :non-pending-registrations="nonPendingRegistrations"
        :registration-stats="registrationStats"
        :unregistered-members="unregisteredMembers"
        :registration-fields="registrationFields"
        v-model:manual-register-member-id="manualRegisterMemberId"
        @accept="acceptRegistration"
        @deny="denyRegistration"
        @manual-register="manualRegister"
    />

    <RegistrationFieldsModal
        v-model="showFieldsModal"
        :fields="registrationFields"
        :busy="registering"
        @confirm="confirmRegistrationFields"
    />

    <FederatedRegistrationsPanel
        v-if="canManageEvents()"
        :registrations="federatedRegs"
        @accept="acceptFederatedReg"
        @deny="denyFederatedReg"
    />
  </div>
</template>
