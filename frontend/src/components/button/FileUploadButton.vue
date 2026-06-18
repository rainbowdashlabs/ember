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
  multiple?: boolean
}>()

const emit = defineEmits<{
  select: [file: File]
  selectMany: [files: File[]]
}>()

const inputRef = ref<HTMLInputElement | null>(null)
defineExpose({ inputRef })

function handleChange(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files
  if (files && files.length > 0) {
    if (props.multiple) emit('selectMany', Array.from(files))
    else emit('select', files[0])
  }
  input.value = ''
}
</script>

<template>
  <label
      :class="{ 'opacity-50 cursor-not-allowed pointer-events-none': disabled }"
      class="inline-flex items-center cursor-pointer rounded-theme px-3 py-1.5 text-sm font-medium bg-primary text-primary-text hover:bg-primary-accent hover:text-primary-accent-text transition-all duration-150 active:scale-95"
  >
    <font-awesome-icon :icon="['fas', 'upload']" class="mr-2"/>
    <slot/>
    <input
        ref="inputRef"
        :accept="accept"
        :disabled="disabled"
        :multiple="multiple"
        class="hidden"
        type="file"
        @change="handleChange"
    />
  </label>
</template>
