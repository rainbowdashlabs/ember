/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref, watch, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  isRecurringEvent,
  type EventBreak,
  type EventCategory,
  type EventField,
  type EventRegistrationEntry,
  type RegistrationCount,
  type StationEvent,
  type UpcomingEventOccurrence,
} from '@/api/events'
import type { StationMember } from '@/api/types'
import { events, managedMembers as managedMembersApi } from '@/api'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useSidebarCounts } from '@/composables/useSidebarCounts'

const PAGE_SIZE = 10
const SEARCH_DEBOUNCE_MS = 250

/**
 * The upcoming-events page's data: the paged occurrence list with its filters, the supporting
 * lookups the cards need, and the sign-up actions.
 *
 * The occurrence list is filtered server-side and paged, so changing a filter re-requests the
 * first page rather than narrowing what is already loaded. Registration changes reload only the
 * registrations and their counts, keeping the list itself stable under the user.
 *
 * @param currentMemberId the acting member, whose registrations are sent without an explicit id
 * @param isGuardian      whether the acting member may also register the members they manage
 */
export function useUpcomingEvents(currentMemberId: Ref<number>, isGuardian: () => boolean) {
  const { t } = useI18n()
  const { refresh: refreshSidebarCounts } = useSidebarCounts()

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

  /**
   * The end date of an event that spans more than its start day, or {@code null} when it does
   * not. Recurring events never span days — each occurrence is its own entry.
   */
  function multiDayEndDate(event: StationEvent, startDateStr: string): string | null {
    if (isRecurringEvent(event.eventType) || !event.endTime) return null
    const endStr = new Date(event.endTime).toISOString().slice(0, 10)
    return endStr > startDateStr ? endStr : null
  }

  /**
   * The occurrences to render: one entry per event, multi-day ones first so they read as the
   * banner rows above the single-day list.
   */
  const filteredUpcoming = computed(() => {
    const seen = new Set<number>()
    const multiDay: UpcomingEventOccurrence[] = []
    const singleDay: UpcomingEventOccurrence[] = []
    for (const item of upcomingOccurrences.value) {
      if (seen.has(item.event.id)) continue
      seen.add(item.event.id)
      if (multiDayEndDate(item.event, item.date)) multiDay.push(item)
      else singleDay.push(item)
    }
    return [...multiDay, ...singleDay]
  })

  function buildUpcomingParams(offset = 0) {
    const params: {
      categoryId?: number
      requiresRegistration?: boolean
      search?: string
      limit: number
      offset: number
    } = {limit: PAGE_SIZE, offset}
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

    if (!isGuardian()) return
    managedMembers.value = (await managedMembersApi.listManaged()).map(m => ({
      id: m.id,
      stationId: m.stationId,
      accountId: m.accountId,
      name: m.name,
      email: m.email,
    }))
  }, {autoLoad: false})

  async function reloadRegistrations() {
    const [regs, counts] = await Promise.all([
      events.listMyRegistrations(),
      events.listRegistrationCounts(),
    ])
    myRegistrations.value = regs
    registrationCounts.value = counts
    refreshSidebarCounts()
  }

  /**
   * Runs a registration change and refreshes the registrations it affected, reporting failure on
   * the shared error channel rather than throwing.
   */
  async function changeRegistration(action: () => Promise<unknown>) {
    error.value = ''
    try {
      await action()
      await reloadRegistrations()
    } catch {
      error.value = t('common.error')
    }
  }

  /**
   * The member id to send: omitted for the acting member, explicit for a managed one.
   */
  function memberIdParam(memberId: number): number | undefined {
    return memberId !== currentMemberId.value ? memberId : undefined
  }

  async function registerForEvent(ev: StationEvent, date: string, memberId: number) {
    registering.value = `${ev.id}-${date}-${memberId}`
    try {
      await changeRegistration(() =>
        events.registerForEvent(ev.id, {eventDate: date, memberId: memberIdParam(memberId)}))
    } finally {
      registering.value = null
    }
  }

  async function declineEvent(ev: StationEvent, date: string, memberId: number) {
    await changeRegistration(() =>
      events.declineEvent(ev.id, {eventDate: date, memberId: memberIdParam(memberId)}))
  }

  async function withdrawRegistration(regId: number) {
    await changeRegistration(() => events.withdrawRegistration(regId))
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
      const more = await events.listUpcomingOccurrences(
        buildUpcomingParams(upcomingOccurrences.value.length))
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
    searchDebounce = setTimeout(() => reloadUpcoming(), SEARCH_DEBOUNCE_MS)
  })

  return {
    allEvents,
    eventBreaks,
    todayEvents,
    myRegistrations,
    eligibleMembers,
    managedMembers,
    registrationCounts,
    overviewFields,
    categories,
    selectedCategoryId,
    searchQuery,
    showNeedsAction,
    loadingMore,
    hasMore,
    registering,
    filteredUpcoming,
    multiDayEndDate,
    loading,
    error,
    reload,
    registerForEvent,
    declineEvent,
    withdrawRegistration,
    loadMore,
  }
}
