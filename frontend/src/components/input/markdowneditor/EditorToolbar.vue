/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Editor } from '@tiptap/vue-3'
import type { FontFamilyOption } from '@/api/generated/schema'
import EditorFontPicker from './EditorFontPicker.vue'
import { alignBlock, canAlign, isAligned, type BlockAlignment } from './blockAlign'

const props = defineProps<{
  editor: Editor | undefined
  /** The families selected words can be set in; the menu offers no font where left out. */
  fonts?: readonly FontFamilyOption[]
}>()

const emit = defineEmits<{
  openLink: []
  openImage: []
  openVideo: []
}>()

const { t } = useI18n()

function isActive(name: string, attrs?: Record<string, unknown>) {
  return props.editor?.isActive(name, attrs) ?? false
}

function cmd() { return props.editor?.chain().focus() }

function setHeading(level: 1 | 2 | 3) {
  if (!props.editor) return
  if (props.editor.isActive('heading', { level })) {
    props.editor.chain().focus().setParagraph().run()
  } else {
    props.editor.chain().focus().clearNodes().setHeading({ level }).run()
  }
}

const showHighlightPicker = ref(false)
const highlightColors = [
  '', '#fef08a', '#fde68a', '#bbf7d0', '#a5f3fc', '#c4b5fd',
  '#fecaca', '#fed7aa', '#fbcfe8', '#e5e7eb',
]
function setHighlightColor(color: string) {
  if (color) { cmd()?.toggleHighlight({ color }).run() }
  else { cmd()?.unsetHighlight().run() }
  showHighlightPicker.value = false
}

const showColorPicker = ref(false)
const textColors = [
  '', '#ec2929', '#FF6421', '#ffdd1b', '#00C507', '#3694FF', '#73CEFF',
  '#9333ea', '#db2777', '#000000', '#6b7280',
]
function setTextColor(color: string) {
  if (color) { cmd()?.setColor(color).run() }
  else { cmd()?.unsetColor().run() }
  showColorPicker.value = false
}

interface ToolbarButton {
  icon: string[]
  action: () => void
  active: () => boolean
  labelKey: string
  badge?: string
  disabled?: () => boolean
}

function alignButton(alignment: BlockAlignment, icon: string, labelKey: string): ToolbarButton {
  return {
    icon: ['fas', icon],
    action: () => { if (props.editor) alignBlock(props.editor, alignment) },
    active: () => !!props.editor && canAlign(props.editor) && isAligned(props.editor, alignment),
    labelKey,
    disabled: () => !props.editor || !canAlign(props.editor),
  }
}

const toolbarButtons: ToolbarButton[][] = [
  [
    { icon: ['fas', 'bold'], action: () => cmd()?.toggleBold().run(), active: () => isActive('bold'), labelKey: 'markdownEditor.bold' },
    { icon: ['fas', 'italic'], action: () => cmd()?.toggleItalic().run(), active: () => isActive('italic'), labelKey: 'markdownEditor.italic' },
    { icon: ['fas', 'underline'], action: () => cmd()?.toggleUnderline().run(), active: () => isActive('underline'), labelKey: 'markdownEditor.underline' },
    { icon: ['fas', 'strikethrough'], action: () => cmd()?.toggleStrike().run(), active: () => isActive('strike'), labelKey: 'markdownEditor.strikethrough' },
    { icon: ['fas', 'code'], action: () => cmd()?.toggleCode().run(), active: () => isActive('code'), labelKey: 'markdownEditor.code' },
  ],
  [
    { icon: ['fas', 'heading'], action: () => setHeading(1), active: () => isActive('heading', { level: 1 }), labelKey: 'markdownEditor.heading1', badge: '1' },
    { icon: ['fas', 'heading'], action: () => setHeading(2), active: () => isActive('heading', { level: 2 }), labelKey: 'markdownEditor.heading2', badge: '2' },
    { icon: ['fas', 'heading'], action: () => setHeading(3), active: () => isActive('heading', { level: 3 }), labelKey: 'markdownEditor.heading3', badge: '3' },
    { icon: ['fas', 'paragraph'], action: () => cmd()?.clearNodes().setParagraph().run(), active: () => isActive('paragraph'), labelKey: 'markdownEditor.paragraph' },
  ],
  [
    alignButton('left', 'align-left', 'markdownEditor.alignLeft'),
    alignButton('center', 'align-center', 'markdownEditor.alignCenter'),
    alignButton('right', 'align-right', 'markdownEditor.alignRight'),
    alignButton('justify', 'align-justify', 'markdownEditor.alignJustify'),
  ],
  [
    { icon: ['fas', 'list-ul'], action: () => cmd()?.toggleBulletList().run(), active: () => isActive('bulletList'), labelKey: 'markdownEditor.bulletList' },
    { icon: ['fas', 'list-ol'], action: () => cmd()?.toggleOrderedList().run(), active: () => isActive('orderedList'), labelKey: 'markdownEditor.orderedList' },
    { icon: ['fas', 'quote-left'], action: () => cmd()?.toggleBlockquote().run(), active: () => isActive('blockquote'), labelKey: 'markdownEditor.quote' },
    { icon: ['fas', 'file-code'], action: () => cmd()?.toggleCodeBlock().run(), active: () => isActive('codeBlock'), labelKey: 'markdownEditor.codeBlock' },
    { icon: ['fas', 'minus'], action: () => cmd()?.setHorizontalRule().run(), active: () => false, labelKey: 'markdownEditor.horizontalRule' },
  ],
  [
    { icon: ['fas', 'link'], action: () => emit('openLink'), active: () => isActive('link'), labelKey: 'markdownEditor.link' },
    { icon: ['fas', 'table-columns'], action: () => cmd()?.insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run(), active: () => isActive('table'), labelKey: 'markdownEditor.table' },
    { icon: ['fas', 'play'], action: () => emit('openVideo'), active: () => false, labelKey: 'markdownEditor.video' },
    { icon: ['fas', 'image'], action: () => emit('openImage'), active: () => false, labelKey: 'markdownEditor.image' },
  ],
]
</script>

<template>
  <div class="flex flex-wrap items-center gap-0.5 px-2 py-1.5 border-b border-[var(--border)] bg-[var(--bg-accent)] sticky top-14 z-10 rounded-t-lg">
    <template v-for="(group, gi) in toolbarButtons" :key="gi">
      <div v-if="gi > 0" class="w-px h-5 bg-[var(--border)] mx-1" />
      <button
        v-for="btn in group"
        :key="btn.labelKey"
        type="button"
        :title="t(btn.labelKey)"
        :aria-label="t(btn.labelKey)"
        :disabled="btn.disabled?.()"
        :class="['p-1.5 rounded text-sm transition-colors disabled:opacity-40 disabled:cursor-not-allowed', btn.active() ? 'text-primary bg-primary/10' : 'text-[var(--text)] hover:bg-[var(--bg-accent)]']"
        @mousedown.prevent
        @click="btn.action()"
      >
        <font-awesome-icon :icon="btn.icon" class="w-3.5 h-3.5" />
        <span v-if="btn.badge" class="text-[10px] font-bold ml-px">{{ btn.badge }}</span>
      </button>
    </template>

    <div class="w-px h-5 bg-[var(--border)] mx-1" />
    <div class="relative">
      <button type="button" :title="t('markdownEditor.highlight')" :class="['p-1.5 rounded text-sm transition-colors', isActive('highlight') ? 'text-primary bg-primary/10' : 'text-[var(--text)] hover:bg-[var(--bg-accent)]']" @mousedown.prevent @click="showHighlightPicker = !showHighlightPicker; showColorPicker = false">
        <font-awesome-icon :icon="['fas', 'highlighter']" class="w-3.5 h-3.5" />
      </button>
      <div v-if="showHighlightPicker" class="absolute top-full left-0 mt-1 z-30 p-2 rounded-lg shadow-lg border border-[var(--border)] bg-[var(--bg)] grid grid-cols-5 gap-2" style="min-width: 160px">
        <button v-for="c in highlightColors" :key="c || 'remove'" type="button" class="w-7 h-7 rounded-full border border-[var(--border)] transition-transform hover:scale-110 cursor-pointer flex items-center justify-center" :style="{ background: c || 'var(--bg-accent)' }" :title="c || t('common.remove')" @mousedown.prevent @click="setHighlightColor(c)">
          <font-awesome-icon v-if="!c" :icon="['fas', 'xmark']" class="w-3 h-3 text-[var(--text-muted)]" />
        </button>
      </div>
    </div>

    <div class="relative">
      <button type="button" :title="t('markdownEditor.textColor')" class="p-1.5 rounded text-sm transition-colors text-[var(--text)] hover:bg-[var(--bg-accent)]" @mousedown.prevent @click="showColorPicker = !showColorPicker; showHighlightPicker = false">
        <font-awesome-icon :icon="['fas', 'palette']" class="w-3.5 h-3.5" />
      </button>
      <div v-if="showColorPicker" class="absolute top-full left-0 mt-1 z-30 p-2 rounded-lg shadow-lg border border-[var(--border)] bg-[var(--bg)] grid grid-cols-5 gap-2" style="min-width: 160px">
        <button v-for="c in textColors" :key="c || 'reset'" type="button" class="w-7 h-7 rounded-full border border-[var(--border)] transition-transform hover:scale-110 cursor-pointer flex items-center justify-center" :style="{ background: c || 'var(--bg-accent)' }" :title="c || t('markdownEditor.defaultColor')" @mousedown.prevent @click="setTextColor(c)">
          <font-awesome-icon v-if="!c" :icon="['fas', 'xmark']" class="w-3 h-3 text-[var(--text-muted)]" />
        </button>
      </div>
    </div>

    <EditorFontPicker v-if="fonts" :editor="editor" :fonts="fonts" />
  </div>
</template>
