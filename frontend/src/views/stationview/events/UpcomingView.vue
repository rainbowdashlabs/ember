/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import UpcomingBody from './upcomingview/UpcomingBody.vue'
import type {EventBreak, EventCategory, EventField, StationEvent, StationMember} from '@/api/types'
import {isRecurringEvent, RegistrationStatus} from '@/api/types'
import type {RouteLocationRaw} from 'vue-router'
import {events, managedMembers as managedMembersApi} from '@/api'
import type {EventRegistrationEntry, RegistrationCount, UpcomingEventOccurrence} from '@/api/events'
import {useSession} from '@/composables/useSession'
import {useSidebarCounts} from '@/composables/useSidebarCounts'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {formatTime} from '@/util/format'

const {t} = useI18n()
const router = useRouter()
const {sessionInfo, loaded, isGuardian, canManageAttendance, canManageEvents} = useSession()
const {refresh: refreshSidebarCounts} = useSidebarCounts()

const VIEW_MODE_STORAGE_KEY = 'eventsUpcoming.viewMode'
type ViewMode = 'list' | 'calendar'

function loadInitialViewMode(): ViewMode {
  if (typeof window === 'undefined') return 'list'
  const stored = window.localStorage.getItem(VIEW_MODE_STORAGE_KEY)
  return stored === 'calendar' ? 'calendar' : 'list'
}

const viewMode = ref<ViewMode>(loadInitialViewMode())
watch(viewMode, (mode) => {
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(VIEW_MODE_STORAGE_KEY, mode)
  }
})

const allEvents = ref<StationEvent[]>([])
const eventBreaks = ref<EventBreak[]>([])

const todayEvents = ref<StationEvent[]>([])
const upcomingOccurrences = ref<UpcomingEventOccurrence[]>([])

const myRegistrations = ref<EventRegistrationEntry[]>([])
const eligibleMembers = ref<Record<number, number[]>>({})
const managedMembers = ref<StationMember[]>([])
const registrationCounts = ref<RegistrationCount[]>([])
const overviewFields = ref<Record<number, EventField[]>>({})
const categories = ref<EventCategory[]>([])
const selectedCategoryId = ref('')
const searchQuery = ref('')
const showNeedsAction = ref(false)
const loadingMore = ref(false)
const hasMore = ref(true)
const registering = ref<string | null>(null)
const PAGE_SIZE = 10
const dayNames = ['', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag']
const currentMemberId = computed(() => sessionInfo.value?.member?.id ?? 0)

const pad2 = (n: number) => String(n).padStart(2, '0')

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
  return dayNames[dow]
}

function multiDayEndDate(event: StationEvent, startDateStr: string): string | null {
  if (isRecurringEvent(event.eventType)) return null
  if (!event.endTime) return null
  const endStr = new Date(event.endTime).toISOString().slice(0, 10)
  if (endStr <= startDateStr) return null
  return endStr
}

const filteredUpcoming = computed(() => {
  const seenEventIds = new Set<number>()
  const deduped: UpcomingEventOccurrence[] = []
  for (const item of upcomingOccurrences.value) {
    if (seenEventIds.has(item.event.id)) continue
    seenEventIds.add(item.event.id)
    deduped.push(item)
  }
  const multiDay: UpcomingEventOccurrence[] = []
  const singleDay: UpcomingEventOccurrence[] = []
  for (const item of deduped) {
    if (multiDayEndDate(item.event, item.date)) multiDay.push(item)
    else singleDay.push(item)
  }
  return [...multiDay, ...singleDay]
})

function getEligibleMembers(eventId: number): { id: number; name: string }[] {
  const eligible = eligibleMembers.value[eventId]
  const ids = eligible ?? [currentMemberId.value, ...managedMembers.value.map(m => m.id)]
  const result: { id: number; name: string }[] = []
  for (const id of ids) {
    if (id === currentMemberId.value) {
      result.push({id, name: t('eventsUpcoming.myself')})
    } else {
      const m = managedMembers.value.find(mm => mm.id === id)
      if (m) result.push({id, name: m.name ?? m.email ?? `#${id}`})
    }
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


function formatDeadline(iso: string): string {
  const d = new Date(iso)
  return `${pad2(d.getDate())}.${pad2(d.getMonth() + 1)}.${d.getFullYear()} ${pad2(d.getHours())}:${pad2(d.getMinutes())}`
}

function todayIsoDate(): string {
  const d = new Date()
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

/**
 * Build the deep link for an event card. Recurring events must carry the occurrence date so the
 * detail view lands on the correct instance (a weekly drill on the 12th vs the 19th). One-time
 * events skip the date — they only have a single occurrence.
 */
function eventDetailRoute(ev: StationEvent, date: string): RouteLocationRaw {
  if (isRecurringEvent(ev.eventType)) {
    return {name: 'event-detail-date', params: {id: ev.id, date}}
  }
  return {name: 'event-detail', params: {id: ev.id}}
}

function todayDetailRoute(ev: StationEvent): RouteLocationRaw {
  return eventDetailRoute(ev, todayIsoDate())
}

function buildUpcomingParams(offset = 0) {
  const params: { categoryId?: number; requiresRegistration?: boolean; search?: string; limit: number; offset: number } = {
    limit: PAGE_SIZE, offset
  }
  if (selectedCategoryId.value) params.categoryId = Number(selectedCategoryId.value)
  if (showNeedsAction.value) params.requiresRegistration = true
  if (searchQuery.value.trim()) params.search = searchQuery.value.trim()
  return params
}

const {loading, error, reload} = useAsyncLoader(async () => {
  const [upcoming, today, regs, elig, counts, ovFields, cats, allEv, brs] = await Promise.all([
    events.listUpcomingOccurrences(buildUpcomingParams()),
    events.listTodayEvents(),
    events.listMyRegistrations(),
    events.listEligibleMembers(),
    events.listRegistrationCounts(),
    events.getOverviewFields(),
    events.listCategories(),
    events.listEvents(),
    events.listBreaks().catch(() => []),
  ])
  upcomingOccurrences.value = upcoming
  hasMore.value = upcoming.length >= PAGE_SIZE
  todayEvents.value = today
  myRegistrations.value = regs
  eligibleMembers.value = elig
  registrationCounts.value = counts
  overviewFields.value = ovFields
  categories.value = cats
  allEvents.value = allEv
  eventBreaks.value = brs

  if (isGuardian()) {
    const managed = await managedMembersApi.listManaged()
    managedMembers.value = managed.map(m => ({
      id: m.id,
      stationId: m.stationId,
      accountId: m.accountId,
      name: m.name,
      email: m.email
    }))
  }
}, {autoLoad: loaded.value})

async function registerForEvent(ev: StationEvent, date: string, memberId: number) {
  const key = `${ev.id}-${date}-${memberId}`
  registering.value = key
  error.value = ''
  try {
    await events.registerForEvent(ev.id, {
      eventDate: date,
      memberId: memberId !== currentMemberId.value ? memberId : undefined,
    })
    await reloadRegistrations()
    refreshSidebarCounts()
  } catch {
    error.value = t('common.error')
  } finally {
    registering.value = null
  }
}


async function reloadRegistrations() {
  const [regs, counts] = await Promise.all([
    events.listMyRegistrations(),
    events.listRegistrationCounts(),
  ])
  myRegistrations.value = regs
  registrationCounts.value = counts
}

async function withdrawRegistration(regId: number) {
  error.value = ''
  try {
    await events.withdrawRegistration(regId)
    await reloadRegistrations()
    refreshSidebarCounts()
  } catch {
    error.value = t('common.error')
  }
}

async function declineEvent(ev: StationEvent, date: string, memberId: number) {
  error.value = ''
  try {
    await events.declineEvent(ev.id, {
      eventDate: date,
      memberId: memberId !== currentMemberId.value ? memberId : undefined,
    })
    await reloadRegistrations()
    refreshSidebarCounts()
  } catch {
    error.value = t('common.error')
  }
}


function openCreateEvent() {
  router.push({name: 'event-new'})
}

function goToAttendance(ev: StationEvent) {
  if (ev.templateId) {
    router.push({name: 'attendance-new', query: {templateId: String(ev.templateId), eventId: String(ev.id)}})
  }
}

async function reloadUpcoming() {
  if (loading.value) return
  try {
    const upcoming = await events.listUpcomingOccurrences(buildUpcomingParams())
    upcomingOccurrences.value = upcoming
    hasMore.value = upcoming.length >= PAGE_SIZE
  } catch {
    error.value = t('common.error')
  }
}

async function loadMore() {
  loadingMore.value = true
  try {
    const more = await events.listUpcomingOccurrences(buildUpcomingParams(upcomingOccurrences.value.length))
    upcomingOccurrences.value = [...upcomingOccurrences.value, ...more]
    hasMore.value = more.length >= PAGE_SIZE
  } catch {
    error.value = t('common.error')
  } finally {
    loadingMore.value = false
  }
}

watch(selectedCategoryId, () => reloadUpcoming())
watch(showNeedsAction, () => reloadUpcoming())

let searchDebounce: ReturnType<typeof setTimeout> | null = null
watch(searchQuery, () => {
  if (searchDebounce) clearTimeout(searchDebounce)
  searchDebounce = setTimeout(() => reloadUpcoming(), 250)
})

watch(loaded, (isLoaded) => {
  if (isLoaded) {
    reload()
  }
})
</script>

<template>
  <ViewContent>
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
          @register="registerForEvent"
          @decline="declineEvent"
          @withdraw="withdrawRegistration"
          @load-more="loadMore"
      />
    </div>
  </ViewContent>
</template>
