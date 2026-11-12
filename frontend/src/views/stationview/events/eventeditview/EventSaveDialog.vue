/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import RadioInput from '@/components/input/toggle/RadioInput.vue'
import EventSaveScopeChoice from './EventSaveScopeChoice.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import type {Failure} from '@/util/failure'
import {EditScopeKind, type ChangePreview} from '@/api/generated/schema'

const open = defineModel<boolean>({required: true})
const kind = defineModel<EditScopeKind>('kind', {required: true})
const date = defineModel<string>('date', {required: true})
const tell = defineModel<boolean>('tell', {required: true})
const note = defineModel<string>('note', {required: true})

const props = defineProps<{
  /** Whether the appointment repeats, which is when the dates the change applies to can be chosen. */
  series: boolean
  /** What the server counted for the current choice, or nothing while it is asked. */
  preview: ChangePreview | null
  /** Why the server would not count the current choice, such as a day the series does not fall on. */
  previewFailure?: Failure | null
  /** Whether files were changed, which already reached every date, so only every date can be chosen. */
  filesChanged?: boolean
  /** Whether there is anything to tell about. */
  canTell: boolean
  saving: boolean
}>()

const emit = defineEmits<{
  save: []
}>()

const {t} = useI18n()

/** The limit the server holds a note to. */
const NOTE_LIMIT = 500

const tellChoice = computed({
  get: () => tell.value ? 'tell' : 'quiet',
  set: (value: string) => { tell.value = value === 'tell' },
})

const needsDate = computed(() => props.series && !props.filesChanged && kind.value !== EditScopeKind.ALL)
const noteTooLong = computed(() => note.value.trim().length > NOTE_LIMIT)
const movedPlaces = computed(() => props.preview?.startMoves ? props.preview.moveAudience : 0)
const blocked = computed(() =>
  props.saving || (needsDate.value && !date.value) || noteTooLong.value || !!props.previewFailure)
</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4">
      <SubHeader>{{ t('events.saveDialog.title') }}</SubHeader>

      <EventSaveScopeChoice v-if="series && !filesChanged" v-model:kind="kind" v-model:date="date"/>
      <p v-if="series && filesChanged" class="text-sm text-(--text-muted)">{{ t('events.saveDialog.filesChanged') }}</p>
      <FailureAlert :failure="previewFailure ?? null" expected/>

      <div class="space-y-2">
        <FieldLabel>{{ t('events.saveDialog.notice') }}</FieldLabel>
        <FieldLabel inline class="cursor-pointer">
          <RadioInput v-model="tellChoice" value="quiet"/>
          {{ t('events.saveDialog.quiet') }}
        </FieldLabel>
        <FieldLabel inline class="cursor-pointer">
          <RadioInput v-model="tellChoice" value="tell" :disabled="!canTell"/>
          {{ t('events.saveDialog.tell', {count: preview?.noticeAudience ?? 0}) }}
        </FieldLabel>
        <div v-if="tell" class="space-y-1">
          <FieldLabel>{{ t('events.saveDialog.note') }}</FieldLabel>
          <TextAreaInput v-model="note" :rows="3" :placeholder="t('events.saveDialog.notePlaceholder')"/>
          <p v-if="noteTooLong" class="text-xs text-(--color-error)">{{ t('events.saveDialog.noteTooLong', {limit: NOTE_LIMIT}) }}</p>
        </div>
      </div>

      <Alert v-if="movedPlaces > 0" variant="info">
        {{ t('events.saveDialog.moved', {count: movedPlaces, time: preview?.newStart ?? ''}) }}
      </Alert>

      <ButtonRow pair align="end">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :icon="['fas', 'floppy-disk']" :disabled="blocked" @click="emit('save')">
          {{ t('common.save') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
