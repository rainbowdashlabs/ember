/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import DateInput from './DateInput.vue'
import TimeShortInput from './TimeShortInput.vue'

/**
 * A moment entered as a day and a time of day, side by side, holding `yyyy-MM-ddTHH:mm` like a
 * `datetime-local` field does.
 *
 * <p>Two fields rather than one, because a phone opens one native `datetime-local` field as a calendar
 * followed by a clock, neither of which says which field it belongs to. The clock read as a second date
 * being asked for, and changing only the day of a start always seemed to ask for the end as well. Each
 * field here opens one picker, and changing the day leaves the time alone.
 *
 * <p>A moment is only handed out once both halves are there: a day without a time is no moment yet,
 * and an emptied day empties the moment.
 */
const model = defineModel<string>()

const props = defineProps<{
  disabled?: boolean
  /** The earliest moment worth offering, of which only the day limits the calendar. */
  min?: string
  /** What a label's `for` names, given to the day. */
  id?: string
}>()

const emit = defineEmits<{
  /** A half was changed and left, with the moment both halves now give. */
  change: [moment: string]
}>()

const {t} = useI18n()

const day = ref('')
const time = ref('')

watch(model, (moment) => {
  day.value = moment?.slice(0, 10) ?? ''
  time.value = moment?.slice(11, 16) ?? ''
}, {immediate: true})

/** The moment the two halves give, empty while either is missing. */
function combined(): string {
  return day.value && time.value ? `${day.value}T${time.value}` : ''
}

watch([day, time], () => {
  const moment = combined()
  if (moment !== (model.value ?? '')) model.value = moment
})
</script>

<template>
  <div class="flex gap-2" data-moment>
    <DateInput
        :id="props.id"
        v-model="day"
        :aria-label="t('common.datePart')"
        :disabled="disabled"
        :min="min?.slice(0, 10) || undefined"
        class="min-w-0 flex-1"
        @change="emit('change', combined())"
    />
    <TimeShortInput
        v-model="time"
        :aria-label="t('common.timePart')"
        :disabled="disabled"
        class="w-24 shrink-0"
        @change="emit('change', combined())"
    />
  </div>
</template>
