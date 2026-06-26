/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TimeShortInput from '@/components/input/datetime/TimeShortInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import {formatTime} from '@/util/format'

const {t} = useI18n()

defineProps<{
  checkIn?: string
  checkOut?: string
}>()

const emit = defineEmits<{
  checkIn: [time: string]
  checkOut: [time: string]
  resetTimes: []
}>()

</script>

<template>
  <div class="flex items-center gap-1 text-xs">
    <TimeShortInput
        :model-value="formatTime(checkIn)"
        class="w-20 text-xs"
        @change="emit('checkIn', ($event.target as HTMLInputElement).value)"
    />
    <span class="text-(--text-muted)">–</span>
    <TimeShortInput
        :model-value="formatTime(checkOut)"
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
