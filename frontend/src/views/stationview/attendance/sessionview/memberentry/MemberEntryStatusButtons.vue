/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import InfoButton from '@/components/button/InfoButton.vue'
import {useBreakpoint} from '@/composables/useBreakpoint'
import type {AttendanceStatus} from '@/api/attendance'

const {t} = useI18n()
const {isMobile} = useBreakpoint()

defineProps<{
  /** Undefined where the member has no row yet, so no button reads as the one already chosen. */
  status?: AttendanceStatus
}>()

const emit = defineEmits<{
  setStatus: [status: AttendanceStatus]
}>()
</script>

<template>
  <div class="flex items-center gap-2 w-full sm:w-auto">
    <SuccessButton
        :class="{ 'opacity-40': status === 'PRESENT' }"
        :disabled="status === 'PRESENT'"
        :full-width="isMobile"
        :aria-label="t('attendanceSession.present')"
        :title="t('attendanceSession.present')"
        class="text-xs flex-1 sm:flex-initial"
        @click="emit('setStatus', 'PRESENT')"
    >
      <font-awesome-icon :icon="['fas', 'check']"/>
    </SuccessButton>
    <ErrorButton
        :class="{ 'opacity-40': status === 'ABSENT' }"
        :disabled="status === 'ABSENT'"
        :full-width="isMobile"
        :aria-label="t('attendanceSession.absent')"
        :title="t('attendanceSession.absent')"
        class="text-xs flex-1 sm:flex-initial"
        @click="emit('setStatus', 'ABSENT')"
    >
      <font-awesome-icon :icon="['fas', 'xmark']"/>
    </ErrorButton>
    <InfoButton
        :class="{ 'opacity-40': status === 'DECLINED' }"
        :disabled="status === 'DECLINED'"
        :full-width="isMobile"
        :aria-label="t('attendanceSession.declined')"
        :title="t('attendanceSession.declined')"
        class="text-xs flex-1 sm:flex-initial"
        @click="emit('setStatus', 'DECLINED')"
    >
      <font-awesome-icon :icon="['fas', 'ban']"/>
    </InfoButton>
  </div>
</template>
