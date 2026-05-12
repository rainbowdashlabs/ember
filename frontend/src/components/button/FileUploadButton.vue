/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'

defineProps<{
  accept?: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  select: [file: File]
}>()

const inputRef = ref<HTMLInputElement | null>(null)
defineExpose({ inputRef })

function handleChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) {
    emit('select', file)
  }
  input.value = ''
}
</script>

<template>
  <label
      :class="{ 'opacity-50 cursor-not-allowed pointer-events-none': disabled }"
      class="inline-flex items-center cursor-pointer rounded-lg px-4 py-2 font-medium bg-primary text-white hover:bg-primary-accent transition-all duration-150 active:scale-95"
  >
    <font-awesome-icon :icon="['fas', 'upload']" class="mr-2"/>
    <slot/>
    <input
        ref="inputRef"
        :accept="accept"
        :disabled="disabled"
        class="hidden"
        type="file"
        @change="handleChange"
    />
  </label>
</template>
