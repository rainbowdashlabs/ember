/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {checklists} from '@/api'
import type {ChecklistCellDto, ChecklistNoteHistoryEntry} from '@/api/checklists'
import {formatDateTime} from '@/util/format'
import {describeFailure, type Failure} from '@/util/failure'

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

/**
 * Why the note's history is missing. An empty list used to stand for both "nothing was ever written"
 * and "we could not find out", which are opposite answers to the question the panel is asked.
 */
const historyFailure = ref<Failure | null>(null)

const {running: saving, failure: cellFailure, run: runWriteCell} = useAsyncAction(
    (payload: {checked: boolean; note: string | null}) =>
        checklists.writeCell(props.checklistId, props.entryId, props.columnId, payload),
)

/**
 * Why the tick sprang back.
 *
 * <p>A cell that would not save simply reverted, with no word anywhere: the reader saw the box tick
 * and untick itself and had no way to tell a refused write from a mis-click of their own. On a list
 * whose whole purpose is recording who has done what, a tick that quietly did not stick is the worst
 * possible silence.
 */
const shownFailure = computed(() => (cellFailure.value
    ? {...cellFailure.value, message: t('checklist.cellSaveFailed')}
    : null))

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
  historyFailure.value = null
  try {
    history.value = await checklists.getNoteHistory(props.checklistId, props.entryId, props.columnId)
  } catch (e) {
    history.value = []
    historyFailure.value = {...describeFailure(e, t), message: t('checklist.noteHistoryFailed')}
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
  <div class="flex items-center gap-1.5 flex-wrap">
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

    <FailureAlert :failure="shownFailure" class="basis-full"/>

    <Modal v-model="showNote" size="md">
      <div class="space-y-3">
        <div class="font-semibold">{{ t('checklist.noteSave') }}</div>
        <TextAreaInput v-model="noteDraft" :placeholder="t('checklist.notePlaceholder')" :rows="3"/>
        <ButtonRow pair align="end">
          <SecondaryButton @click="showNote = false">{{ t('checklist.cancel') }}</SecondaryButton>
          <PrimaryButton :disabled="saving" @click="saveNote">{{ t('checklist.noteSave') }}</PrimaryButton>
        </ButtonRow>
        <div class="pt-3 border-t border-bg-light-accent dark:border-bg-dark-accent">
          <div class="font-semibold text-sm mb-1">{{ t('checklist.noteHistory') }}</div>
          <div v-if="loadingHistory" class="text-sm text-(--text-muted)">…</div>
          <FailureAlert v-else-if="historyFailure" :failure="historyFailure"/>
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
