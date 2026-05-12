/*
*     SPDX-License-Identifier: AGPL-3.0-only
*
*     Copyright (C) RainbowDashLabs and Contributor
*/
<script lang="ts" setup>
import {ref} from 'vue'

const props = defineProps<{
  accept?: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  select: [file: File]
}>()

const inputRef = ref<HTMLInputElement | null>(null)

function handleChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) {
    emit('select', file)
  }
  input.value = ''
}

function open() {
  inputRef.value?.click()
}

defineExpose({open})
</script>

<template>
  <label :class="{ 'pointer-events-none opacity-50': disabled }" class="cursor-pointer inline-block">
    <span class="pointer-events-none">
      <slot/>
    </span>
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
