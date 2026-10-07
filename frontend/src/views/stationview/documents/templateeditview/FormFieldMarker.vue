/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import type {ScreenBox} from './pdfViewport'

/**
 * Where one of the PDF's own form fields sits on the page, outlined and named by what fills it, so its
 * entry in the list below the page can be found. It cannot be moved: the PDF decides where it is.
 * Pressing it chooses its entry.
 */
const props = defineProps<{
  box: ScreenBox
  /** What fills the field, or what the PDF calls it where nothing does yet. */
  label: string
  /** Whether a text or check fills it from the template. */
  bound: boolean
  chosen: boolean
}>()

const emit = defineEmits<{
  choose: []
}>()

const style = computed(() => ({
  left: `${props.box.left}px`,
  top: `${props.box.top}px`,
  width: `${props.box.width}px`,
  height: `${props.box.height}px`,
}))
</script>

<template>
  <div class="absolute cursor-pointer rounded-sm border-2 border-dashed focus:outline-none"
       :class="[bound ? 'border-success bg-success/10' : 'border-info bg-info/10', chosen ? 'ring-2 ring-primary' : '']"
       :style="style" role="button" tabindex="0" :aria-label="label" :aria-pressed="chosen" :title="label"
       data-testid="pdf-form-field-marker"
       @pointerdown.stop @click="emit('choose')" @keydown.enter.prevent="emit('choose')" @keydown.space.prevent="emit('choose')">
    <span class="absolute inset-0 truncate px-0.5 text-xs leading-tight text-black">{{ label }}</span>
  </div>
</template>
