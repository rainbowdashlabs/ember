/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

/**
 * One switch of a field's settings, with the line under it saying what it does.
 */
const on = defineModel<boolean>({required: true})

defineProps<{
  label: string
  hint: string
}>()
</script>

<template>
  <div class="space-y-1">
    <div class="flex items-center justify-between">
      <label class="text-sm font-medium">{{ label }}</label>
      <ToggleInput v-model="on"/>
    </div>
    <p class="text-xs text-(--text-muted)">{{ hint }}</p>
  </div>
</template>
