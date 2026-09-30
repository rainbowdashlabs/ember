/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useId} from 'vue'
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

defineProps<{
  showAutoAttend: boolean
}>()

const required = defineModel<boolean>('required', {required: true})
const autoAttend = defineModel<boolean>('autoAttend', {required: true})

const {t} = useI18n()
const labelId = useId()
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <span :id="`${labelId}-required`" class="text-sm font-medium">{{ t('attendanceConfig.fieldRequired') }}</span>
      <ToggleInput v-model="required" :aria-labelledby="`${labelId}-required`"/>
    </div>
    <div v-if="showAutoAttend" class="space-y-1">
      <div class="flex items-center justify-between">
        <span :id="`${labelId}-auto-attend`" class="text-sm font-medium">{{ t('attendanceConfig.fieldAutoAttend') }}</span>
        <ToggleInput v-model="autoAttend" :aria-labelledby="`${labelId}-auto-attend`"/>
      </div>
      <p class="text-xs text-(--text-muted)">{{ t('attendanceConfig.fieldAutoAttendHint') }}</p>
    </div>
  </div>
</template>
