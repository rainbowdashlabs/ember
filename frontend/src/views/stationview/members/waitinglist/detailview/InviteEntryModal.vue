/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TimeInput from '@/components/input/datetime/TimeInput.vue'
import OccurrenceSearchPicker from '@/components/input/search/OccurrenceSearchPicker.vue'
import type {EventOccurrenceRef} from '@/api/events'
import type {WaitingListEntryWithScore} from '@/api/waitingList'

/**
 * The invitation to come and look, which is the one transition that has something to fill in.
 *
 * The evening is optional: a station that has not settled on one yet can still write, and the mail
 * then simply says nothing about when. What it never does is sign anybody up for the appointment.
 */
const occurrence = defineModel<EventOccurrenceRef | null>('occurrence', {required: true})
const arrivalTime = defineModel<string>('arrivalTime', {required: true})

const props = defineProps<{
  target: WaitingListEntryWithScore | null
  running: boolean
}>()

const emit = defineEmits<{
  (e: 'cancel'): void
  (e: 'confirm'): void
}>()

const {t} = useI18n()

function entryFullName(item: WaitingListEntryWithScore): string {
  const e = item.entry
  return e.lastname ? `${e.firstname} ${e.lastname}` : e.firstname
}

function onUpdate(value: boolean) {
  if (!value) emit('cancel')
}
</script>

<template>
  <Modal :model-value="props.target !== null" @update:model-value="onUpdate">
    <div v-if="props.target" class="space-y-4" data-testid="waitlist-invite-modal">
      <SubHeader>{{ t('waitingList.inviteTitle') }}</SubHeader>
      <p class="text-sm">{{ t('waitingList.inviteText', {name: entryFullName(props.target)}) }}</p>

      <div class="space-y-1">
        <FieldLabel>{{ t('waitingList.inviteAppointment') }}</FieldLabel>
        <OccurrenceSearchPicker
            v-model="occurrence"
            :placeholder="t('waitingList.invitePickAppointment')"
            :empty-label="t('waitingList.inviteNoAppointments')"
            testid="waitlist-invite-occurrence"
        />
        <p class="text-xs text-(--text-muted)">{{ t('waitingList.inviteAppointmentHint') }}</p>
      </div>

      <div class="space-y-1">
        <FieldLabel>{{ t('waitingList.inviteArrival') }}</FieldLabel>
        <TimeInput v-model="arrivalTime" />
        <p class="text-xs text-(--text-muted)">{{ t('waitingList.inviteArrivalHint') }}</p>
      </div>

      <div class="flex justify-end gap-2">
        <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="props.running" data-testid="waitlist-invite-send" @click="emit('confirm')">
          {{ props.running ? t('common.loading') : t('waitingList.inviteSend') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
