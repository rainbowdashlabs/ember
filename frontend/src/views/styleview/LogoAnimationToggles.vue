/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

const autoBlink = defineModel<boolean>('autoBlink', { required: true })
const shake = defineModel<boolean>('shake', { required: true })
const displayShake = defineModel<boolean>('displayShake', { required: true })
</script>

<template>
  <div class="space-y-1">
    <label class="flex items-center gap-2 mb-1">
      <ToggleInput v-model="autoBlink"/>
      <span class="text-xs font-medium text-(--text-muted)">Blinzeln</span>
    </label>
    <label class="flex items-center gap-2 mb-1">
      <ToggleInput v-model="shake"/>
      <span class="text-xs font-medium text-(--text-muted)">Hüpfen</span>
    </label>
    <label class="flex items-center gap-2 mb-1">
      <ToggleInput v-model="displayShake"/>
      <span class="text-xs font-medium text-(--text-muted)">Wackeln (Anzeige)</span>
    </label>
  </div>
</template>
