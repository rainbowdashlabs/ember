/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import Alert from '@/components/feedback/Alert.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import EventCancelModal from './EventCancelModal.vue'
import EventRegistrationsTab from './EventRegistrationsTab.vue'
import EventDetailHeader from './EventDetailHeader.vue'
import EventInfoTab from './EventInfoTab.vue'
import type {AbsentMember, EventField, StationEvent} from '@/api/events'
import type {StationMember} from '@/api/types'
import {formatDateTime} from '@/util/format'

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
  registrableMembers: { id: number; name: string }[]
  hasManagedMembers: boolean
  canManageEvents: boolean
  canManageAttendance: boolean
  hasPermission: (perm: string) => boolean
}>()

const emit = defineEmits<{
  (e: 'cancelled'): void
  (e: 'field-updated', field: EventField): void
}>()

const {t} = useI18n()

const activeTab = ref<'info' | 'registrations'>('info')
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

    <div v-if="reminders.length > 0" class="flex flex-wrap gap-2 text-sm">
      <span class="text-(--text-muted)">{{ t('eventEdit.reminders') }}:</span>
      <InfoBadge v-for="days in reminders" :key="days">{{ days }} {{ t('eventEdit.daysBefore') }}</InfoBadge>
    </div>

    <TabBar v-if="event.requiresRegistration" v-model="activeTab"
            :tabs="[{key: 'info', label: t('eventDetail.tabInfo')}, {key: 'registrations', label: t('eventDetail.tabRegistrations')}]" />

    <EventInfoTab
        v-if="activeTab === 'info' || !event.requiresRegistration"
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
        v-show="activeTab === 'registrations' && event.requiresRegistration"
        :event="event"
        :event-id="eventId"
        :current-member-id="currentMemberId"
        :registrable-members="registrableMembers"
        :has-managed-members="hasManagedMembers"
        :next-occurrence-date="effectiveDate"
    />

    <EventCancelModal
        :show="showCancelModal"
        :event-id="event.id"
        @close="showCancelModal = false"
        @cancelled="onCancelled"
    />
  </div>
</template>
