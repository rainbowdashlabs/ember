/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import InfoButton from '@/components/button/InfoButton.vue'
import {useBreakpoint} from '@/composables/useBreakpoint'
import type {AttendanceStatus} from '@/api/types'

const {isMobile} = useBreakpoint()

defineProps<{
  status: AttendanceStatus
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
        class="text-xs flex-1 sm:flex-initial"
        @click="emit('setStatus', 'PRESENT')"
    >
      <font-awesome-icon :icon="['fas', 'check']"/>
    </SuccessButton>
    <ErrorButton
        :class="{ 'opacity-40': status === 'ABSENT' }"
        :disabled="status === 'ABSENT'"
        :full-width="isMobile"
        class="text-xs flex-1 sm:flex-initial"
        @click="emit('setStatus', 'ABSENT')"
    >
      <font-awesome-icon :icon="['fas', 'xmark']"/>
    </ErrorButton>
    <InfoButton
        :class="{ 'opacity-40': status === 'DECLINED' }"
        :disabled="status === 'DECLINED'"
        :full-width="isMobile"
        class="text-xs flex-1 sm:flex-initial"
        @click="emit('setStatus', 'DECLINED')"
    >
      <font-awesome-icon :icon="['fas', 'ban']"/>
    </InfoButton>
  </div>
</template>
