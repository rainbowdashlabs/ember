/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryContainer from '@/components/container/PrimaryContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import EventFilterBar from './upcomingview/EventFilterBar.vue'
import type {EventBreak, EventCategory, EventField, StationEvent, StationMember} from '@/api/types'
import {isRecurringEvent, RegistrationStatus} from '@/api/types'
import type {RouteLocationRaw} from 'vue-router'
import EventsCalendar from './upcomingview/EventsCalendar.vue'
import {events, managedMembers as managedMembersApi} from '@/api'
import type {EventRegistrationEntry, RegistrationCount, UpcomingEventOccurrence} from '@/api/events'
import FederatedEventsSection from './upcomingview/FederatedEventsSection.vue'
import EventRegistrationActions from './upcomingview/EventRegistrationActions.vue'
import {useSession} from '@/composables/useSession'
import {useSidebarCounts} from '@/composables/useSidebarCounts'
import MutedText from '@/components/typography/MutedText.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import EventFieldValue from '@/components/display/EventFieldValue.vue'

const {t} = useI18n()
const router = useRouter()
const {sessionInfo, loaded, isGuardian, canManageAttendance} = useSession()
const {refresh: refreshSidebarCounts} = useSidebarCounts()

// Calendar view rolls its own occurrences client-side from the full event list (one-time +
// recurring templates), so it doesn't share the paginated `upcomingOccurrences` list. List
// view stays paginated for snappy initial load on noisy stations.
const VIEW_MODE_STORAGE_KEY = 'eventsUpcoming.viewMode'
type ViewMode = 'list' | 'calendar'

function loadInitialViewMode(): ViewMode {
  if (typeof window === 'undefined') return 'list'
  const stored = window.localStorage.getItem(VIEW_MODE_STORAGE_KEY)
  return stored === 'calendar' ? 'calendar' : 'list'
}

const viewMode = ref<ViewMode>(loadInitialViewMode())
// Persist the choice — next time the user opens the page they land in the same view.
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
// eventId -> eligible memberIds (missing = all eligible)
const eligibleMembers = ref<Record<number, number[]>>({})
const managedMembers = ref<StationMember[]>([])
const registrationCounts = ref<RegistrationCount[]>([])
const overviewFields = ref<Record<number, EventField[]>>({})
const categories = ref<EventCategory[]>([])
const selectedCategoryId = ref('')
const searchQuery = ref('')
const showNeedsAction = ref(false)
const loading = ref(true)
const loadingMore = ref(false)
const hasMore = ref(true)
const error = ref('')
const registering = ref<string | null>(null)
const PAGE_SIZE = 10
const dayNames = ['', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag']
const currentMemberId = computed(() => sessionInfo.value?.member?.id ?? 0)

const pad2 = (n: number) => String(n).padStart(2, '0')
function formatTime(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  return `${pad2(d.getHours())}:${pad2(d.getMinutes())}`
}

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

// For multi-day one-time events, returns the YYYY-MM-DD of the endTime if it differs from
// the start date; otherwise null. Recurring events always end on the same day.
function multiDayEndDate(event: StationEvent, startDateStr: string): string | null {
  if (!event.endTime) return null
  const endStr = new Date(event.endTime).toISOString().slice(0, 10)
  return endStr !== startDateStr ? endStr : null
}

// Upcoming list is server-filtered (category + needs-action + search), so we render it as-is.
// Today's list and the calendar still apply matchesTextSearch client-side since those endpoints
// don't take a search param yet.
const filteredUpcoming = computed(() => upcomingOccurrences.value)

function getEligibleMembers(eventId: number): { id: number; name: string }[] {
  const eligible = eligibleMembers.value[eventId]
  // No restriction = self + managed
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

function buildUpcomingParams(offset = 0) {
  const params: { categoryId?: number; requiresRegistration?: boolean; search?: string; limit: number; offset: number } = {
    limit: PAGE_SIZE, offset
  }
  if (selectedCategoryId.value) params.categoryId = Number(selectedCategoryId.value)
  if (showNeedsAction.value) params.requiresRegistration = true
  if (searchQuery.value.trim()) params.search = searchQuery.value.trim()
  return params
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [upcoming, today, regs, elig, counts, ovFields, cats, allEv, brs] = await Promise.all([
      events.listUpcomingOccurrences(buildUpcomingParams()),
      events.listTodayEvents(),
      events.listMyRegistrations(),
      events.listEligibleMembers(),
      events.listRegistrationCounts(),
      events.getOverviewFields(),
      events.listCategories(),
      // Calendar view needs the full event list (one-time + recurring templates) to compute
      // occurrences for any visible month. Cheap-bounded fetch — events are per-station.
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
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

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

// Search hits the backend now (debounced so each keystroke doesn't fire a request).
let searchDebounce: ReturnType<typeof setTimeout> | null = null
watch(searchQuery, () => {
  if (searchDebounce) clearTimeout(searchDebounce)
  searchDebounce = setTimeout(() => reloadUpcoming(), 250)
})

onMounted(() => {
  if (loaded.value) {
    loadData()
  }
})

watch(loaded, (isLoaded) => {
  if (isLoaded && loading.value) {
    loadData()
  }
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <!-- View switcher: list (default, paginated, full registration controls) vs calendar
             (month grid, always 7 days per row, fills with prev/next month dates). -->
        <div class="flex justify-end gap-2">
          <SelectionToggleButton :selected="viewMode === 'list'" @toggle="viewMode = 'list'">
            <font-awesome-icon :icon="['fas', 'list']" class="mr-1"/>
            {{ t('eventsUpcoming.viewList') }}
          </SelectionToggleButton>
          <SelectionToggleButton :selected="viewMode === 'calendar'" @toggle="viewMode = 'calendar'">
            <font-awesome-icon :icon="['fas', 'calendar-days']" class="mr-1"/>
            {{ t('eventsUpcoming.viewCalendar') }}
          </SelectionToggleButton>
        </div>

        <!-- Filters (apply to both views) -->
        <EventFilterBar
            v-model:search="searchQuery"
            v-model:category-id="selectedCategoryId"
            v-model:needs-action="showNeedsAction"
            :categories="categories"
        />

        <!-- Calendar view -->
        <EventsCalendar
            v-if="viewMode === 'calendar'"
            :all-events="allEvents"
            :event-breaks="eventBreaks"
            :eligible-member-ids="eligibleMembers"
            :current-member-id="currentMemberId"
            :managed-member-ids="managedMembers.map(m => m.id)"
            :selected-category-id="selectedCategoryId"
            :search-query="searchQuery"
        />

        <!-- Today -->
        <template v-if="viewMode === 'list'">
        <div v-if="filteredTodayEvents.length > 0" class="space-y-3">
          <SectionHeader>{{ t('eventsUpcoming.today') }}</SectionHeader>
          <div class="grid gap-3 sm:grid-cols-2">
            <PrimaryContainer v-for="ev in filteredTodayEvents" :key="ev.id" class="space-y-2">
              <div class="flex items-center justify-between">
                <router-link :to="eventDetailRoute(ev, todayIsoDate())" class="font-semibold text-primary hover:underline">{{ ev.name }}</router-link>
                <MutedIcon v-if="ev.restricted" :icon="['fas', 'lock']" class="ml-1"/>
                <span class="text-sm">{{ formatTime(ev.startTime) }} – {{ formatTime(ev.endTime) }}</span>
              </div>
              <p v-if="ev.description" class="text-sm text-(--text-muted)">{{ ev.description }}</p>
              <div v-if="overviewFields[ev.id]?.length" class="flex flex-wrap gap-3 text-xs">
                <span v-for="f in overviewFields[ev.id]" :key="f.id" class="text-(--text-muted)">
                  <span class="font-medium">{{ f.name }}:</span>
                  <EventFieldValue :field-type="f.fieldType" :value="f.value"/>
                </span>
              </div>
              <div class="flex gap-2">
                <PrimaryButton :icon="['fas', 'clipboard-user']" v-if="ev.templateId && canManageAttendance()"
                               @click="goToAttendance(ev)">
                  {{ t('eventsUpcoming.attendance') }}
                </PrimaryButton>
              </div>
            </PrimaryContainer>
          </div>
        </div>

        <EmptyState compact v-if="filteredTodayEvents.length === 0">{{ t('eventsUpcoming.noToday') }}</EmptyState>

        <!-- Federated Events -->
        <FederatedEventsSection/>

        <!-- Upcoming -->
        <div v-if="filteredUpcoming.length > 0" class="space-y-3">
          <SectionHeader>{{ t('eventsUpcoming.upcoming') }}</SectionHeader>
          <div class="space-y-2">
            <NeutralContainer v-for="item in filteredUpcoming" :key="`${item.event.id}-${item.date}`" class="space-y-2">
              <div class="flex items-center justify-between flex-wrap gap-2">
                <div>
                  <router-link :to="eventDetailRoute(item.event, item.date)" class="font-medium text-primary hover:underline">{{ item.event.name }}</router-link>
                  <MutedIcon v-if="item.event.restricted" :icon="['fas', 'lock']" class="ml-1"/>
                  <MutedText size="sm" class="ml-2">
                    {{ dayLabel(item.date) }}, {{ item.date }}<template v-if="multiDayEndDate(item.event, item.date)"> – {{ dayLabel(multiDayEndDate(item.event, item.date)!) }}, {{ multiDayEndDate(item.event, item.date) }}</template>
                  </MutedText>
                  <MutedText class="ml-2">{{ formatTime(item.event.startTime) }} – {{ formatTime(item.event.endTime) }}</MutedText>
                  <MutedText v-if="item.event.requiresRegistration && item.event.registrationDeadline" class="ml-2 text-xs text-(--text-muted)">({{ t('eventsUpcoming.deadline') }}: {{ formatDeadline(item.event.registrationDeadline) }})</MutedText>
                  <p v-if="item.event.description" class="text-sm text-(--text-muted) mt-0.5">{{ item.event.description }}</p>
                  <div v-if="overviewFields[item.event.id]?.length" class="flex flex-wrap gap-3 text-xs mt-1">
                    <span v-for="f in overviewFields[item.event.id]" :key="f.id" class="text-(--text-muted)"><span class="font-medium">{{ f.name }}:</span> <EventFieldValue :field-type="f.fieldType" :value="f.value"/></span>
                  </div>
                </div>
                <!-- Registration counts -->
                <div v-if="getRegistrationSummary(item.event.id, item.date).total > 0" class="flex items-center gap-2 text-xs">
                  <SuccessBadge v-if="getRegistrationSummary(item.event.id, item.date).accepted">{{ getRegistrationSummary(item.event.id, item.date).accepted }} {{ t('eventsUpcoming.accepted') }}</SuccessBadge>
                  <InfoBadge v-if="getRegistrationSummary(item.event.id, item.date).pending">{{ getRegistrationSummary(item.event.id, item.date).pending }} {{ t('eventsUpcoming.pendingCount') }}</InfoBadge>
                  <ErrorBadge v-if="getRegistrationSummary(item.event.id, item.date).declined">{{ getRegistrationSummary(item.event.id, item.date).declined }} {{ t('eventsUpcoming.declinedCount') }}</ErrorBadge>
                </div>
              </div>
              <EventRegistrationActions
                  :eligible-members="getEligibleMembers(item.event.id)"
                  :registrations="myRegistrations.filter(r => r.eventId === item.event.id && r.eventDate === item.date)"
                  :requires-registration="!!item.event.requiresRegistration"
                  :has-managed-members="managedMembers.length > 0"
                  :registering="registering != null"
                  @register="registerForEvent(item.event, item.date, $event)"
                  @decline="declineEvent(item.event, item.date, $event)"
                  @withdraw="withdrawRegistration($event)"
              />
            </NeutralContainer>
          </div>
          <div v-if="hasMore" class="flex justify-center pt-2">
            <SecondaryButton :disabled="loadingMore" @click="loadMore">
              <Spinner v-if="loadingMore" size="sm" class="mr-2"/>
              {{ t('eventsUpcoming.loadMore') }}
            </SecondaryButton>
          </div>
        </div>
        </template>
      </template>
    </div>
  </ViewContent>
</template>
