/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import type {LogoLayer} from '@/components/display/LayeredEmberLogo.vue'

defineProps<{
  label: string
  layers: LogoLayer[]
  active: Set<string>
}>()

const emit = defineEmits<{
  toggle: [name: string]
}>()
</script>

<template>
  <div>
    <p class="text-xs font-medium text-(--text-muted) mb-1">{{ label }}</p>
    <div class="flex flex-wrap gap-2">
      <SelectionToggleButton
        v-for="layer in layers"
        :key="layer.name"
        :selected="active.has(layer.name)"
        @toggle="emit('toggle', layer.name)"
      >
        {{ layer.label }}
      </SelectionToggleButton>
    </div>
  </div>
</template>
