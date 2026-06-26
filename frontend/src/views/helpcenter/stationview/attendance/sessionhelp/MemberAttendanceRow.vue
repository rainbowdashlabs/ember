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
import TimeShortInput from '@/components/input/datetime/TimeShortInput.vue'

type Status = 'present' | 'absent' | 'open'

const props = defineProps<{
  name: string
  status: Status
  checkIn?: string
  checkOut?: string
}>()

const {t} = useI18n()

const borderClass = {
  present: 'border-success bg-success/5',
  absent: 'border-error bg-error/5',
  open: 'border-bg-light-accent dark:border-bg-dark-accent bg-bg-light-accent/20 dark:bg-bg-dark-accent/20',
}[props.status]

const statusIcon = {
  present: 'check',
  absent: 'xmark',
  open: 'asterisk',
}[props.status]

const statusIconClass = {
  present: 'text-success',
  absent: 'text-error',
  open: 'text-(--text-muted)',
}[props.status]
</script>

<template>
  <div :class="['rounded-lg px-4 py-3 border-l-4 transition-all', borderClass]">
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
      <div class="flex items-center gap-2 min-w-0">
        <font-awesome-icon :icon="['fas', statusIcon]" :class="['h-4 w-4 shrink-0', statusIconClass]"/>
        <span class="font-medium text-sm">{{ name }}</span>
      </div>
      <div class="flex flex-col sm:flex-row sm:items-center gap-2">
        <div class="flex items-center gap-2">
          <SuccessButton :class="status === 'present' ? 'opacity-40' : ''" disabled>
            <font-awesome-icon :icon="['fas', 'check']"/>
          </SuccessButton>
          <ErrorButton :class="status === 'absent' ? 'opacity-40' : ''" disabled>
            <font-awesome-icon :icon="['fas', 'xmark']"/>
          </ErrorButton>
          <InfoButton disabled>
            <font-awesome-icon :icon="['fas', 'ban']"/>
          </InfoButton>
        </div>
        <div v-if="checkIn && checkOut" class="flex items-center gap-1 text-xs">
          <TimeShortInput :model-value="checkIn" class="w-20 text-xs" disabled />
          <span class="text-(--text-muted)">–</span>
          <TimeShortInput :model-value="checkOut" class="w-20 text-xs" disabled />
        </div>
      </div>
    </div>
  </div>
</template>
