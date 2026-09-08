/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import Alert from '@/components/feedback/Alert.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import EventCancelModal from './EventCancelModal.vue'
import EventRegistrationsTab from './EventRegistrationsTab.vue'
import EventDetailHeader from './EventDetailHeader.vue'
import EventInfoTab from './EventInfoTab.vue'
import EventEquipmentTab from './EventEquipmentTab.vue'
import EventRegistrationActions from '../eventshared/EventRegistrationActions.vue'
import RegistrationFieldsModal from '../eventshared/RegistrationFieldsModal.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import {isRecurringEvent, type AbsentMember, type EventField, type EventRegistrationEntry, type EventRegistrationField, type RegistrationFieldValue, type StationEvent} from '@/api/events'
import {events} from '@/api'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {StationModules, StationPermission, type StationMember} from '@/api/types'
import {formatDateTime} from '@/util/format'
import {localAnswers, type AnswerablePerson} from '@/util/eventAnswers'
import {useSession} from '@/composables/useSession'

const props = defineProps<{
  event: StationEvent
  eventId: number
  fields: EventField[]
  allMembers: StationMember[]
  reminders: number[]
  absentMembers: AbsentMember[]
  focusedDate: string | null
  effectiveDate: string | null
  startFormatted: string
  endFormatted: string
  categoryName: string
  templateName: string
  currentMemberId: number
  registrableMembers: AnswerablePerson[]
  hasManagedMembers: boolean
  canManageEvents: boolean
  canManageAttendance: boolean
  hasPermission: (perm: string) => boolean
  /** The reader's own answers to this date, and those of anybody they answer for. */
  myRegistrations: EventRegistrationEntry[]
  registering: boolean
}>()

const emit = defineEmits<{
  (e: 'cancelled'): void
  (e: 'answers-updated'): void
  (e: 'field-updated', field: EventField): void
  (e: 'register', people: AnswerablePerson[]): void
  (e: 'decline', people: AnswerablePerson[]): void
  (e: 'withdraw', registrationId: number): void
}>()

/**
 * Who the station has today. Read from the same list the info tab names people from, which holds
 * current members only, so anybody who has since left is absent from it by construction.
 */
const currentMemberIds = computed(() => props.allMembers.map(member => member.id))

const {t} = useI18n()
const {isModuleEnabled} = useSession()

/**
 * The questions the appointment asks of whoever registers, which is what makes an answer worth
 * opening again. Read here rather than in the sign-up list, because this block offers to correct an
 * answer long after it was given.
 */
const registrationFields = ref<EventRegistrationField[]>([])

onMounted(async () => {
  if (!props.event.requiresRegistration) return
  registrationFields.value = await events.listRegistrationFields(props.eventId).catch(() => [])
})

const answers = computed(() =>
    localAnswers(props.registrableMembers, props.myRegistrations, registrationFields.value.length > 0))

/**
 * Whether an answer can still be given at all, which is the same rule the buttons follow: an
 * appointment that has to be signed up for stops taking answers at its deadline.
 */
const stillTakingAnswers = computed(() => {
  if (!props.event.requiresRegistration) return true
  if (!props.event.registrationDeadline) return true
  return new Date(props.event.registrationDeadline).getTime() > Date.now()
})

/**
 * Whether the block is worth drawing at all.
 *
 * <p>An answer to show, or one that can still be given. Where the appointment is open to nobody in
 * the household it stays, because the block is then the one place saying why: an empty box is what
 * this used to be on a date whose deadline had passed with nobody having answered.
 */
const showAnswerBlock = computed(() => {
  if (!props.effectiveDate) return false
  if (answers.value.length > 0) return true
  if (props.registrableMembers.length === 0) return true
  return stillTakingAnswers.value
})

const updatingRegistration = ref<EventRegistrationEntry | null>(null)
const showUpdateAnswers = ref(false)

/** Opens the answers of one registration, prefilled with what was given. */
function updateAnswers(registrationId: number) {
  const registration = props.myRegistrations.find(entry => entry.id === registrationId)
  if (!registration) return
  updatingRegistration.value = registration
  showUpdateAnswers.value = true
}

const {running: savingAnswers, error: answersError, run: saveAnswers} = useAsyncAction(
    async (values: RegistrationFieldValue[]) => {
      const registration = updatingRegistration.value
      if (!registration) return
      await events.updateRegistrationFieldValues(registration.id, values)
      showUpdateAnswers.value = false
      updatingRegistration.value = null
      emit('answers-updated')
    },
    {formatError: () => t('common.error')},
)

const activeTab = ref<'info' | 'registrations' | 'equipment'>('info')

/**
 * Whether what the appointment needs can be read at all. The gear lives in the inventory, so a
 * station that has switched it off has nothing to show, and somebody who may not read it is refused
 * by the server: either way the tab would only stand for an answer nobody can get.
 */
const canReadEquipment = computed(() =>
    isModuleEnabled(StationModules.INVENTORY) && props.hasPermission(StationPermission.INVENTORY_READ))

/**
 * What the second tab is called, which follows what the appointment asks of people.
 *
 * <p>Where it has to be signed up for, the tab holds the sign-ups. Where it does not, everybody is
 * expected and the only answer anybody gives is a refusal, so the tab holds who is not coming. It
 * used to be absent entirely on such an appointment, which left the refusals with nowhere to be
 * read: they can be given from the appointment's own page, and then went nowhere anybody looked.
 */
const answerTabLabel = computed(() =>
    props.event.requiresRegistration ? t('eventDetail.tabRegistrations') : t('eventDetail.tabAttendance'))

const tabs = computed(() => {
  const entries = [
    {key: 'info', label: t('eventDetail.tabInfo')},
    {key: 'registrations', label: answerTabLabel.value},
  ]
  if (canReadEquipment.value) {
    entries.push({key: 'equipment', label: t('eventDetail.tabEquipment')})
  }
  return entries
})

const showCancelModal = ref(false)
function onCancelled() {
  showCancelModal.value = false
  emit('cancelled')
}
</script>

<template>
  <div class="space-y-6">
    <Alert v-if="event.cancelled" variant="error">
      <span class="font-bold">{{ t('events.cancelled') }}</span>
      <span v-if="event.cancelReason"> - {{ event.cancelReason }}</span>
      <span v-if="event.cancelledAt" class="text-xs opacity-75 ml-2">{{ formatDateTime(event.cancelledAt) }}</span>
    </Alert>

    <EventDetailHeader
        :event="event"
        :can-manage-events="canManageEvents"
        :can-write-news="hasPermission(StationPermission.NEWS_EDIT)"
        :effective-date="effectiveDate"
        :category-name="categoryName"
        @cancel="showCancelModal = true"
    />

    <div v-if="event.requiresRegistration" class="flex flex-wrap gap-3 text-sm">
      <SuccessBadge>{{ t('events.requiresRegistration') }}</SuccessBadge>
      <InfoBadge v-if="event.requiresConfirmation">{{ t('events.requiresConfirmation') }}</InfoBadge>
      <span v-if="event.registrationDeadline" class="text-(--text-muted)">{{ t('events.registrationDeadline') }}: {{ formatDateTime(event.registrationDeadline) }}</span>
      <span v-if="event.minRegistrations" class="text-(--text-muted)">{{ t('events.minRegistrations') }}: {{ event.minRegistrations }}</span>
      <span v-if="event.thresholdDate" class="text-(--text-muted)">{{ t('events.thresholdDate') }}: {{ formatDateTime(event.thresholdDate) }}</span>
    </div>

    <NeutralContainer v-if="showAnswerBlock" class="space-y-2" data-testid="your-answer">
      <SubHeader>{{ t('eventDetail.yourAnswer') }}</SubHeader>
      <EventRegistrationActions
          :people="registrableMembers"
          :answers="answers"
          :requires-registration="!!event.requiresRegistration"
          :registration-deadline="event.registrationDeadline"
          :has-managed-members="hasManagedMembers"
          :registering="registering"
          @register="people => emit('register', people)"
          @decline="people => emit('decline', people)"
          @withdraw="registrationId => emit('withdraw', registrationId)"
          @update="registrationId => updateAnswers(registrationId)"
      />
    </NeutralContainer>

    <RegistrationFieldsModal
        v-model="showUpdateAnswers"
        :fields="registrationFields"
        :values="updatingRegistration?.fields"
        :title="t('eventDetail.updateAnswer')"
        :confirm-label="t('common.save')"
        :busy="savingAnswers"
        :error="answersError"
        @confirm="saveAnswers"
    />

    <div v-if="reminders.length > 0" class="flex flex-wrap gap-2 text-sm">
      <span class="text-(--text-muted)">{{ t('eventEdit.reminders') }}:</span>
      <InfoBadge v-for="days in reminders" :key="days">{{ days }} {{ t('eventEdit.daysBefore') }}</InfoBadge>
    </div>

    <TabBar v-model="activeTab" :tabs="tabs"/>

    <EventInfoTab
        v-if="activeTab === 'info'"
        :event="event"
        :event-id="eventId"
        :fields="fields"
        :all-members="allMembers"
        :current-member-id="currentMemberId"
        :absent-members="absentMembers"
        :focused-date="focusedDate"
        :start-formatted="startFormatted"
        :end-formatted="endFormatted"
        :template-name="templateName"
        :can-manage-events="canManageEvents"
        :can-manage-attendance="canManageAttendance"
        :has-permission="hasPermission"
        @field-updated="(f) => emit('field-updated', f)"
    />

    <EventRegistrationsTab
        v-show="activeTab === 'registrations'"
        :event="event"
        :event-id="eventId"
        :current-member-id="currentMemberId"
        :registrable-members="registrableMembers"
        :has-managed-members="hasManagedMembers"
        :effective-date="effectiveDate"
        :current-member-ids="currentMemberIds"
    />

    <EventEquipmentTab
        v-if="activeTab === 'equipment' && canReadEquipment"
        :event-id="eventId"
        :effective-date="effectiveDate"
        :recurring="isRecurringEvent(event.eventType)"
        :can-edit="canManageEvents"
    />

    <EventCancelModal
        :show="showCancelModal"
        :event-id="event.id"
        @close="showCancelModal = false"
        @cancelled="onCancelled"
    />
  </div>
</template>
