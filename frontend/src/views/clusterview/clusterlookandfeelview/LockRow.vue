/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

defineProps<{
  label: string
}>()

const locked = defineModel<boolean>({default: false})
</script>

<template>
  <div class="flex items-center justify-between gap-4">
    <span class="text-sm font-medium">{{ label }}</span>
    <ToggleInput v-model="locked"/>
  </div>
</template>
