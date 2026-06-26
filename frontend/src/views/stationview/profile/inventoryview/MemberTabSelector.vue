/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'

interface Tab {
  id: string
  label: string
  isOwn: boolean
}

defineProps<{
  tabs: Tab[]
  selectedId: string
}>()

const emit = defineEmits<{
  select: [id: string]
}>()
</script>

<template>
  <div v-if="tabs.length > 1" class="flex flex-wrap gap-2">
    <SelectionToggleButton
        v-for="tab in tabs"
        :key="tab.id"
        :selected="selectedId === tab.id"
        @toggle="emit('select', tab.id)"
    >
      <font-awesome-icon :icon="['fas', tab.isOwn ? 'user' : 'users']" class="mr-1"/>
      {{ tab.label }}
    </SelectionToggleButton>
  </div>
</template>
