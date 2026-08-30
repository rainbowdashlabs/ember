/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

const windowHours = defineModel<number>('windowHours', {required: true})
const includeBots = defineModel<boolean>('includeBots', {required: true})

const {t} = useI18n()

const windowHoursModel = computed({
  get: () => String(windowHours.value),
  set: v => { windowHours.value = Number(v) },
})
</script>

<template>
  <div class="flex flex-wrap gap-3 items-end">
    <label class="flex flex-col gap-1 text-sm">
      <span class="text-(--text-muted)">{{ t('insights.controls.window') }}</span>
      <SelectInput v-model="windowHoursModel">
        <option value="24">{{ t('insights.window.last24h') }}</option>
        <option value="168">{{ t('insights.window.last7d') }}</option>
        <option value="720">{{ t('insights.window.last30d') }}</option>
        <option value="2160">{{ t('insights.window.last90d') }}</option>
        <option value="8760">{{ t('insights.window.last365d') }}</option>
      </SelectInput>
    </label>
    <label class="flex items-center gap-2 text-sm">
      <ToggleInput v-model="includeBots"/>
      <span class="text-(--text-muted)">{{ t('insights.controls.includeBots') }}</span>
    </label>
  </div>
</template>
