/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import FieldLabel from '@/components/typography/FieldLabel.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

const model = defineModel<boolean>({required: true})

defineProps<{
  label: string
  hint?: string
  disabled?: boolean
}>()
</script>

<template>
  <div class="flex items-center justify-between gap-4">
    <div>
      <FieldLabel>{{ label }}</FieldLabel>
      <p v-if="hint" class="text-xs text-(--text-muted)">{{ hint }}</p>
    </div>
    <ToggleInput v-model="model" :disabled="disabled"/>
  </div>
</template>
