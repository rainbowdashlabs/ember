/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import EventFieldValueInput from '../eventshared/EventFieldValueInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import type {EventField} from '@/api/events'
import type {StationMember} from '@/api/types'
import {events} from '@/api'
import {showToast} from '@/util/toast'

/**
 * Answering one question of an appointment for the date on screen.
 *
 * <p>A question answered per date has no answer on the appointment itself, so the editor cannot
 * reach it: the answer belongs to one occurrence and is given where that occurrence is shown.
 * Whoever may edit the appointment sees this; everybody else sees only the answer.
 */
const props = defineProps<{
  eventId: number
  field: EventField
  date: string
  allMembers: StationMember[]
}>()

const emit = defineEmits<{
  (e: 'saved', field: EventField): void
}>()

const {t} = useI18n()

const draft = ref(props.field.value ?? '')
const saving = ref(false)

watch(() => [props.field.id, props.field.value, props.date], () => {
  draft.value = props.field.value ?? ''
})

async function save() {
  if (saving.value) return
  saving.value = true
  try {
    emit('saved', await events.setEventFieldValueOn(props.eventId, props.field.id, props.date, draft.value))
    showToast(t('eventFields.valueSaved'), 'success')
  } catch {
    showToast(t('eventFields.valueSaveFailed'), 'error')
  } finally {
    saving.value = false
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
        :disabled="saving"
    />
    <PrimaryButton :disabled="saving || draft === (field.value ?? '')" compact class="text-sm" @click="save">
      {{ t('eventFields.saveValue') }}
    </PrimaryButton>
  </div>
</template>
