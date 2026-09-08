/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import DecimalInput from '@/components/input/number/DecimalInput.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {AttendanceSession} from '@/api/attendance'
import {formatDateTime, instantToLocalInput} from '@/util/format'

/**
 * What the sheet itself says: what it is called, when it ran, and what being there is worth.
 *
 * <p>The times are the sheet's own even where an appointment stands behind it, since an appointment
 * hands them down once and a sheet that started late or ran long has to be able to say so. Where
 * they no longer match the appointment, the appointment's own times are offered back rather than
 * quietly restored.
 */
const {t} = useI18n()

const props = defineProps<{
  session: AttendanceSession
  readonly?: boolean
  /** When the appointment behind the sheet runs, absent where it stands on its own. */
  eventStartTime?: string | null
  eventEndTime?: string | null
}>()

const emit = defineEmits<{
  updateTitle: [title: string]
  updateStartTime: [moment: string]
  updateEndTime: [moment: string]
  updateCountedHours: [hours: number | null]
  takeEventTimes: [startTime: string, endTime: string]
}>()

const countedHours = computed(() =>
    props.session.countedMinutes == null ? null : props.session.countedMinutes / 60,
)

const eventTimesDiffer = computed(() =>
    !!props.eventStartTime && !!props.eventEndTime
    && (props.eventStartTime !== props.session.startTime || props.eventEndTime !== props.session.endTime),
)

/** An empty field means the sheet's times decide again, which is what most sheets say. */
function onCountedHours(value: number | string | undefined) {
  const hours = typeof value === 'string' ? Number(value.trim() || Number.NaN) : value
  emit('updateCountedHours', hours === undefined || Number.isNaN(hours) || hours < 0 ? null : hours)
}
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <div class="space-y-1">
        <FieldLabel>{{ t('attendanceSession.title') }}</FieldLabel>
        <TextInput v-if="!readonly" :model-value="session.title ?? ''"
                   @update:model-value="emit('updateTitle', ($event as string) ?? '')"/>
        <span v-else class="text-sm">{{ session.title || '-' }}</span>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('attendanceSession.startTime') }}</FieldLabel>
        <DateTimeInput v-if="!readonly"
                       :model-value="instantToLocalInput(session.startTime)"
                       @change="emit('updateStartTime', ($event.target as HTMLInputElement).value)"
        />
        <span v-else class="text-sm">{{ formatDateTime(session.startTime) || '-' }}</span>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('attendanceSession.endTime') }}</FieldLabel>
        <DateTimeInput v-if="!readonly"
                       :model-value="instantToLocalInput(session.endTime)"
                       @change="emit('updateEndTime', ($event.target as HTMLInputElement).value)"
        />
        <span v-else class="text-sm">{{ formatDateTime(session.endTime) || '-' }}</span>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('attendanceSession.countedHours') }}</FieldLabel>
        <DecimalInput v-if="!readonly"
                      data-testid="session-counted-hours"
                      :model-value="countedHours ?? undefined"
                      :placeholder="t('attendanceSession.countedHoursPlaceholder')"
                      step="0.25"
                      @update:model-value="onCountedHours"
        />
        <span v-else class="text-sm">{{ countedHours ?? '-' }}</span>
        <p class="text-xs text-(--text-muted)">{{ t('attendanceSession.countedHoursHint') }}</p>
      </div>
    </div>

    <div v-if="!readonly && eventTimesDiffer" class="flex items-center gap-2 flex-wrap text-xs text-(--text-muted)">
      <span>
        {{ t('attendanceSession.eventTimes', {
          start: formatDateTime(eventStartTime),
          end: formatDateTime(eventEndTime),
        }) }}
      </span>
      <SecondaryButton compact @click="emit('takeEventTimes', eventStartTime!, eventEndTime!)">
        {{ t('attendanceSession.takeEventTimes') }}
      </SecondaryButton>
    </div>
  </NeutralContainer>
</template>
