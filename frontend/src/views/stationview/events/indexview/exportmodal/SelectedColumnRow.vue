/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'

defineProps<{
  label: string
  index: number
  total: number
}>()

const emit = defineEmits<{
  up: []
  down: []
  remove: []
}>()
</script>

<template>
  <div class="flex items-center gap-2 rounded-lg px-3 py-2 bg-bg-light-accent/30 dark:bg-bg-dark-accent/30">
    <span class="text-(--text-muted) text-xs w-5 text-center">{{ index + 1 }}</span>
    <span class="flex-1 text-sm font-medium">{{ label }}</span>
    <MutedIconButton
        :icon="['fas', 'chevron-up']"
        label="Move up"
        :disabled="index === 0"
        hover="text"
        class="h-6 w-6"
        @click="emit('up')"
    />
    <MutedIconButton
        :icon="['fas', 'chevron-down']"
        label="Move down"
        :disabled="index === total - 1"
        hover="text"
        class="h-6 w-6"
        @click="emit('down')"
    />
    <DeleteButton @click="emit('remove')"/>
  </div>
</template>
