/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import EventFieldValueInput from '../eventshared/EventFieldValueInput.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import type {AppointmentField} from '@/api/generated/schema'
import type {MemberLike} from '@/components/input/select/memberOption'
import {events} from '@/api'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * Answering one question of an appointment for the date on screen.
 *
 * <p>A question answered per date has no answer on the appointment itself, so the editor cannot
 * reach it: the answer belongs to one occurrence and is given where that occurrence is shown.
 * Whoever may edit the appointment sees this; everybody else sees only the answer.
 */
const props = defineProps<{
  eventId: number
  field: AppointmentField
  date: string
  allMembers: MemberLike[]
}>()

const emit = defineEmits<{
  (e: 'saved', field: AppointmentField): void
}>()

const {t} = useI18n()

const draft = ref(props.field.value ?? '')
const failure = ref<Failure | null>(null)

watch(() => [props.field.id, props.field.value, props.date], () => {
  draft.value = props.field.value ?? ''
})

/**
 * Writes the answer for this one date.
 *
 * <p>A save that worked shows on the button; one that did not stays on the screen next to the box
 * still holding the typed answer, with the reason, since their answer is not stored and they need
 * to see why.
 */
async function save() {
  failure.value = null
  try {
    emit('saved', await events.setEventFieldValueOn(props.eventId, props.field.id, props.date, draft.value))
  } catch (e) {
    failure.value = {...describeFailure(e, t), message: t('eventFields.valueSaveFailed')}
    throw e
  }
}
</script>

<template>
  <div class="mt-1 space-y-1" :data-testid="`per-date-value-${field.id}`">
    <EventFieldValueInput
        v-model="draft"
        :field-type="field.fieldType ?? ''"
        :config="field.config"
        :all-members="allMembers"
    />
    <SaveButton :action="save" :disabled="draft === (field.value ?? '')" compact class="text-sm">
      {{ t('eventFields.saveValue') }}
    </SaveButton>
    <FailureAlert :failure="failure"/>
  </div>
</template>
