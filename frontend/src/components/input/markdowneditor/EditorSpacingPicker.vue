/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import type {Editor} from '@tiptap/vue-3'
import FloatingPanel from '@/components/feedback/FloatingPanel.vue'
import EditorToolbarButton from './EditorToolbarButton.vue'
import {
  applyLetterSpacing,
  applyLineHeight,
  LETTER_SPACINGS,
  letterSpacingAtCursor,
  LINE_HEIGHTS,
  lineHeightAtCursor,
  type LetterSpacing,
  type LineHeight,
} from './textSpacing'

/**
 * The spacing entry of the text editor's menu: the line height of the paragraphs at the cursor, and
 * the spacing between the letters of the words at the cursor. Each list starts with the normal one,
 * which takes the setting off again; the one in effect is marked.
 */
const props = defineProps<{
  editor: Editor | undefined
}>()

const {t} = useI18n()
const open = ref(false)

const lineHeight = computed(() => lineHeightAtCursor(props.editor))
const letterSpacing = computed(() => letterSpacingAtCursor(props.editor))
const label = computed(() => t('markdownEditor.spacing'))

function pickLineHeight(value: LineHeight | null) {
  if (props.editor) applyLineHeight(props.editor, value)
}

function pickLetterSpacing(value: LetterSpacing | null) {
  if (props.editor) applyLetterSpacing(props.editor, value)
}
</script>

<template>
  <FloatingPanel v-model:open="open" :label="label" role="dialog" align="start" panel-class="w-56 p-2 space-y-3">
    <template #trigger="{triggerAttrs}">
      <EditorToolbarButton :icon="['fas', 'text-width']" :label="label"
                           :active="lineHeight !== null || letterSpacing !== null" data-testid="editor-spacing"
                           v-bind="triggerAttrs" @click="open = !open"/>
    </template>
    <fieldset class="space-y-1">
      <legend class="text-xs font-semibold text-(--text-muted) mb-1">{{ t('markdownEditor.lineHeight') }}</legend>
      <button v-for="value in [null, ...LINE_HEIGHTS]" :key="value ?? 'normal'" type="button"
              class="w-full text-left text-xs px-2 py-1 rounded hover:bg-(--bg-accent)"
              :class="value === lineHeight ? 'text-primary font-semibold' : 'text-(--text)'"
              :data-testid="`editor-line-height-${value ?? 'normal'}`"
              @mousedown.prevent @click="pickLineHeight(value)">
        {{ value ? t('markdownEditor.lineHeightValue', {value: value.replace('.', ',')}) : t('markdownEditor.spacingNormal') }}
      </button>
    </fieldset>
    <fieldset class="space-y-1">
      <legend class="text-xs font-semibold text-(--text-muted) mb-1">{{ t('markdownEditor.letterSpacing') }}</legend>
      <button v-for="value in [null, ...LETTER_SPACINGS]" :key="value ?? 'normal'" type="button"
              class="w-full text-left text-xs px-2 py-1 rounded hover:bg-(--bg-accent)"
              :class="value === letterSpacing ? 'text-primary font-semibold' : 'text-(--text)'"
              :data-spacing="value ?? undefined"
              :data-testid="`editor-letter-spacing-${value ?? 'normal'}`"
              @mousedown.prevent @click="pickLetterSpacing(value)">
        {{ t(`markdownEditor.letterSpacingValue.${value ?? 'normal'}`) }}
      </button>
    </fieldset>
  </FloatingPanel>
</template>
