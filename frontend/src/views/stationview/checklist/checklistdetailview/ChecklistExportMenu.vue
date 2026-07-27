/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, onBeforeUnmount} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import {downloadAuthed} from '@/util/downloadAuthed'

const props = defineProps<{
  checklistId: number
}>()

const {t} = useI18n()
const open = ref(false)

function toggle() {
  open.value = !open.value
}

async function download(suffix: 'csv' | 'pdf') {
  open.value = false
  await downloadAuthed(`/checklist/${props.checklistId}/export.${suffix}`)
}

function onClickOutside(event: MouseEvent) {
  const target = event.target as HTMLElement
  if (!target.closest('.checklist-export-menu')) open.value = false
}

document.addEventListener('click', onClickOutside)
onBeforeUnmount(() => document.removeEventListener('click', onClickOutside))
</script>

<template>
  <div class="relative checklist-export-menu">
    <SecondaryButton @click="toggle">
      <font-awesome-icon :icon="['fas', 'download']" class="mr-1"/>
      {{ t('checklist.export') }}
      <font-awesome-icon :icon="['fas', 'chevron-down']" class="ml-1 h-3 w-3"/>
    </SecondaryButton>
    <div
        v-if="open"
        class="absolute right-0 mt-1 z-10 min-w-[180px] rounded-theme border border-bg-light-accent bg-bg-light shadow dark:border-bg-dark-accent dark:bg-bg-dark"
    >
      <DropdownMenuItem @click="download('csv')">
        <font-awesome-icon :icon="['fas', 'file-csv']" class="mr-2"/>
        {{ t('checklist.exportCsv') }}
      </DropdownMenuItem>
      <DropdownMenuItem @click="download('pdf')">
        <font-awesome-icon :icon="['fas', 'file-pdf']" class="mr-2"/>
        {{ t('checklist.exportPdf') }}
      </DropdownMenuItem>
    </div>
  </div>
</template>
