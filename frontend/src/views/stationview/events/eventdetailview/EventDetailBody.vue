/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import Alert from '@/components/feedback/Alert.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import EventCancelModal from './EventCancelModal.vue'
import EventRegistrationsTab from './EventRegistrationsTab.vue'
import EventDetailHeader from './EventDetailHeader.vue'
import EventInfoTab from './EventInfoTab.vue'
import EventRegistrationActions from '../eventshared/EventRegistrationActions.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {AbsentMember, EventField, EventRegistrationEntry, StationEvent} from '@/api/events'
import type {StationMember} from '@/api/types'
import {formatDateTime} from '@/util/format'
import {localAnswers, type AnswerablePerson} from '@/util/eventAnswers'

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
  (e: 'field-updated', field: EventField): void
  (e: 'register', people: AnswerablePerson[]): void
  (e: 'decline', people: AnswerablePerson[]): void
  (e: 'withdraw', registrationId: number): void
}>()

const answers = computed(() => localAnswers(props.registrableMembers, props.myRegistrations))

/**
 * Who the station has today. Read from the same list the info tab names people from, which holds
 * current members only, so anybody who has since left is absent from it by construction.
 */
const currentMemberIds = computed(() => props.allMembers.map(member => member.id))

const {t} = useI18n()

const activeTab = ref<'info' | 'registrations'>('info')

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

    <NeutralContainer v-if="effectiveDate" class="space-y-2">
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
      />
    </NeutralContainer>

    <div v-if="reminders.length > 0" class="flex flex-wrap gap-2 text-sm">
      <span class="text-(--text-muted)">{{ t('eventEdit.reminders') }}:</span>
      <InfoBadge v-for="days in reminders" :key="days">{{ days }} {{ t('eventEdit.daysBefore') }}</InfoBadge>
    </div>

    <TabBar v-model="activeTab" :tabs="[{key: 'info', label: t('eventDetail.tabInfo')}, {key: 'registrations', label: answerTabLabel}]" />

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

    <EventCancelModal
        :show="showCancelModal"
        :event-id="event.id"
        @close="showCancelModal = false"
        @cancelled="onCancelled"
    />
  </div>
</template>
