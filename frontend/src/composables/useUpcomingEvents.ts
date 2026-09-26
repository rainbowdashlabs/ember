/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, watch, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  isRecurringEvent,
  type AllEventRestrictions,
  type EventBreak,
  type EventCategory,
  type EventField,
  type EventRegistrationEntry,
  type RegistrationCount,
  type StationEvent,
  type UpcomingEventOccurrence,
} from '@/api/events'
import type { MemberGroup, StationMember, UserTag } from '@/api/types'
import { events, managedMembers as managedMembersApi, memberGroups, userTags } from '@/api'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useEventAnswer } from '@/composables/useEventAnswer'
import { usePagedList, PAGE_SIZE } from '@/composables/usePagedList'
import { useEventListFilters } from '@/composables/useEventListFilters'
import { describeFailure, saying } from '@/util/failure'
import { toIsoDate } from '@/util/format'

/**
 * The upcoming-events page's data: the paged occurrence list with its filters, the supporting
 * lookups the cards need, and the sign-up actions.
 *
 * The occurrence list is filtered server-side and paged, so changing a filter re-requests the
 * first page rather than narrowing what is already loaded. Registration changes reload only the
 * registrations and their counts, keeping the list itself stable under the user.
 *
 * The tab decides which end of the calendar the list comes from, and nothing else about it changes:
 * the page lists dates either way, so a weekly drill is one row per date on both, hundreds of them
 * behind it included. Every filter applies to whichever tab is open, and all of them live in the
 * address.
 *
 * @param currentMemberId the acting member, whose registrations are sent without an explicit id
 * @param isGuardian      whether the acting member may also register the members they manage
 */
export function useUpcomingEvents(currentMemberId: Ref<number>, isGuardian: () => boolean) {
  const { t } = useI18n()

  const allEvents = ref<StationEvent[]>([])
  const eventBreaks = ref<EventBreak[]>([])
  const todayEvents = ref<StationEvent[]>([])
  const myRegistrations = ref<EventRegistrationEntry[]>([])
  const eligibleMembers = ref<Record<number, number[]>>({})
  const managedMembers = ref<StationMember[]>([])
  const registrationCounts = ref<RegistrationCount[]>([])
  const overviewFields = ref<Record<number, EventField[]>>({})
  const categories = ref<EventCategory[]>([])
  const restrictions = ref<AllEventRestrictions>({})
  const groups = ref<MemberGroup[]>([])
  const tags = ref<UserTag[]>([])

  const showNeedsAction = ref(false)

  const { tab, isPast, searchInput, categoryId, from, to } = useEventListFilters(() => reloadOccurrences())

  /**
   * The end date of an event that spans more than its start day, or {@code null} when it does
   * not. Recurring events never span days - each occurrence is its own entry.
   */
  function multiDayEndDate(event: StationEvent, startDateStr: string): string | null {
    if (isRecurringEvent(event.eventType) || !event.endTime) return null
    const endStr = toIsoDate(new Date(event.endTime))
    return endStr > startDateStr ? endStr : null
  }

  function occurrenceParams(offset: number) {
    return {
      categoryId: categoryId.value ? Number(categoryId.value) : undefined,
      requiresRegistration: showNeedsAction.value ? true : undefined,
      search: searchInput.value.trim() || undefined,
      from: from.value || undefined,
      to: to.value || undefined,
      limit: PAGE_SIZE,
      offset,
    }
  }

  /**
   * The dates the list draws, in the order the server put them: the nearest first while they are
   * still to come, the most recent first once they have passed.
   *
   * <p>One row per date and not one per appointment: a weekly drill is ten rows over the next ten
   * weeks, because the list is a run of what is coming up rather than a register of what exists.
   * Keeping only the next date of each collapsed a station's month into a handful of rows, and the
   * page went on asking for ten more dates and throwing nine of them away.
   *
   * <p>Nothing is reordered on top of the server's order. Multi-day events used to be hoisted to the
   * front as banner rows, which put an event months away above tomorrow's drill.
   */
  const occurrences = usePagedList<UpcomingEventOccurrence>(offset => isPast.value
    ? events.listPastOccurrences(occurrenceParams(offset))
    : events.listUpcomingOccurrences(occurrenceParams(offset)))

  const { loading, failure, reload } = useAsyncLoader(async () => {
    const [today, regs, elig, counts, ovFields, cats, allEv, brs, restr, grps, tgs] = await Promise.all([
      events.listTodayEvents(),
      events.listMyRegistrations(),
      events.listEligibleMembers(),
      events.listRegistrationCounts(),
      events.getOverviewFields(),
      events.listCategories(),
      events.listEvents(),
      events.listBreaks().catch(() => []),
      events.listAllRestrictions().catch(() => ({})),
      memberGroups.listGroups().catch(() => []),
      userTags.listTags().catch(() => []),
      occurrences.load(),
    ])
    todayEvents.value = today
    myRegistrations.value = regs
    eligibleMembers.value = elig
    registrationCounts.value = counts
    overviewFields.value = ovFields
    categories.value = cats
    allEvents.value = allEv
    eventBreaks.value = brs
    restrictions.value = restr
    groups.value = grps
    tags.value = tgs

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
  }

  const {
    registering, answerPrompt, registerFor, declineFor, withdrawRegistration,
    confirmAnswerPrompt, cancelAnswerPrompt,
  } = useEventAnswer(currentMemberId, reloadRegistrations, failure)

  /**
   * Fetches the dates again after a filter or a search changed.
   *
   * <p>It names what could not be read rather than saying nothing happened, and it says that what
   * is on screen is now out of date: the old dates are still drawn, and a reader who is not told so
   * reads them as the answer to the filter they just set.
   */
  async function reloadOccurrences() {
    if (loading.value) return
    try {
      await occurrences.load()
    } catch (e) {
      failure.value = saying(describeFailure(e, t), t('eventsUpcoming.listNotLoaded'))
    }
  }

  async function loadMore() {
    try {
      await occurrences.loadMore()
    } catch (e) {
      failure.value = saying(describeFailure(e, t), t('eventsUpcoming.moreNotLoaded'))
    }
  }

  watch(showNeedsAction, () => reloadOccurrences())

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
    restrictions,
    groups,
    tags,
    tab,
    isPast,
    searchInput,
    categoryId,
    from,
    to,
    showNeedsAction,
    registering,
    occurrences: occurrences.view,
    multiDayEndDate,
    loading,
    failure,
    reload,
    registerFor,
    declineFor,
    withdrawRegistration,
    loadMore,
    answerPrompt,
    confirmAnswerPrompt,
    cancelAnswerPrompt,
  }
}
