/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ExportFormatModal from '@/components/documents/ExportFormatModal.vue'
import {downloadAuthed} from '@/util/downloadAuthed'
import type {ExportFormat, ExportSeparator} from '@/util/exportFormat'

const props = defineProps<{
  checklistId: number
}>()

const {t} = useI18n()

const showFormat = ref(false)

/** The separator only reaches a spreadsheet; a printed sheet has no cells to put one between. */
async function download(format: ExportFormat, separator: ExportSeparator) {
  showFormat.value = false
  const query = format === 'csv' ? `?separator=${separator}` : ''
  await downloadAuthed(`/checklist/${props.checklistId}/export.${format}${query}`)
}
</script>

<template>
  <div class="relative checklist-export-menu">
    <SecondaryButton @click="showFormat = true">
      <font-awesome-icon :icon="['fas', 'download']" class="mr-1"/>
      {{ t('checklist.export') }}
    </SecondaryButton>
    <ExportFormatModal v-model="showFormat" :formats="['csv', 'pdf']" @export="download"/>
  </div>
</template>
