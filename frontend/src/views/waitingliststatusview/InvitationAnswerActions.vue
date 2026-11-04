/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import RadioInput from '@/components/input/toggle/RadioInput.vue'
import type {WaitingListAnswer, WaitingListPublicAppointment} from '@/api/generated/schema'

/**
 * The three things somebody can say back to an invitation, all one click and no sign-in.
 *
 * They are clicked here rather than followed out of the mail: a one-click link in a mail body is
 * followed by scanners, which would answer on the reader's behalf.
 *
 * An invitation offering several dates asks which one before "I'm coming" can be clicked, and its
 * "the date does not suit" becomes "none of them suits".
 */
const note = defineModel<string>('note', {required: true})
const picked = defineModel<string>('picked', {required: true})

const props = defineProps<{answering: boolean, appointments: WaitingListPublicAppointment[]}>()

const emit = defineEmits<{(e: 'answer', answer: WaitingListAnswer): void}>()

const {t} = useI18n()

const choosing = computed(() => props.appointments.length > 1)
</script>

<template>
  <div class="space-y-3" data-testid="waitlist-answer">
    <fieldset v-if="choosing" class="space-y-1" data-testid="waitlist-answer-pick">
      <legend><FieldLabel>{{ t('waitingList.publicStatus.answerPick') }}</FieldLabel></legend>
      <label
          v-for="(appointment, index) in props.appointments"
          :key="appointment.position"
          class="flex items-center gap-2 text-sm cursor-pointer"
      >
        <RadioInput v-model="picked" :value="String(appointment.position)" :disabled="props.answering" />
        <span>
          {{ t('waitingList.publicStatus.invitationOption', {number: index + 1}) }}:
          {{ appointment.appointmentName }}, {{ appointment.appointmentDate }}
        </span>
      </label>
    </fieldset>
    <div class="space-y-1">
      <FieldLabel>{{ t('waitingList.publicStatus.answerNote') }}</FieldLabel>
      <TextAreaInput v-model="note" :placeholder="t('waitingList.publicStatus.answerNotePlaceholder')" :rows="2" />
    </div>
    <div class="flex flex-col sm:flex-row gap-3">
      <PrimaryButton
          :icon="['fas', 'check']"
          :disabled="props.answering || (choosing && picked === '')"
          class="flex-1"
          data-testid="waitlist-answer-coming"
          @click="emit('answer', 'COMING')"
      >
        {{ t('waitingList.publicStatus.answerComing') }}
      </PrimaryButton>
      <SecondaryButton
          :icon="['fas', 'calendar-days']"
          :disabled="props.answering"
          class="flex-1"
          data-testid="waitlist-answer-date"
          @click="emit('answer', 'DATE_DOES_NOT_SUIT')"
      >
        {{ choosing ? t('waitingList.publicStatus.answerNoneSuits') : t('waitingList.publicStatus.answerDate') }}
      </SecondaryButton>
      <ErrorButton
          :icon="['fas', 'xmark']"
          :disabled="props.answering"
          class="flex-1"
          data-testid="waitlist-answer-no"
          @click="emit('answer', 'NOT_INTERESTED')"
      >
        {{ t('waitingList.publicStatus.answerNotInterested') }}
      </ErrorButton>
    </div>
  </div>
</template>
