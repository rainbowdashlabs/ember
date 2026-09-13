/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, nextTick, onMounted, ref, useAttrs} from 'vue'

/**
 * Layout and identity belong to the box, behaviour belongs to the field.
 *
 * <p>Callers size this component by class and find it by test id, so those stay on the root where
 * they have always been. What moves to the input is what only an input can answer: the keys a caller
 * listens for and the ARIA a screen reader reads, which on a wrapper would describe a div nobody is
 * typing into.
 */
defineOptions({inheritAttrs: false})

const model = defineModel<string>()

const props = withDefaults(defineProps<{
  placeholder?: string
  disabled?: boolean
  autofocus?: boolean
}>(), {
  autofocus: false,
})

const attrs = useAttrs()

const fieldAttrs = computed(() =>
  Object.fromEntries(
    Object.entries(attrs).filter(
      ([name]) => name.startsWith('aria-') || name === 'role' || name.startsWith('on'),
    ),
  ),
)

const boxAttrs = computed(() =>
  Object.fromEntries(
    Object.entries(attrs).filter(
      ([name]) =>
        !(name.startsWith('aria-') || name === 'role' || name.startsWith('on') || name === 'class' || name === 'style'),
    ),
  ),
)

const inputEl = ref<HTMLInputElement | null>(null)

function focus() {
  inputEl.value?.focus()
}

function clear() {
  model.value = ''
}

onMounted(() => {
  if (props.autofocus) nextTick(focus)
})

defineExpose({focus})
</script>

<template>
  <div v-bind="boxAttrs" :class="['relative w-full min-w-0', attrs.class as string]" :style="attrs.style as string">
    <span class="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-primary">
      <font-awesome-icon :icon="['fas', 'magnifying-glass']" class="h-4 w-4"/>
    </span>
    <input
        ref="inputEl"
        v-model="model"
        v-bind="fieldAttrs"
        :disabled="disabled"
        :placeholder="placeholder"
        type="search"
        class="w-full pl-10 pr-9 py-2 rounded-theme border-2 border-primary bg-bg-light text-(--text)
               placeholder:text-(--text-muted)
               transition-colors duration-150 outline-none
               focus:ring-2 focus:ring-primary/30
               disabled:opacity-50 disabled:cursor-not-allowed
               dark:bg-bg-dark"
    />
    <button
        v-if="model"
        type="button"
        :aria-label="placeholder ?? 'clear'"
        class="absolute inset-y-0 right-0 flex items-center pr-3 text-(--text-muted) hover:text-primary transition-colors"
        @click="clear"
    >
      <font-awesome-icon :icon="['fas', 'xmark']" class="h-4 w-4"/>
    </button>
  </div>
</template>
