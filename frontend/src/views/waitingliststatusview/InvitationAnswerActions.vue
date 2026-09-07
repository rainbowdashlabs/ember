/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import type {WaitingListAnswerName} from '@/api/waitingList'

/**
 * The three things somebody can say back to an invitation, all one click and no sign-in.
 *
 * They are clicked here rather than followed out of the mail: a one-click link in a mail body is
 * followed by scanners, which would answer on the reader's behalf.
 */
const note = defineModel<string>('note', {required: true})

const props = defineProps<{answering: boolean}>()

const emit = defineEmits<{(e: 'answer', answer: WaitingListAnswerName): void}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-3" data-testid="waitlist-answer">
    <div class="space-y-1">
      <FieldLabel>{{ t('waitingList.publicStatus.answerNote') }}</FieldLabel>
      <TextAreaInput v-model="note" :placeholder="t('waitingList.publicStatus.answerNotePlaceholder')" :rows="2" />
    </div>
    <div class="flex flex-col sm:flex-row gap-3">
      <PrimaryButton
          :icon="['fas', 'check']"
          :disabled="props.answering"
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
        {{ t('waitingList.publicStatus.answerDate') }}
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
