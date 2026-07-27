/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {checklists} from '@/api'
import type {ChecklistCellDto, ChecklistNoteHistoryEntry} from '@/api/types'
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
  (e: 'changed', cell: ChecklistCellDto): void
}>()

const {t} = useI18n()

const localChecked = ref(props.checked)
const showNote = ref(false)
const noteDraft = ref(props.note ?? '')
const history = ref<ChecklistNoteHistoryEntry[]>([])
const loadingHistory = ref(false)

const {running: saving, run: runWriteCell} = useAsyncAction(
    (payload: {checked: boolean; note: string | null}) =>
        checklists.writeCell(props.checklistId, props.entryId, props.columnId, payload),
)

watch(() => props.checked, v => {
  localChecked.value = v
})
watch(() => props.note, v => {
  noteDraft.value = v ?? ''
})

async function pushBoolean(value: boolean) {
  if (props.disabled || saving.value) return
  const cell = await runWriteCell({checked: value, note: props.note ?? null})
  if (cell) {
    emit('changed', cell)
  } else {
    localChecked.value = props.checked
  }
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
  const note = noteDraft.value.trim() === '' ? null : noteDraft.value
  const cell = await runWriteCell({checked: localChecked.value, note})
  if (!cell) return
  showNote.value = false
  emit('changed', cell)
}

function describeHistory(entry: ChecklistNoteHistoryEntry): string {
  if (!entry.newNote || entry.newNote.trim() === '') return t('checklist.noteCleared')
  if (!entry.oldNote || entry.oldNote.trim() === '') return t('checklist.noteAdded')
  return entry.newNote
}
</script>

<template>
  <div class="flex items-center gap-1.5">
    <ToggleInput :model-value="localChecked" :disabled="disabled || saving" @update:model-value="onToggle"/>
    <MutedIconButton
        :icon="['fas', 'comment']"
        :label="t('checklist.noteSave')"
        :disabled="disabled"
        @click="openNote"
    />
    <p
        v-if="note"
        class="flex-1 min-w-0 text-xs leading-snug whitespace-pre-wrap break-words line-clamp-2 text-(--text)"
    >{{ note }}</p>

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
