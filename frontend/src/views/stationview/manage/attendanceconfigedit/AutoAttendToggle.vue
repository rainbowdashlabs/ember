/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useId} from 'vue'
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

/** Whether the members a member field names are counted as present on the sheet. */
const autoAttend = defineModel<boolean>('autoAttend', {required: true})

const {t} = useI18n()
const labelId = useId()
</script>

<template>
  <div class="space-y-1">
    <div class="flex items-center justify-between">
      <span :id="labelId" class="text-sm font-medium">{{ t('attendanceConfig.fieldAutoAttend') }}</span>
      <ToggleInput v-model="autoAttend" :aria-labelledby="labelId"/>
    </div>
    <p class="text-xs text-(--text-muted)">{{ t('attendanceConfig.fieldAutoAttendHint') }}</p>
  </div>
</template>
