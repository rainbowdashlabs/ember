/*
*     SPDX-License-Identifier: AGPL-3.0-only
*
*     Copyright (C) RainbowDashLabs and Contributor
*/
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import TodayEvents from './indexview/TodayEvents.vue'
import EventsByCategory from './indexview/EventsByCategory.vue'
import BreaksList from './indexview/BreaksList.vue'
import BreakModal from './indexview/BreakModal.vue'
import HolidayImportModal from './indexview/HolidayImportModal.vue'
import CategoryModal from './indexview/CategoryModal.vue'
import type {AttendanceTemplate, EventBreak, EventCategory, StationEvent} from '@/api/types'
import {attendance, events} from '@/api'

const {t} = useI18n()
const router = useRouter()
const allEvents = ref<StationEvent[]>([])
const todayEvents = ref<StationEvent[]>([])
const breaks = ref<EventBreak[]>([])
const categories = ref<EventCategory[]>([])
const templates = ref<AttendanceTemplate[]>([])
const loading = ref(true)
const error = ref('')

interface CategoryGroup {
  category: EventCategory | null
  events: StationEvent[]
}

const eventsByCategory = computed((): CategoryGroup[] => {
  const groups: CategoryGroup[] = []
  const sorted = [...categories.value].sort((a, b) => a.position - b.position)

  for (const cat of sorted) {
    const catEvents = allEvents.value.filter(e => e.categoryId === cat.id)
    if (catEvents.length > 0) {
      groups.push({category: cat, events: catEvents})
    }
  }

  const uncategorized = allEvents.value.filter(e => !e.categoryId)
  if (uncategorized.length > 0) {
    groups.push({category: null, events: uncategorized})
  }

  return groups
})

// Modal state (breaks, categories, delete confirmations)
const showBreakModal = ref(false)
const editingBreak = ref<EventBreak | null>(null)
const showHolidayModal = ref(false)
const showCategoryModal = ref(false)
const editingCategory = ref<EventCategory | null>(null)
const showDeleteEventModal = ref(false)
const deleteEventTarget = ref<StationEvent | null>(null)
const showDeleteBreakModal = ref(false)
const deleteBreakTarget = ref<EventBreak | null>(null)

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [ev, today, br, cats, tpl] = await Promise.all([
      events.listEvents(),
      events.listTodayEvents(),
      events.listBreaks(),
      events.listCategories(),
      attendance.listTemplates(),
    ])
    allEvents.value = ev
    todayEvents.value = today
    breaks.value = br
    categories.value = cats
    templates.value = tpl
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

// Event navigation
function openAddEvent() {
  router.push({name: 'event-new'})
}

function openEditEvent(ev: StationEvent) {
  router.push({name: 'event-edit', params: {id: ev.id}})
}

function requestDeleteEvent(ev: StationEvent) {
  deleteEventTarget.value = ev
  showDeleteEventModal.value = true
}

async function confirmDeleteEvent() {
  if (!deleteEventTarget.value) return
  try {
    await events.deleteEvent(deleteEventTarget.value.id)
    showDeleteEventModal.value = false
    deleteEventTarget.value = null
    await loadData()
  } catch {
    error.value = t('common.error')
  }
}

// Break CRUD
function openAddBreak() {
  editingBreak.value = null
  showBreakModal.value = true
}

function openEditBreak(br: EventBreak) {
  editingBreak.value = br
  showBreakModal.value = true
}

async function saveBreak(data: { name: string; startDate: string; endDate: string }) {
  error.value = ''
  try {
    if (editingBreak.value) {
      await events.updateBreak(editingBreak.value.id, data)
    } else {
      await events.createBreak(data)
    }
    showBreakModal.value = false
    await loadData()
  } catch {
    error.value = t('common.error')
  }
}

function requestDeleteBreak(br: EventBreak) {
  deleteBreakTarget.value = br
  showDeleteBreakModal.value = true
}

async function confirmDeleteBreak() {
  if (!deleteBreakTarget.value) return
  try {
    await events.deleteBreak(deleteBreakTarget.value.id)
    showDeleteBreakModal.value = false
    deleteBreakTarget.value = null
    await loadData()
  } catch {
    error.value = t('common.error')
  }
}

// Holiday import
async function onImportHolidays(holidays: Array<{ name: string; startDate: string; endDate: string }>) {
  error.value = ''
  try {
    for (const h of holidays) {
      await events.createBreak(h)
    }
    showHolidayModal.value = false
    await loadData()
  } catch {
    error.value = t('common.error')
  }
}

// Categories
function openAddCategory() {
  editingCategory.value = null
  showCategoryModal.value = true
}

function openEditCategory(cat: EventCategory) {
  editingCategory.value = cat
  showCategoryModal.value = true
}

async function saveCategory(name: string) {
  error.value = ''
  try {
    if (editingCategory.value) {
      await events.updateCategory(editingCategory.value.id, {name, position: editingCategory.value.position})
    } else {
      await events.createCategory({name, position: categories.value.length})
    }
    showCategoryModal.value = false
    await loadData()
  } catch {
    error.value = t('common.error')
  }
}

async function deleteCategory(id: number) {
  try {
    await events.deleteCategory(id)
    await loadData()
  } catch {
    error.value = t('common.error')
  }
}

// Attendance
function goToAttendance(ev: StationEvent) {
  if (ev.templateId) {
    router.push({name: 'attendance-new', query: {templateId: String(ev.templateId), eventId: String(ev.id)}})
  }
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <TodayEvents :events="todayEvents" @attendance="goToAttendance"/>

        <EventsByCategory
            :groups="eventsByCategory"
            :has-events="allEvents.length > 0"
            :templates="templates"
            @add-event="openAddEvent"
            @edit-event="openEditEvent"
            @delete-event="requestDeleteEvent"
            @add-category="openAddCategory"
            @edit-category="openEditCategory"
            @delete-category="deleteCategory"
        />

        <BreaksList
            :breaks="breaks"
            @add="openAddBreak"
            @delete="requestDeleteBreak"
            @edit="openEditBreak"
            @import-holidays="showHolidayModal = true"
        />
      </template>

      <BreakModal
          v-model="showBreakModal"
          :event-break="editingBreak"
          @save="saveBreak"
      />

      <HolidayImportModal
          v-model="showHolidayModal"
          @import="onImportHolidays"
      />

      <CategoryModal
          v-model="showCategoryModal"
          :category="editingCategory"
          @save="saveCategory"
      />

      <ConfirmDeleteModal
          v-model="showDeleteEventModal"
          :message="t('events.deleteEventConfirm', { name: deleteEventTarget?.name })"
          @confirm="confirmDeleteEvent"
      />

      <ConfirmDeleteModal
          v-model="showDeleteBreakModal"
          :message="t('events.deleteBreakConfirm', { name: deleteBreakTarget?.name })"
          @confirm="confirmDeleteBreak"
      />
    </div>
  </ViewContent>
</template>
