/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import DeleteButton from '@/components/button/DeleteButton.vue'

defineProps<{
  label: string
  index: number
}>()

const emit = defineEmits<{
  remove: []
}>()
</script>

<template>
  <div class="flex items-center gap-2 rounded-lg px-3 py-2 bg-bg-light-accent/30 dark:bg-bg-dark-accent/30">
    <span class="text-(--text-muted) text-xs w-5 text-center">{{ index + 1 }}</span>
    <span class="flex-1 text-sm font-medium">{{ label }}</span>
    <DeleteButton @click="emit('remove')"/>
  </div>
</template>
