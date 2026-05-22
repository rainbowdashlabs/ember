/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import type { Editor } from '@tiptap/vue-3'
import { BubbleMenu } from '@tiptap/vue-3/menus'

const props = defineProps<{
  editor: Editor
  isOnLink: boolean
  currentLinkUrl: string
  showLinkDialog: boolean
}>()

const emit = defineEmits<{
  openLink: []
  removeLink: []
}>()

const bubbleHidden = ref(false)

function isActive(name: string, attrs?: Record<string, unknown>) {
  return props.editor.isActive(name, attrs)
}

// Reset on selection change
props.editor.on('selectionUpdate', () => { bubbleHidden.value = false })

interface ToolbarButton {
  icon: string[]
  action: () => void
  active: () => boolean
  label: string
}

const cmd = () => props.editor.chain().focus()

const buttons: ToolbarButton[] = [
  { icon: ['fas', 'bold'], action: () => cmd().toggleBold().run(), active: () => isActive('bold'), label: 'Bold' },
  { icon: ['fas', 'italic'], action: () => cmd().toggleItalic().run(), active: () => isActive('italic'), label: 'Italic' },
  { icon: ['fas', 'underline'], action: () => cmd().toggleUnderline().run(), active: () => isActive('underline'), label: 'Underline' },
  { icon: ['fas', 'strikethrough'], action: () => cmd().toggleStrike().run(), active: () => isActive('strike'), label: 'Strikethrough' },
  { icon: ['fas', 'code'], action: () => cmd().toggleCode().run(), active: () => isActive('code'), label: 'Code' },
  { icon: ['fas', 'link'], action: () => emit('openLink'), active: () => isActive('link'), label: 'Link' },
]
</script>

<template>
  <BubbleMenu
    v-if="!bubbleHidden && !showLinkDialog"
    :editor="editor"
    :should-show="({ editor: e }) => !e.state.selection.empty || (e.isActive('link') && e.state.selection.empty)"
    :tippy-options="{ duration: 150 }"
    class="flex items-center gap-0.5 px-1.5 py-1 rounded-lg shadow-lg border border-[var(--border)] bg-[var(--bg)]"
  >
    <!-- Text selection: formatting buttons -->
    <template v-if="!editor.state.selection.empty">
      <button
        v-for="btn in buttons"
        :key="btn.label"
        type="button"
        :title="btn.label"
        :class="['p-1.5 rounded text-sm transition-colors', btn.active() ? 'bg-[var(--primary)] text-white' : 'text-[var(--text)] hover:bg-[var(--bg-accent)]']"
        @mousedown.prevent
        @click="btn.action()"
      >
        <font-awesome-icon :icon="btn.icon" class="w-3.5 h-3.5" />
      </button>
      <button v-if="isOnLink" type="button" title="Link entfernen" class="p-1.5 rounded text-sm transition-colors text-red-500 hover:bg-red-100 dark:hover:bg-red-900/30" @mousedown.prevent @click="$emit('removeLink')">
        <font-awesome-icon :icon="['fas', 'link-slash']" class="w-3.5 h-3.5" />
      </button>
      <div class="w-px h-4 bg-[var(--border)] mx-0.5" />
      <button type="button" title="Schließen" class="p-1 rounded text-xs transition-colors text-[var(--text-muted)] hover:bg-[var(--bg-accent)]" @mousedown.prevent @click="bubbleHidden = true">
        <font-awesome-icon :icon="['fas', 'xmark']" class="w-3 h-3" />
      </button>
    </template>

    <!-- Link tooltip: cursor on link, no selection -->
    <template v-else-if="isOnLink">
      <font-awesome-icon :icon="['fas', 'link']" class="w-3 h-3 text-[var(--text-muted)] flex-shrink-0" />
      <span class="truncate text-[var(--text-muted)] text-xs max-w-[200px] mx-1" :title="currentLinkUrl">{{ currentLinkUrl }}</span>
      <a :href="currentLinkUrl" target="_blank" rel="noopener noreferrer" class="p-1 rounded hover:bg-[var(--bg-accent)] text-[var(--primary)] transition-colors flex-shrink-0" title="Link öffnen" @mousedown.prevent>
        <font-awesome-icon :icon="['fas', 'arrow-right']" class="w-3 h-3" />
      </a>
      <button type="button" title="Link bearbeiten" class="p-1 rounded hover:bg-[var(--bg-accent)] text-[var(--text)] transition-colors flex-shrink-0" @mousedown.prevent @click="$emit('openLink')">
        <font-awesome-icon :icon="['fas', 'pen']" class="w-3 h-3" />
      </button>
      <button type="button" title="Link entfernen" class="p-1 rounded hover:bg-red-100 dark:hover:bg-red-900/30 text-red-500 transition-colors flex-shrink-0" @mousedown.prevent @click="$emit('removeLink')">
        <font-awesome-icon :icon="['fas', 'link-slash']" class="w-3 h-3" />
      </button>
    </template>
  </BubbleMenu>
</template>
