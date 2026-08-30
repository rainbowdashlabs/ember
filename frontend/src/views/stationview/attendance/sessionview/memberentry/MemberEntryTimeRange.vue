/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import TimeShortInput from '@/components/input/datetime/TimeShortInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import {formatTime} from '@/util/format'

const {t} = useI18n()

/**
 * When somebody came and went, against the times the sheet itself runs to.
 *
 * <p>Nearly everybody was there from the start to the end, and writing that down once per member is
 * work that says nothing. An entry that carries no time of its own therefore shows the session's,
 * faintly, as the answer already assumed. Nothing is stored for it: the field only reports a change,
 * so a time that is left alone stays absent on the server and a time that is corrected is the
 * deviation worth keeping.
 */
const props = defineProps<{
  checkIn?: string
  checkOut?: string
  /** What the sheet runs to, shown in place of an entry's own times. */
  sessionStart?: string
  sessionEnd?: string
}>()

const emit = defineEmits<{
  checkIn: [time: string]
  checkOut: [time: string]
  resetTimes: []
}>()

const shownCheckIn = computed(() => formatTime(props.checkIn) || formatTime(props.sessionStart))
const shownCheckOut = computed(() => formatTime(props.checkOut) || formatTime(props.sessionEnd))
</script>

<template>
  <div class="flex items-center gap-1 text-xs">
    <TimeShortInput
        :class="{'opacity-60': !checkIn}"
        :model-value="shownCheckIn"
        class="w-20 text-xs"
        @change="emit('checkIn', ($event.target as HTMLInputElement).value)"
    />
    <span class="text-(--text-muted)">–</span>
    <TimeShortInput
        :class="{'opacity-60': !checkOut}"
        :model-value="shownCheckOut"
        class="w-20 text-xs"
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
