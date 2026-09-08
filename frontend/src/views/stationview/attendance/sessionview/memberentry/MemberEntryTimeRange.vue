/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import TimeShortInput from '@/components/input/datetime/TimeShortInput.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import {formatTime, instantToLocalInput} from '@/util/format'

/**
 * When somebody came and went, against the times the sheet itself runs to.
 *
 * <p>Nearly everybody was there from the start to the end, and writing that down once per member is
 * work that says nothing. An entry that carries no time of its own therefore shows the session's,
 * faintly, as the answer already assumed. Nothing is stored for it: the field only reports a change,
 * so a time that is left alone stays absent on the server and a time that is corrected is the
 * deviation worth keeping.
 *
 * <p>On a sheet that runs over more than one day the field carries the day as well. A time on its
 * own would be two moments at once there, and the one that was picked decided the hours.
 */
const {t} = useI18n()

const props = defineProps<{
  checkIn?: string
  checkOut?: string
  /** What the sheet runs to, shown in place of an entry's own times. */
  sessionStart?: string
  sessionEnd?: string
  /** Whether the sheet runs into another day. */
  spansDays?: boolean
}>()

const emit = defineEmits<{
  checkIn: [time: string]
  checkOut: [time: string]
  resetTimes: []
}>()

const shownCheckIn = computed(() => shown(props.checkIn, props.sessionStart))
const shownCheckOut = computed(() => shown(props.checkOut, props.sessionEnd))

function shown(own?: string, session?: string): string {
  const moment = own || session
  return props.spansDays ? instantToLocalInput(moment) : formatTime(moment)
}
</script>

<template>
  <div class="flex items-center gap-1 text-xs">
    <component
        :is="spansDays ? DateTimeInput : TimeShortInput"
        :class="[spansDays ? 'w-44' : 'w-20', {'opacity-60': !checkIn}]"
        :model-value="shownCheckIn"
        class="text-xs"
        @change="emit('checkIn', ($event.target as HTMLInputElement).value)"
    />
    <span class="text-(--text-muted)">–</span>
    <component
        :is="spansDays ? DateTimeInput : TimeShortInput"
        :class="[spansDays ? 'w-44' : 'w-20', {'opacity-60': !checkOut}]"
        :model-value="shownCheckOut"
        class="text-xs"
        @change="emit('checkOut', ($event.target as HTMLInputElement).value)"
    />
    <IconButton
        v-if="checkIn || checkOut"
        :icon="['fas', 'xmark']"
        :label="t('attendanceSession.resetTimes')"
        @click="emit('resetTimes')"
    />
  </div>
</template>
