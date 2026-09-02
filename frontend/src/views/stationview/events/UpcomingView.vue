/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter, type RouteLocationRaw} from 'vue-router'
import {useEventRoutes} from '@/composables/useEventRoutes'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import UpcomingBody from './upcomingview/UpcomingBody.vue'
import EventAnswerDialog from './eventshared/EventAnswerDialog.vue'
import {isRecurringEvent, RegistrationStatus, type StationEvent} from '@/api/events'
import {useSession} from '@/composables/useSession'
import {useUpcomingEvents} from '@/composables/useUpcomingEvents'
import {formatDateTime, formatTime, todayIsoDate} from '@/util/format'
import {answerableMembers, type AnswerablePerson} from '@/util/eventAnswers'

const {t} = useI18n()
const router = useRouter()
const eventRoutes = useEventRoutes()
const {sessionInfo, loaded, isGuardian, canManageAttendance, canManageEvents} = useSession()

const currentMemberId = computed(() => sessionInfo.value?.member?.id ?? 0)

const upcoming = useUpcomingEvents(currentMemberId, isGuardian)
const {
  allEvents, eventBreaks, todayEvents, myRegistrations, eligibleMembers, managedMembers,
  registrationCounts, overviewFields, categories,
  selectedCategoryId, searchQuery, showNeedsAction,
  loadingMore, hasMore, registering, filteredUpcoming, multiDayEndDate,
  loading, error,
  answerPrompt, confirmAnswerPrompt, cancelAnswerPrompt,
} = upcoming

const VIEW_MODE_STORAGE_KEY = 'eventsUpcoming.viewMode'
type ViewMode = 'list' | 'calendar'

function loadInitialViewMode(): ViewMode {
  if (typeof window === 'undefined') return 'list'
  return window.localStorage.getItem(VIEW_MODE_STORAGE_KEY) === 'calendar' ? 'calendar' : 'list'
}

const viewMode = ref<ViewMode>(loadInitialViewMode())
watch(viewMode, (mode) => {
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(VIEW_MODE_STORAGE_KEY, mode)
  }
})

/**
 * Today's events are filtered in the browser - unlike the upcoming list, they are a short fixed
 * set the server already returned in full.
 */
function matchesTextSearch(ev: StationEvent): boolean {
  if (!searchQuery.value) return true
  const q = searchQuery.value.toLowerCase()
  return !!(ev.name?.toLowerCase().includes(q) || ev.description?.toLowerCase().includes(q)
      || overviewFields.value[ev.id]?.some(f => f.name?.toLowerCase().includes(q) || f.value?.toLowerCase().includes(q)))
}

const filteredTodayEvents = computed(() => todayEvents.value.filter(ev => matchesTextSearch(ev)))

function getEligibleMembers(eventId: number): AnswerablePerson[] {
  return answerableMembers(
      eventId,
      eligibleMembers.value,
      currentMemberId.value,
      managedMembers.value,
      t('eventsUpcoming.myself'))
}

function getRegistrationSummary(eventId: number, date: string) {
  const counts = registrationCounts.value.filter(c => c.eventId === eventId && c.eventDate === date)
  const accepted = counts.find(c => c.status === RegistrationStatus.ACCEPTED)?.count ?? 0
  const pending = counts.find(c => c.status === RegistrationStatus.PENDING)?.count ?? 0
  const declined = counts.find(c => c.status === RegistrationStatus.DECLINED)?.count ?? 0
  return {accepted, pending, declined, total: accepted + pending + declined}
}

/**
 * Build the deep link for an event card. Recurring events must carry the occurrence date so the
 * detail view lands on the correct instance (a weekly drill on the 12th vs the 19th). One-time
 * events skip the date - they only have a single occurrence.
 */
function eventDetailRoute(ev: StationEvent, date: string): RouteLocationRaw {
  if (isRecurringEvent(ev.eventType)) {
    return {name: eventRoutes.detailOnDate, params: {id: ev.id, date}}
  }
  return {name: eventRoutes.detail, params: {id: ev.id}}
}

function todayDetailRoute(ev: StationEvent): RouteLocationRaw {
  return eventDetailRoute(ev, todayIsoDate())
}

function openCreateEvent() {
  router.push({name: eventRoutes.create})
}

function goToAttendance(ev: StationEvent) {
  if (ev.templateId) {
    router.push({name: 'attendance-new', query: {templateId: String(ev.templateId), eventId: String(ev.id)}})
  }
}

watch(loaded, (isLoaded) => {
  if (isLoaded) upcoming.reload()
}, {immediate: true})
</script>

<template>
  <ViewContent
      :title="t('pages.events-upcoming.title')"
      :subtitle="t('pages.events-upcoming.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <UpcomingBody
          v-if="!loading"
          :view-mode="viewMode"
          :can-create="canManageEvents()"
          :can-manage-attendance="canManageAttendance()"
          :search-query="searchQuery"
          :selected-category-id="selectedCategoryId"
          :show-needs-action="showNeedsAction"
          :categories="categories"
          :all-events="allEvents"
          :event-breaks="eventBreaks"
          :filtered-today-events="filteredTodayEvents"
          :filtered-upcoming="filteredUpcoming"
          :overview-fields="overviewFields"
          :my-registrations="myRegistrations"
          :managed-members-count="managedMembers.length"
          :registering="registering != null"
          :has-more="hasMore"
          :loading-more="loadingMore"
          :multi-day-end-date="multiDayEndDate"
          :get-registration-summary="getRegistrationSummary"
          :get-eligible-members="getEligibleMembers"
          :today-detail-route="todayDetailRoute"
          :event-detail-route="eventDetailRoute"
          :format-time="formatTime"
          :format-deadline="formatDateTime"
          @update:view-mode="viewMode = $event"
          @update:search="searchQuery = $event"
          @update:category-id="selectedCategoryId = $event"
          @update:needs-action="showNeedsAction = $event"
          @create="openCreateEvent"
          @attendance="goToAttendance"
          @register="upcoming.registerFor"
          @decline="upcoming.declineFor"
          @withdraw="upcoming.withdrawRegistration"
          @load-more="upcoming.loadMore"
      />
    </div>

    <EventAnswerDialog
        :model-value="answerPrompt !== null"
        :people="answerPrompt?.people ?? []"
        :fields="answerPrompt?.fields ?? []"
        :attending="answerPrompt?.attending ?? true"
        :busy="!!registering"
        @update:model-value="shown => { if (!shown) cancelAnswerPrompt() }"
        @confirm="confirmAnswerPrompt"
    />
  </ViewContent>
</template>
