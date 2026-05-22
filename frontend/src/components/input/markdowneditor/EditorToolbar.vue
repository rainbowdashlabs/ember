/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import type { Editor } from '@tiptap/vue-3'

const props = defineProps<{
  editor: Editor | undefined
  showRawMarkdown: boolean
  fileId?: number
}>()

const emit = defineEmits<{
  toggleRaw: []
  openLink: []
  openImage: []
  openVideo: []
}>()

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

// Highlight
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

// Text color
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
  label: string
}

const toolbarButtons: ToolbarButton[][] = [
  [
    { icon: ['fas', 'bold'], action: () => cmd()?.toggleBold().run(), active: () => isActive('bold'), label: 'Bold' },
    { icon: ['fas', 'italic'], action: () => cmd()?.toggleItalic().run(), active: () => isActive('italic'), label: 'Italic' },
    { icon: ['fas', 'underline'], action: () => cmd()?.toggleUnderline().run(), active: () => isActive('underline'), label: 'Underline' },
    { icon: ['fas', 'strikethrough'], action: () => cmd()?.toggleStrike().run(), active: () => isActive('strike'), label: 'Strikethrough' },
    { icon: ['fas', 'code'], action: () => cmd()?.toggleCode().run(), active: () => isActive('code'), label: 'Code' },
  ],
  [
    { icon: ['fas', 'heading'], action: () => setHeading(1), active: () => isActive('heading', { level: 1 }), label: 'H1' },
    { icon: ['fas', 'heading'], action: () => setHeading(2), active: () => isActive('heading', { level: 2 }), label: 'H2' },
    { icon: ['fas', 'heading'], action: () => setHeading(3), active: () => isActive('heading', { level: 3 }), label: 'H3' },
    { icon: ['fas', 'paragraph'], action: () => cmd()?.clearNodes().setParagraph().run(), active: () => isActive('paragraph'), label: 'P' },
  ],
  [
    { icon: ['fas', 'list-ul'], action: () => cmd()?.toggleBulletList().run(), active: () => isActive('bulletList'), label: 'Bullet list' },
    { icon: ['fas', 'list-ol'], action: () => cmd()?.toggleOrderedList().run(), active: () => isActive('orderedList'), label: 'Ordered list' },
    { icon: ['fas', 'quote-left'], action: () => cmd()?.toggleBlockquote().run(), active: () => isActive('blockquote'), label: 'Quote' },
    { icon: ['fas', 'file-code'], action: () => cmd()?.toggleCodeBlock().run(), active: () => isActive('codeBlock'), label: 'Code block' },
    { icon: ['fas', 'minus'], action: () => cmd()?.setHorizontalRule().run(), active: () => false, label: 'Horizontal rule' },
  ],
  [
    { icon: ['fas', 'link'], action: () => emit('openLink'), active: () => isActive('link'), label: 'Link' },
    { icon: ['fas', 'table-columns'], action: () => cmd()?.insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run(), active: () => isActive('table'), label: 'Table' },
    { icon: ['fas', 'play'], action: () => emit('openVideo'), active: () => false, label: 'Video' },
    ...(props.fileId ? [{ icon: ['fas', 'image'] as string[], action: () => emit('openImage'), active: () => false, label: 'Image' }] : []),
  ],
]
</script>

<template>
  <div class="flex flex-wrap items-center gap-0.5 px-2 py-1.5 border-b border-[var(--border)] bg-[var(--bg-accent)] sticky top-14 z-10 rounded-t-lg">
    <template v-for="(group, gi) in toolbarButtons" :key="gi">
      <div v-if="gi > 0" class="w-px h-5 bg-[var(--border)] mx-1" />
      <button
        v-for="btn in group"
        :key="btn.label"
        type="button"
        :title="btn.label"
        :class="['p-1.5 rounded text-sm transition-colors', btn.active() ? 'bg-[var(--primary)] text-white' : 'text-[var(--text)] hover:bg-[var(--bg-accent)]']"
        :disabled="showRawMarkdown"
        @mousedown.prevent
        @click="btn.action()"
      >
        <font-awesome-icon :icon="btn.icon" class="w-3.5 h-3.5" />
        <span v-if="['H1', 'H2', 'H3'].includes(btn.label)" class="text-[10px] font-bold ml-px">{{ btn.label.slice(1) }}</span>
      </button>
    </template>

    <!-- Highlight picker -->
    <div class="w-px h-5 bg-[var(--border)] mx-1" />
    <div class="relative">
      <button type="button" title="Hervorhebung" :class="['p-1.5 rounded text-sm transition-colors', isActive('highlight') ? 'bg-[var(--primary)] text-white' : 'text-[var(--text)] hover:bg-[var(--bg-accent)]']" :disabled="showRawMarkdown" @mousedown.prevent @click="showHighlightPicker = !showHighlightPicker; showColorPicker = false">
        <font-awesome-icon :icon="['fas', 'highlighter']" class="w-3.5 h-3.5" />
      </button>
      <div v-if="showHighlightPicker" class="absolute top-full left-0 mt-1 z-30 p-2 rounded-lg shadow-lg border border-[var(--border)] bg-[var(--bg)] grid grid-cols-5 gap-2" style="min-width: 160px">
        <button v-for="c in highlightColors" :key="c || 'remove'" type="button" class="w-7 h-7 rounded-full border border-[var(--border)] transition-transform hover:scale-110 cursor-pointer flex items-center justify-center" :style="{ background: c || 'var(--bg-accent)' }" :title="c || 'Entfernen'" @mousedown.prevent @click="setHighlightColor(c)">
          <font-awesome-icon v-if="!c" :icon="['fas', 'xmark']" class="w-3 h-3 text-[var(--text-muted)]" />
        </button>
      </div>
    </div>

    <!-- Text color picker -->
    <div class="relative">
      <button type="button" title="Textfarbe" class="p-1.5 rounded text-sm transition-colors text-[var(--text)] hover:bg-[var(--bg-accent)]" :disabled="showRawMarkdown" @mousedown.prevent @click="showColorPicker = !showColorPicker; showHighlightPicker = false">
        <font-awesome-icon :icon="['fas', 'palette']" class="w-3.5 h-3.5" />
      </button>
      <div v-if="showColorPicker" class="absolute top-full left-0 mt-1 z-30 p-2 rounded-lg shadow-lg border border-[var(--border)] bg-[var(--bg)] grid grid-cols-5 gap-2" style="min-width: 160px">
        <button v-for="c in textColors" :key="c || 'reset'" type="button" class="w-7 h-7 rounded-full border border-[var(--border)] transition-transform hover:scale-110 cursor-pointer flex items-center justify-center" :style="{ background: c || 'var(--bg-accent)' }" :title="c || 'Standard'" @mousedown.prevent @click="setTextColor(c)">
          <font-awesome-icon v-if="!c" :icon="['fas', 'xmark']" class="w-3 h-3 text-[var(--text-muted)]" />
        </button>
      </div>
    </div>

    <!-- Raw toggle -->
    <div class="w-px h-5 bg-[var(--border)] mx-1" />
    <button type="button" title="Markdown anzeigen" :class="['p-1.5 rounded text-sm transition-colors cursor-pointer', showRawMarkdown ? 'bg-[var(--primary)] text-white' : 'text-[var(--text)] hover:bg-[var(--bg-accent)]']" @click.stop="$emit('toggleRaw')">
      <font-awesome-icon :icon="['fas', 'file-code']" class="w-3.5 h-3.5" />
    </button>
  </div>
</template>
