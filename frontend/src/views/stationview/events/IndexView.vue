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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Modal from '@/components/feedback/Modal.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
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

// -- Export --
const showExportModal = ref(false)
const exporting = ref(false)
const exportMode = ref('year')
const exportYear = ref(String(new Date().getFullYear()))
const exportMonth = ref(String(new Date().getMonth() + 1))
const exportCategoryIds = ref<Set<number>>(new Set())
const availableFieldNames = ref<string[]>([])

interface ExportColumn {
  key: string
  label: string
  isExtra?: boolean
}

const builtinColumns: ExportColumn[] = [
  {key: 'name', label: t('events.exportColName')},
  {key: 'type', label: t('events.exportColType')},
  {key: 'day', label: t('events.exportColDay')},
  {key: 'date', label: t('events.exportColDate')},
  {key: 'time', label: t('events.exportColTime')},
  {key: 'description', label: t('events.exportColDescription')},
]

const selectedColumns = ref<ExportColumn[]>([
  {key: 'name', label: t('events.exportColName')},
  {key: 'day', label: t('events.exportColDay')},
  {key: 'date', label: t('events.exportColDate')},
  {key: 'time', label: t('events.exportColTime')},
])

const availableColumns = computed(() =>
    builtinColumns.filter(c => !selectedColumns.value.some(s => s.key === c.key))
)

function addColumn(col: ExportColumn) {
  selectedColumns.value = [...selectedColumns.value, col]
}

function removeColumn(index: number) {
  selectedColumns.value = selectedColumns.value.filter((_, i) => i !== index)
}

function moveColumnUp(index: number) {
  if (index <= 0) return
  const cols = [...selectedColumns.value]
  const tmp = cols[index - 1]
  cols[index - 1] = cols[index]
  cols[index] = tmp
  selectedColumns.value = cols
}

function moveColumnDown(index: number) {
  if (index >= selectedColumns.value.length - 1) return
  const cols = [...selectedColumns.value]
  const tmp = cols[index + 1]
  cols[index + 1] = cols[index]
  cols[index] = tmp
  selectedColumns.value = cols
}

function addFieldColumn(name: string) {
  if (selectedColumns.value.some(c => c.isExtra && c.label === name)) return
  selectedColumns.value = [...selectedColumns.value, {key: `extra:${name}`, label: name, isExtra: true}]
}

const availableExtraFields = computed(() =>
    availableFieldNames.value.filter(name => !selectedColumns.value.some(c => c.isExtra && c.label === name))
)

function toggleExportCategory(catId: number) {
  const s = new Set(exportCategoryIds.value)
  if (s.has(catId)) s.delete(catId); else s.add(catId)
  exportCategoryIds.value = s
}

async function openExport() {
  exportCategoryIds.value = new Set(categories.value.map(c => c.id))
  try {
    availableFieldNames.value = await events.listFieldNames()
  } catch { /* ignore */ }
  showExportModal.value = true
}

async function doExport() {
  exporting.value = true
  error.value = ''
  try {
    const year = parseInt(exportYear.value)
    const month = parseInt(exportMonth.value)
    let from: string
    let to: string
    if (exportMode.value === 'year') {
      from = `${year}-01-01`
      to = `${year}-12-31`
    } else {
      const m = String(month).padStart(2, '0')
      from = `${year}-${m}-01`
      const lastDay = new Date(year, month, 0).getDate()
      to = `${year}-${m}-${String(lastDay).padStart(2, '0')}`
    }
    const columns = selectedColumns.value.map(c => ({
      type: c.isExtra ? 'field' : 'builtin',
      key: c.isExtra ? undefined : c.key,
      fieldName: c.isExtra ? c.label : undefined,
      label: c.label,
    }))
    const blob = await events.exportEventList({
      categoryIds: [...exportCategoryIds.value],
      columns,
      from,
      to,
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'events.pdf'
    a.click()
    URL.revokeObjectURL(url)
    showExportModal.value = false
  } catch {
    error.value = t('common.error')
  } finally {
    exporting.value = false
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
        <!-- Export Button -->
        <NeutralContainer class="flex items-center justify-between">
          <SectionHeader>{{ t('events.export') }}</SectionHeader>
          <PrimaryButton @click="openExport">
            <font-awesome-icon :icon="['fas', 'file-export']" class="mr-1"/>
            {{ t('events.exportPdf') }}
          </PrimaryButton>
        </NeutralContainer>
      </template>

      <!-- Export Modal -->
      <Modal v-model="showExportModal">
        <div class="space-y-5 p-4 max-h-[80vh] overflow-y-auto">
          <h3 class="text-lg font-semibold">{{ t('events.exportPdf') }}</h3>

          <!-- Time range -->
          <div class="space-y-2">
            <label class="block text-sm font-medium">{{ t('events.exportPeriod') }}</label>
            <div class="flex items-center gap-3 flex-wrap">
              <SelectInput v-model="exportMode" class="w-32">
                <option value="year">{{ t('events.exportYear') }}</option>
                <option value="month">{{ t('events.exportMonth') }}</option>
              </SelectInput>
              <TextInput v-model="exportYear" class="w-24"/>
              <SelectInput v-if="exportMode === 'month'" v-model="exportMonth" class="w-32">
                <option v-for="m in 12" :key="m" :value="String(m)">{{ new Date(2000, m - 1).toLocaleDateString('de-DE', { month: 'long' }) }}</option>
              </SelectInput>
            </div>
          </div>

          <!-- Categories -->
          <div class="space-y-2">
            <label class="block text-sm font-medium">{{ t('events.exportCategories') }}</label>
            <div class="flex flex-wrap gap-2">
              <button
                  v-for="cat in categories"
                  :key="cat.id"
                  :class="exportCategoryIds.has(cat.id)
                  ? 'border-primary bg-primary/10 text-primary ring-1 ring-primary/30'
                  : 'border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted) hover:border-primary'"
                  class="rounded-lg px-3 py-1.5 text-xs font-medium border transition-all"
                  type="button"
                  @click="toggleExportCategory(cat.id)"
              >
                {{ cat.name }}
              </button>
            </div>
          </div>

          <!-- Columns (ordered) -->
          <div class="space-y-3">
            <label class="block text-sm font-medium">{{ t('events.exportColumns') }}</label>

            <!-- Selected columns in order -->
            <div v-if="selectedColumns.length === 0" class="text-sm text-(--text-muted) py-2 text-center">
              {{ t('events.exportNoColumns') }}
            </div>
            <div class="space-y-1">
              <div
                  v-for="(col, index) in selectedColumns"
                  :key="col.key"
                  class="flex items-center gap-2 rounded-lg px-3 py-2 bg-bg-light-accent/30 dark:bg-bg-dark-accent/30"
              >
                <span class="text-(--text-muted) text-xs w-5 text-center">{{ index + 1 }}</span>
                <span class="flex-1 text-sm font-medium">{{ col.label }}</span>
                <IconButton
                    :icon="['fas', 'chevron-up']"
                    label="Move up"
                    :disabled="index === 0"
                    class="text-(--text-muted) hover:text-(--text) h-6 w-6"
                    @click="moveColumnUp(index)"
                />
                <IconButton
                    :icon="['fas', 'chevron-down']"
                    label="Move down"
                    :disabled="index === selectedColumns.length - 1"
                    class="text-(--text-muted) hover:text-(--text) h-6 w-6"
                    @click="moveColumnDown(index)"
                />
                <DeleteButton @click="removeColumn(index)"/>
              </div>
            </div>

            <!-- Add builtin columns -->
            <div v-if="availableColumns.length > 0" class="flex flex-wrap gap-2">
              <button
                  v-for="col in availableColumns"
                  :key="col.key"
                  class="rounded-lg px-3 py-1.5 text-xs font-medium border border-dashed border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted) hover:border-primary hover:text-primary transition-all"
                  type="button"
                  @click="addColumn(col)"
              >
                <font-awesome-icon :icon="['fas', 'plus']" class="mr-1 h-3 w-3"/>
                {{ col.label }}
              </button>
            </div>

            <!-- Add event field columns -->
            <div v-if="availableExtraFields.length > 0" class="space-y-1">
              <span class="text-xs text-(--text-muted)">{{ t('events.exportExtraFields') }}</span>
              <div class="flex flex-wrap gap-2">
                <button
                    v-for="name in availableExtraFields"
                    :key="name"
                    class="rounded-lg px-3 py-1.5 text-xs font-medium border border-dashed border-secondary/50 text-secondary hover:border-secondary hover:bg-secondary/10 transition-all"
                    type="button"
                    @click="addFieldColumn(name)"
                >
                  <font-awesome-icon :icon="['fas', 'plus']" class="mr-1 h-3 w-3"/>
                  {{ name }}
                </button>
              </div>
            </div>
          </div>

          <div class="flex justify-end gap-2 pt-2">
            <SecondaryButton @click="showExportModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="exporting || selectedColumns.length === 0" @click="doExport">
              <font-awesome-icon :icon="['fas', 'file-export']" class="mr-1"/>
              {{ exporting ? t('common.loading') : t('events.exportPdf') }}
            </PrimaryButton>
          </div>
        </div>
      </Modal>

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
