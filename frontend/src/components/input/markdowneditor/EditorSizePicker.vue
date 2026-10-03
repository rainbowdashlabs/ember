/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { watchDebounced } from '@vueuse/core'
import { useI18n } from 'vue-i18n'
import NumberInput from '@/components/input/number/NumberInput.vue'
import FloatingPanel from '@/components/feedback/FloatingPanel.vue'
import { LARGEST_TEXT_SIZE, pixelSize, SMALLEST_TEXT_SIZE } from '@/util/textSize'

/**
 * The size entry of the editor's menu: a button opening a panel with a field for a size in pixels.
 *
 * <p>A size applies as soon as it stands in the field, whether typed or counted with the arrows, after a
 * short pause so typing "16" does not first set the words in 1. Enter closes the panel. An empty field
 * and the reset entry give the words their normal size back. A size that is no whole number within
 * bounds is refused and the field says so. The panel is rendered at the end of the
 * page, so the edge of the text field cannot cut it off.
 *
 * <p>The field opens on the size the words are shown in, their own or the one they inherit, so its
 * arrows count on from there rather than from the smallest size an empty number field starts at. That
 * inherited size left as it is applies nothing.
 */
const props = defineProps<{
  /** The size of the words at the cursor in pixels, or null where they have none of their own. */
  current: number | null
  /** The size in pixels the words at the cursor are shown in, their own or the one they inherit. */
  shown: number
}>()

const emit = defineEmits<{
  pick: [size: number | null]
}>()

const TYPING_PAUSE_MS = 300

const { t } = useI18n()

const open = ref(false)
const typed = ref<number>()
const blank = computed(() => String(typed.value ?? '').trim() === '')
const invalid = computed(() => !blank.value && pixelSize(typed.value) === null)
const label = computed(() => t('markdownEditor.textSize'))

watch(open, isOpen => {
  if (isOpen) typed.value = props.current ?? props.shown
})

watchDebounced(typed, () => {
  if (open.value && !blank.value) apply(false)
}, {debounce: TYPING_PAUSE_MS})

function pick(size: number | null) {
  const unchanged = size === props.current || (props.current === null && size === props.shown)
  if (!unchanged) emit('pick', size)
}

function apply(close: boolean) {
  const size = blank.value ? null : pixelSize(typed.value)
  if (!blank.value && size === null) return
  pick(size)
  if (close) open.value = false
}

function reset() {
  pick(null)
  open.value = false
}
</script>

<template>
  <FloatingPanel v-model:open="open" :label="label" role="dialog" align="start" panel-class="w-48 p-2 space-y-2">
    <template #trigger="{ triggerAttrs }">
      <button
        type="button"
        :title="label"
        :aria-label="label"
        data-testid="editor-size"
        v-bind="triggerAttrs"
        :class="['p-1.5 rounded text-sm transition-colors inline-flex items-center gap-1', current ? 'text-primary bg-primary/10' : 'text-[var(--text)] hover:bg-[var(--bg-accent)]']"
        @mousedown.prevent
        @click="open = !open"
      >
        <font-awesome-icon :icon="['fas', 'text-height']" class="w-3.5 h-3.5" />
        <span v-if="current" class="text-xs">{{ t('markdownEditor.pixelSize', { size: current }) }}</span>
      </button>
    </template>
    <div class="flex items-center gap-2">
      <NumberInput
        v-model="typed"
        :min="SMALLEST_TEXT_SIZE"
        :max="LARGEST_TEXT_SIZE"
        :placeholder="t('markdownEditor.sizePlaceholder')"
        :aria-label="t('markdownEditor.sizeInPixels')"
        :aria-invalid="invalid"
        class="text-xs"
        data-testid="editor-size-field"
        @keydown.enter.prevent="apply(true)"
        @blur="apply(false)"
      />
      <span class="text-xs text-[var(--text-muted)]">{{ t('markdownEditor.pixels') }}</span>
    </div>
    <p v-if="invalid" class="text-xs text-error">
      {{ t('markdownEditor.sizeInvalid', { min: SMALLEST_TEXT_SIZE, max: LARGEST_TEXT_SIZE }) }}
    </p>
    <button
      type="button"
      class="w-full text-left text-xs px-2 py-1 rounded hover:bg-[var(--bg-accent)] text-[var(--text)]"
      data-testid="editor-size-reset"
      @mousedown.prevent
      @click="reset"
    >
      {{ t('markdownEditor.defaultSize') }}
    </button>
  </FloatingPanel>
</template>
