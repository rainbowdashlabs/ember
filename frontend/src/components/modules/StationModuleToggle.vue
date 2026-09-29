/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import AppIcon from '@/components/display/AppIcon.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import type {StationModuleOption} from '@/data/stationModules'

/**
 * One module with its switch: its icon, its name and a line on what it is for. Every list of
 * modules draws its rows with this, the help pages included, so they all read the same. The slot
 * takes a remark after the name, such as who locked the module.
 */
const enabled = defineModel<boolean>({required: true})

defineProps<{
  module: StationModuleOption
  disabled?: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <div data-testid="module-toggle" :data-module="module.value" class="flex items-start gap-3">
    <ToggleInput v-model="enabled" :disabled="disabled"/>
    <AppIcon :icon="module.icon" class="mt-1 h-4 w-4 shrink-0 text-(--text-muted)"/>
    <div class="min-w-0">
      <span class="text-sm font-medium">{{ t(module.labelKey) }}</span>
      <slot/>
      <p class="text-xs text-(--text-muted)">{{ t(module.descriptionKey) }}</p>
    </div>
  </div>
</template>
