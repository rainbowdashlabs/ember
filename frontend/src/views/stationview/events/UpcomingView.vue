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
import RegistrationFieldsModal from './eventshared/RegistrationFieldsModal.vue'
import {isRecurringEvent, RegistrationStatus, type StationEvent} from '@/api/events'
import {useSession} from '@/composables/useSession'
import {useUpcomingEvents} from '@/composables/useUpcomingEvents'
import {formatTime} from '@/util/format'

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
  fieldPrompt, confirmFieldPrompt, cancelFieldPrompt,
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

const dayNames = ['', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag']
const pad2 = (n: number) => String(n).padStart(2, '0')

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

function dayLabel(dateStr: string): string {
  const d = new Date(dateStr + 'T00:00:00Z')
  const dow = d.getUTCDay() === 0 ? 7 : d.getUTCDay()
  return dayNames[dow] ?? ''
}

function formatDeadline(iso: string): string {
  const d = new Date(iso)
  return `${pad2(d.getDate())}.${pad2(d.getMonth() + 1)}.${d.getFullYear()} ${pad2(d.getHours())}:${pad2(d.getMinutes())}`
}

function todayIsoDate(): string {
  const d = new Date()
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

function getEligibleMembers(eventId: number): { id: number; name: string }[] {
  const eligible = eligibleMembers.value[eventId]
  const ids = eligible ?? [currentMemberId.value, ...managedMembers.value.map(m => m.id)]
  const result: { id: number; name: string }[] = []
  for (const id of ids) {
    if (id === currentMemberId.value) {
      result.push({id, name: t('eventsUpcoming.myself')})
      continue
    }
    const m = managedMembers.value.find(mm => mm.id === id)
    if (m) result.push({id, name: m.name ?? m.email ?? `#${id}`})
  }
  return result
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
          :eligible-members="eligibleMembers"
          :current-member-id="currentMemberId"
          :managed-member-ids="managedMembers.map(m => m.id)"
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
          :day-label="dayLabel"
          :format-time="formatTime"
          :format-deadline="formatDeadline"
          @update:view-mode="viewMode = $event"
          @update:search="searchQuery = $event"
          @update:category-id="selectedCategoryId = $event"
          @update:needs-action="showNeedsAction = $event"
          @create="openCreateEvent"
          @attendance="goToAttendance"
          @register="upcoming.registerForEvent"
          @decline="upcoming.declineEvent"
          @withdraw="upcoming.withdrawRegistration"
          @load-more="upcoming.loadMore"
      />
    </div>

    <RegistrationFieldsModal
        :model-value="fieldPrompt !== null"
        :fields="fieldPrompt?.fields ?? []"
        @update:model-value="v => { if (!v) cancelFieldPrompt() }"
        @confirm="confirmFieldPrompt"
    />
  </ViewContent>
</template>
