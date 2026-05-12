/*
*     SPDX-License-Identifier: AGPL-3.0-only
*
*     Copyright (C) RainbowDashLabs and Contributor
*/
<script lang="ts" setup>
interface Tab {
  key: string
  label: string
}

const props = defineProps<{
  tabs: Tab[]
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<template>
  <div class="flex gap-2 border-b border-bg-light-accent dark:border-bg-dark-accent">
    <button
        v-for="tab in props.tabs"
        :key="tab.key"
        :class="modelValue === tab.key ? 'border-primary text-primary' : 'border-transparent text-(--text-muted) hover:text-(--text)'"
        class="px-4 py-2 text-sm font-medium transition-colors -mb-px border-b-2"
        @click="emit('update:modelValue', tab.key)"
    >
      {{ tab.label }}
    </button>
  </div>
</template>
