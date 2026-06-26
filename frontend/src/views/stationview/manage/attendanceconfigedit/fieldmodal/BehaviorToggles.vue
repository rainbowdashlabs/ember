/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

defineProps<{
  showAutoAttend: boolean
}>()

const required = defineModel<boolean>('required', {required: true})
const autoAttend = defineModel<boolean>('autoAttend', {required: true})

const {t} = useI18n()
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <label class="text-sm font-medium">{{ t('attendanceConfig.fieldRequired') }}</label>
      <ToggleInput v-model="required"/>
    </div>
    <div v-if="showAutoAttend" class="space-y-1">
      <div class="flex items-center justify-between">
        <label class="text-sm font-medium">{{ t('attendanceConfig.fieldAutoAttend') }}</label>
        <ToggleInput v-model="autoAttend"/>
      </div>
      <p class="text-xs text-(--text-muted)">{{ t('attendanceConfig.fieldAutoAttendHint') }}</p>
    </div>
  </div>
</template>
