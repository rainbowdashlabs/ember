/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

defineProps<{
  label: string
  enabled: boolean
}>()
</script>

<template>
  <div class="flex items-center gap-3">
    <ToggleInput :model-value="enabled" disabled/>
    <span class="text-sm font-medium">{{ label }}</span>
  </div>
</template>
