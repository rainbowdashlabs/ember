/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {checklists} from '@/api'
import type {ChecklistNoteHistoryEntry} from '@/api/types'
import {formatDateTime} from '@/util/format'

const props = defineProps<{
  checklistId: number
  entryId: number
  columnId: number
  checked: boolean
  note?: string | null
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'changed'): void
}>()

const {t} = useI18n()

const localChecked = ref(props.checked)
const showNote = ref(false)
const noteDraft = ref(props.note ?? '')
const history = ref<ChecklistNoteHistoryEntry[]>([])
const loadingHistory = ref(false)
const saving = ref(false)

watch(() => props.checked, v => {
  localChecked.value = v
})
watch(() => props.note, v => {
  noteDraft.value = v ?? ''
})

async function pushBoolean(value: boolean) {
  if (props.disabled || saving.value) return
  saving.value = true
  try {
    await checklists.writeCell(props.checklistId, props.entryId, props.columnId, {
      checked: value,
      note: props.note ?? null,
    })
    emit('changed')
  } catch {
    localChecked.value = props.checked
  }
  saving.value = false
}

function onToggle() {
  if (props.disabled) return
  const next = !localChecked.value
  localChecked.value = next
  pushBoolean(next)
}

async function openNote() {
  if (props.disabled) return
  noteDraft.value = props.note ?? ''
  showNote.value = true
  loadingHistory.value = true
  try {
    history.value = await checklists.getNoteHistory(props.checklistId, props.entryId, props.columnId)
  } catch {
    history.value = []
  }
  loadingHistory.value = false
}

async function saveNote() {
  saving.value = true
  try {
    const note = noteDraft.value.trim() === '' ? null : noteDraft.value
    await checklists.writeCell(props.checklistId, props.entryId, props.columnId, {
      checked: localChecked.value,
      note,
    })
    showNote.value = false
    emit('changed')
  } finally {
    saving.value = false
  }
}

function describeHistory(entry: ChecklistNoteHistoryEntry): string {
  if (!entry.newNote || entry.newNote.trim() === '') return t('checklist.noteCleared')
  if (!entry.oldNote || entry.oldNote.trim() === '') return t('checklist.noteAdded')
  return entry.newNote
}
</script>

<template>
  <div class="flex items-center justify-center gap-2">
    <ToggleInput :model-value="localChecked" :disabled="disabled || saving" @update:model-value="onToggle"/>
    <IconButton
        :icon="['fas', 'comment']"
        :label="t('checklist.noteSave')"
        :class="note ? 'text-(--primary)' : 'text-(--text-muted)'"
        :disabled="disabled"
        @click="openNote"
    />

    <Modal v-model="showNote" size="md">
      <div class="space-y-3">
        <div class="font-semibold">{{ t('checklist.noteSave') }}</div>
        <TextAreaInput v-model="noteDraft" :placeholder="t('checklist.notePlaceholder')" rows="3"/>
        <div class="flex justify-end gap-2">
          <SecondaryButton @click="showNote = false">{{ t('checklist.cancel') }}</SecondaryButton>
          <PrimaryButton :disabled="saving" @click="saveNote">{{ t('checklist.noteSave') }}</PrimaryButton>
        </div>
        <div class="pt-3 border-t border-bg-light-accent dark:border-bg-dark-accent">
          <div class="font-semibold text-sm mb-1">{{ t('checklist.noteHistory') }}</div>
          <div v-if="loadingHistory" class="text-sm text-(--text-muted)">…</div>
          <div v-else-if="history.length === 0" class="text-sm text-(--text-muted)">{{ t('checklist.noteHistoryEmpty') }}</div>
          <ul v-else class="space-y-2 max-h-60 overflow-y-auto text-sm">
            <li v-for="entry in history" :key="entry.id" class="border-l-2 border-(--primary) pl-2">
              <div class="text-(--text-muted) text-xs">
                {{ t('checklist.noteHistoryEntry', {
                  who: entry.changedByName ?? t('checklist.noteHistoryDeletedActor'),
                  when: formatDateTime(entry.changedAt),
                }) }}
              </div>
              <div class="whitespace-pre-wrap">{{ describeHistory(entry) }}</div>
            </li>
          </ul>
        </div>
      </div>
    </Modal>
  </div>
</template>
