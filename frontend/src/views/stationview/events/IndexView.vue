/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import {useEventRoutes} from '@/composables/useEventRoutes'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import EventDashboardBody from './indexview/EventDashboardBody.vue'
import EventDashboardModals from './indexview/EventDashboardModals.vue'
import type {AttendanceTemplate} from '@/api/attendance'
import type {EventBreak, EventCategory, EventField, StationEvent} from '@/api/events'
import {attendance, events} from '@/api'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useSession} from '@/composables/useSession'
import {StationPermission} from '@/api/types'
import {describeFailure, type Failure} from '@/util/failure'

defineProps<{
  /** The heading, when the station's own wording is not the right one. */
  title?: string
  subtitle?: string
}>()

const {t} = useI18n()
const {hasPermission} = useSession()
const router = useRouter()
const eventRoutes = useEventRoutes()
const allEvents = ref<StationEvent[]>([])
const todayEvents = ref<StationEvent[]>([])
const breaks = ref<EventBreak[]>([])
const categories = ref<EventCategory[]>([])
const templates = ref<AttendanceTemplate[]>([])
const overviewFields = ref<Record<number, EventField[]>>({})

interface CategoryGroup {
  category: EventCategory | null
  events: StationEvent[]
}

const eventsByCategory = computed((): CategoryGroup[] => {
  const groups: CategoryGroup[] = []
  const sorted = [...categories.value].sort((a, b) => a.position - b.position)

  const sortByStart = (a: StationEvent, b: StationEvent) =>
      (a.startTime ?? '').localeCompare(b.startTime ?? '')

  for (const cat of sorted) {
    const catEvents = allEvents.value.filter(e => e.categoryId === cat.id).sort(sortByStart)
    if (catEvents.length > 0) {
      groups.push({category: cat, events: catEvents})
    }
  }

  const uncategorized = allEvents.value.filter(e => !e.categoryId).sort(sortByStart)
  if (uncategorized.length > 0) {
    groups.push({category: null, events: uncategorized})
  }

  return groups
})

const showBreakModal = ref(false)
const editingBreak = ref<EventBreak | null>(null)
const showHolidayModal = ref(false)
const showExportModal = ref(false)

/**
 * Attendance templates belong to the attendance action, which only whoever records it may use.
 * Asking for them alongside the events sank the whole page for everyone else: a member opening the
 * events page got an error instead of the station's events, because one of the calls beside them
 * was refused.
 */
const {loading, failure, reload} = useAsyncLoader(async () => {
  const [ev, today, br, cats, ovFields] = await Promise.all([
    events.listEvents(),
    events.listTodayEvents(),
    events.listBreaks(),
    events.listCategories(),
    events.getOverviewFields(),
  ])
  allEvents.value = ev
  todayEvents.value = today
  breaks.value = br
  categories.value = cats
  overviewFields.value = ovFields

  templates.value = hasPermission(StationPermission.ATTENDANCE_EDIT)
      ? await attendance.listTemplates().catch(() => [])
      : []
})

const {
  show: showDeleteEventModal,
  target: deleteEventTarget,
  requestDelete: requestDeleteEvent,
  confirm: confirmDeleteEvent,
} = useConfirmDelete<StationEvent>({
  onDelete: ev => events.deleteEvent(ev.id),
  onSuccess: () => reload(),
  failure,
})

const {
  show: showDeleteBreakModal,
  target: deleteBreakTarget,
  requestDelete: requestDeleteBreak,
  confirm: confirmDeleteBreak,
} = useConfirmDelete<EventBreak>({
  onDelete: br => events.deleteBreak(br.id),
  onSuccess: () => reload(),
  failure,
})

function openAddEvent() {
  router.push({name: eventRoutes.create})
}

function openEditEvent(ev: StationEvent) {
  router.push({name: eventRoutes.edit, params: {id: ev.id}})
}

function openAddBreak() {
  editingBreak.value = null
  showBreakModal.value = true
}

function openEditBreak(br: EventBreak) {
  editingBreak.value = br
  showBreakModal.value = true
}

/** Puts a failure on the page, whether it happened here or in one of the dialogs. */
function show(described: Failure) {
  failure.value = described
}

function clearFailure() {
  failure.value = null
}

/**
 * Saving the break and reading the dashboard back are answered for separately: a break that was
 * stored and a page that then failed to refresh must not read as a break that was not stored.
 */
async function saveBreak(data: { name: string; startDate: string; endDate: string }) {
  clearFailure()
  try {
    if (editingBreak.value) {
      await events.updateBreak(editingBreak.value.id, data)
    } else {
      await events.createBreak(data)
    }
    showBreakModal.value = false
  } catch (e) {
    show(describeFailure(e, t))
    return
  }
  await reload()
}

async function onImportHolidays(holidays: Array<{ name: string; startDate: string; endDate: string }>) {
  clearFailure()
  try {
    for (const h of holidays) {
      await events.createBreak(h)
    }
    showHolidayModal.value = false
  } catch (e) {
    show(describeFailure(e, t))
    return
  }
  await reload()
}

function goToAttendance(ev: StationEvent) {
  if (ev.templateId) {
    router.push({name: 'attendance-new', query: {templateId: String(ev.templateId), eventId: String(ev.id)}})
  }
}

</script>

<template>
  <ViewContent
      :title="title ?? t('pages.events.title')"
      :subtitle="subtitle ?? t('pages.events.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <FailureAlert :failure="failure"/>

      <EventDashboardBody
          v-if="!loading"
          :today-events="todayEvents"
          :events-by-category="eventsByCategory"
          :has-events="allEvents.length > 0"
          :templates="templates"
          :overview-fields="overviewFields"
          :breaks="breaks"
          @attendance="goToAttendance"
          @add-event="openAddEvent"
          @edit-event="openEditEvent"
          @delete-event="requestDeleteEvent"
          @add-break="openAddBreak"
          @edit-break="openEditBreak"
          @delete-break="requestDeleteBreak"
          @import-holidays="showHolidayModal = true"
          @open-export="showExportModal = true"
      />

      <EventDashboardModals
          v-model:show-export="showExportModal"
          v-model:show-break="showBreakModal"
          v-model:show-holiday="showHolidayModal"
          v-model:show-delete-event="showDeleteEventModal"
          v-model:show-delete-break="showDeleteBreakModal"
          :categories="categories"
          :editing-break="editingBreak"
          :delete-event-target="deleteEventTarget"
          :delete-break-target="deleteBreakTarget"
          @error="show"
          @save-break="saveBreak"
          @import-holidays="onImportHolidays"
          @confirm-delete-event="confirmDeleteEvent"
          @confirm-delete-break="confirmDeleteBreak"
      />
    </div>
  </ViewContent>
</template>
