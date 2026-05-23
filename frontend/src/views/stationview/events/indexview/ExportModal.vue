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
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import type {EventCategory} from '@/api/types'
import {events} from '@/api'

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
    }).catch(() => { /* ignore */ })
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
          <SelectionToggleButton
              v-for="cat in categories"
              :key="cat.id"
              :selected="exportCategoryIds.has(cat.id)"
              @toggle="toggleExportCategory(cat.id)"
          >
            {{ cat.name }}
          </SelectionToggleButton>
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
          <SecondaryButton
              v-for="col in availableColumns"
              :key="col.key"
              class="!text-xs !border !border-dashed !border-bg-light-accent dark:!border-bg-dark-accent !text-(--text-muted) hover:!border-primary hover:!text-primary !bg-transparent"
              @click="addColumn(col)"
          >
            <font-awesome-icon :icon="['fas', 'plus']" class="mr-1 h-3 w-3"/>
            {{ col.label }}
          </SecondaryButton>
        </div>

        <!-- Add event field columns -->
        <div v-if="availableExtraFields.length > 0" class="space-y-1">
          <span class="text-xs text-(--text-muted)">{{ t('events.exportExtraFields') }}</span>
          <div class="flex flex-wrap gap-2">
            <SecondaryButton
                v-for="name in availableExtraFields"
                :key="name"
                class="!text-xs !border !border-dashed !border-secondary/50 !text-secondary hover:!border-secondary hover:!bg-secondary/10 !bg-transparent"
                @click="addFieldColumn(name)"
            >
              <font-awesome-icon :icon="['fas', 'plus']" class="mr-1 h-3 w-3"/>
              {{ name }}
            </SecondaryButton>
          </div>
        </div>
      </div>

      <div class="flex justify-end gap-2 pt-2">
        <SecondaryButton @click="modelValue = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="exporting || selectedColumns.length === 0" @click="doExport">
          <font-awesome-icon :icon="['fas', 'file-export']" class="mr-1"/>
          {{ exporting ? t('common.loading') : t('events.exportPdf') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
