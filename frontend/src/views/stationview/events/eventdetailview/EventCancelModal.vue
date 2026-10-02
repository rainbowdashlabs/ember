/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import {events} from '@/api'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {formatWeekdayDate} from '@/util/format'

/**
 * Asks for a reason and calls an appointment off: the one date on screen, or the whole series.
 *
 * <p>The two are said apart in the heading, the question and the button, because calling off a
 * series is final and calling off a date is not.
 */
const props = defineProps<{
  show: boolean
  eventId: number
  /** The date to call off, or null to call off the whole series. */
  date: string | null
}>()

const emit = defineEmits<{
  close: []
  cancelled: []
}>()

const {t} = useI18n()
const visible = computed({
  get: () => props.show,
  set: (v: boolean) => { if (!v) emit('close') },
})
const cancelReason = ref('')

const title = computed(() => props.date ? t('events.cancelDate') : t('events.cancelSeries'))
const question = computed(() => props.date
    ? t('events.cancelDateConfirm', {date: formatWeekdayDate(props.date)})
    : t('events.cancelSeriesConfirm'))

const {running: cancelling, failure: cancelFailure, run: cancel} = useAsyncAction(async () => {
  const reason = cancelReason.value || undefined
  if (props.date) await events.cancelEventDate(props.eventId, props.date, reason)
  else await events.cancelSeries(props.eventId, reason)
  cancelReason.value = ''
  emit('cancelled')
})
</script>

<template>
  <Modal v-model="visible">
    <template #header>{{ title }}</template>
    <div class="space-y-4">
      <p>{{ question }}</p>
      <TextAreaInput v-model="cancelReason" :placeholder="t('events.cancelReason')" :rows="3"/>
      <FailureAlert :failure="cancelFailure"/>
      <ButtonRow pair align="end">
        <SecondaryButton @click="emit('close')">{{ t('common.cancel') }}</SecondaryButton>
        <ErrorButton :disabled="cancelling" :icon="['fas', 'ban']" data-testid="event-cancel-confirm" @click="cancel">
          {{ cancelling ? t('common.loading') : title }}
        </ErrorButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
