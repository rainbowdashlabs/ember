/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import BreakModal from './BreakModal.vue'
import HolidayImportModal from './HolidayImportModal.vue'
import ExportModal from './ExportModal.vue'
import type {EventBreak, EventCategory, StationEvent} from '@/api/types'

const {t} = useI18n()

const showExport = defineModel<boolean>('showExport', {required: true})
const showBreak = defineModel<boolean>('showBreak', {required: true})
const showHoliday = defineModel<boolean>('showHoliday', {required: true})
const showDeleteEvent = defineModel<boolean>('showDeleteEvent', {required: true})
const showDeleteBreak = defineModel<boolean>('showDeleteBreak', {required: true})

defineProps<{
  categories: EventCategory[]
  editingBreak: EventBreak | null
  deleteEventTarget: StationEvent | null
  deleteBreakTarget: EventBreak | null
}>()

defineEmits<{
  (e: 'error', message: string): void
  (e: 'save-break', data: { name: string; startDate: string; endDate: string }): void
  (e: 'import-holidays', holidays: Array<{ name: string; startDate: string; endDate: string }>): void
  (e: 'confirm-delete-event'): void
  (e: 'confirm-delete-break'): void
}>()
</script>

<template>
  <ExportModal v-model="showExport" :categories="categories" @error="e => $emit('error', e)"/>

  <BreakModal
      v-model="showBreak"
      :event-break="editingBreak"
      @save="data => $emit('save-break', data)"
  />

  <HolidayImportModal
      v-model="showHoliday"
      @import="holidays => $emit('import-holidays', holidays)"
  />

  <ConfirmDeleteModal
      v-model="showDeleteEvent"
      :message="t('events.deleteEventConfirm', { name: deleteEventTarget?.name })"
      @confirm="$emit('confirm-delete-event')"
  />

  <ConfirmDeleteModal
      v-model="showDeleteBreak"
      :message="t('events.deleteBreakConfirm', { name: deleteBreakTarget?.name })"
      @confirm="$emit('confirm-delete-break')"
  />
</template>
