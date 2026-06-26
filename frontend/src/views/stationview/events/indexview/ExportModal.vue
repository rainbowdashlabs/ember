/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {EventCategory} from '@/api/types'
import {events} from '@/api'
import TimeRangeSection from './exportmodal/TimeRangeSection.vue'
import CategoriesSection from './exportmodal/CategoriesSection.vue'
import ColumnsSection, {type ExportColumn} from './exportmodal/ColumnsSection.vue'

const {t} = useI18n()

const props = defineProps<{
  categories: EventCategory[]
}>()

const modelValue = defineModel<boolean>({required: true})

const emit = defineEmits<{
  error: [message: string]
}>()

const exporting = ref(false)
const exportMode = ref('year')
const exportYear = ref(String(new Date().getFullYear()))
const exportMonth = ref(String(new Date().getMonth() + 1))
const exportCategoryIds = ref<Set<number>>(new Set())
const availableFieldNames = ref<string[]>([])

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

const availableExtraFields = computed(() =>
    availableFieldNames.value.filter(name => !selectedColumns.value.some(c => c.isExtra && c.label === name))
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

function toggleExportCategory(catId: number) {
  const s = new Set(exportCategoryIds.value)
  if (s.has(catId)) s.delete(catId); else s.add(catId)
  exportCategoryIds.value = s
}

watch(modelValue, (visible) => {
  if (visible) {
    exportCategoryIds.value = new Set(props.categories.map(c => c.id))
    events.listFieldNames().then(names => {
      availableFieldNames.value = names
    }).catch(() => {})
  }
})

async function doExport() {
  exporting.value = true
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
    modelValue.value = false
  } catch {
    emit('error', t('common.error'))
  } finally {
    exporting.value = false
  }
}
</script>

<template>
  <Modal v-model="modelValue">
    <div class="space-y-5 p-4 max-h-[80vh] overflow-y-auto">
      <SubHeader>{{ t('events.exportPdf') }}</SubHeader>

      <TimeRangeSection
          v-model:export-mode="exportMode"
          v-model:export-year="exportYear"
          v-model:export-month="exportMonth"
      />

      <CategoriesSection
          :categories="categories"
          :selected-ids="exportCategoryIds"
          @toggle="toggleExportCategory"
      />

      <ColumnsSection
          :selected-columns="selectedColumns"
          :available-columns="availableColumns"
          :available-extra-fields="availableExtraFields"
          @add="addColumn"
          @add-field="addFieldColumn"
          @remove="removeColumn"
          @move-up="moveColumnUp"
          @move-down="moveColumnDown"
      />

      <div class="flex justify-end gap-2 pt-2">
        <SecondaryButton @click="modelValue = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :icon="['fas', 'file-export']" :disabled="exporting || selectedColumns.length === 0" @click="doExport">
          {{ exporting ? t('common.loading') : t('events.exportPdf') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
