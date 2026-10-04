/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import ColorPickerInput from '@/components/input/ColorPickerInput.vue'
import FloatingPanel from '@/components/feedback/FloatingPanel.vue'
import { cssColorHex, hexCode } from '@/util/contrastColor'

/**
 * A colour entry of the editor's menu: a button opening a panel of swatches, a field for a hex code
 * and the browser's own picker for any other colour.
 *
 * <p>A swatch and a hex code confirmed with enter apply and close the panel; a hex code left by moving
 * on and a colour from the browser's picker apply and keep it open, so the colour can still be tuned.
 * The empty swatch stands for taking the colour off again. A press outside the panel and escape
 * close it; the panel is rendered at the end of the page, so the edge of the text field cannot cut it
 * off.
 */
const open = defineModel<boolean>('open', { required: true })

const props = defineProps<{
  icon: string[]
  label: string
  /** The colours offered as swatches, an empty one for taking the colour off. */
  swatches: readonly string[]
  /** What the empty swatch says on hover. */
  resetLabel: string
  /** The colour at the cursor, in whatever form the editor reads it. */
  current: string | null
  active: boolean
}>()

const emit = defineEmits<{
  pick: [color: string]
}>()

const { t } = useI18n()

const hex = ref('')
const spectrum = ref('#000000')
const hexInvalid = computed(() => hex.value.trim() !== '' && hexCode(hex.value) === null)

watch(open, isOpen => {
  if (!isOpen) return
  const current = cssColorHex(props.current)
  hex.value = current ?? ''
  spectrum.value = current ?? '#000000'
})

function pickSwatch(color: string) {
  emit('pick', color)
  open.value = false
}

function applyHex(close: boolean) {
  const color = hexCode(hex.value)
  if (!color) return
  hex.value = color
  spectrum.value = color
  if (color !== cssColorHex(props.current)) emit('pick', color)
  if (close) open.value = false
}

function applySpectrum() {
  hex.value = spectrum.value
  emit('pick', spectrum.value)
}
</script>

<template>
  <FloatingPanel v-model:open="open" :label="label" role="dialog" align="start" panel-class="w-48 p-2 space-y-2">
    <template #trigger="{ triggerAttrs }">
      <button
        type="button"
        :title="label"
        :aria-label="label"
        v-bind="triggerAttrs"
        :class="['p-1.5 rounded text-sm transition-colors', active ? 'text-primary bg-primary/10' : 'text-[var(--text)] hover:bg-[var(--bg-accent)]']"
        @mousedown.prevent
        @click="open = !open"
      >
        <font-awesome-icon :icon="icon" class="w-3.5 h-3.5" />
      </button>
    </template>
    <div class="grid grid-cols-5 gap-2">
      <button
        v-for="c in swatches"
        :key="c || 'reset'"
        type="button"
        class="w-7 h-7 rounded-full border border-[var(--border)] transition-transform hover:scale-110 cursor-pointer flex items-center justify-center"
        :style="{ background: c || 'var(--bg-accent)' }"
        :title="c || resetLabel"
        :aria-label="c || resetLabel"
        @mousedown.prevent
        @click="pickSwatch(c)"
      >
        <font-awesome-icon v-if="!c" :icon="['fas', 'xmark']" class="w-3 h-3 text-[var(--text-muted)]" />
      </button>
    </div>
    <div class="flex items-center gap-2">
      <ColorPickerInput
        v-model="spectrum"
        :title="t('markdownEditor.customColor')"
        :aria-label="t('markdownEditor.customColor')"
        class="h-8 w-10 shrink-0 rounded border border-[var(--border)] cursor-pointer bg-transparent"
        data-testid="editor-color-spectrum"
        @change="applySpectrum"
      />
      <TextInput
        v-model="hex"
        :placeholder="t('markdownEditor.hexPlaceholder')"
        :aria-label="t('markdownEditor.hexColor')"
        :aria-invalid="hexInvalid"
        class="font-mono text-xs"
        data-testid="editor-color-hex"
        @keydown.enter.prevent="applyHex(true)"
        @blur="applyHex(false)"
      />
    </div>
    <p v-if="hexInvalid" class="text-xs text-error">{{ t('markdownEditor.hexInvalid') }}</p>
  </FloatingPanel>
</template>
