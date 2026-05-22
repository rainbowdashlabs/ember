/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import type { Editor } from '@tiptap/vue-3'

const props = defineProps<{ editor: Editor | undefined }>()

function cmd() { return props.editor?.chain().focus() }
</script>

<template>
  <div class="flex flex-wrap items-center gap-1 px-2 py-1 border-b border-[var(--border)] bg-[var(--bg-accent)] text-xs sticky top-[93px] z-10">
    <span class="text-[var(--text-muted)] mr-1">Tabelle:</span>
    <button type="button" class="px-1.5 py-0.5 rounded hover:bg-[var(--bg)] text-[var(--text)] transition-colors" title="Spalte davor" @mousedown.prevent @click="cmd()?.addColumnBefore().run()">
      <font-awesome-icon :icon="['fas', 'chevron-left']" class="w-3 h-3" /> Spalte
    </button>
    <button type="button" class="px-1.5 py-0.5 rounded hover:bg-[var(--bg)] text-[var(--text)] transition-colors" title="Spalte danach" @mousedown.prevent @click="cmd()?.addColumnAfter().run()">
      Spalte <font-awesome-icon :icon="['fas', 'chevron-right']" class="w-3 h-3" />
    </button>
    <button type="button" class="px-1.5 py-0.5 rounded hover:bg-[var(--bg)] text-[var(--text)] transition-colors" title="Zeile davor" @mousedown.prevent @click="cmd()?.addRowBefore().run()">
      <font-awesome-icon :icon="['fas', 'chevron-up']" class="w-3 h-3" /> Zeile
    </button>
    <button type="button" class="px-1.5 py-0.5 rounded hover:bg-[var(--bg)] text-[var(--text)] transition-colors" title="Zeile danach" @mousedown.prevent @click="cmd()?.addRowAfter().run()">
      Zeile <font-awesome-icon :icon="['fas', 'chevron-down']" class="w-3 h-3" />
    </button>
    <div class="w-px h-4 bg-[var(--border)] mx-1" />
    <button type="button" class="px-1.5 py-0.5 rounded hover:bg-red-100 dark:hover:bg-red-900/30 text-red-600 transition-colors" title="Spalte löschen" @mousedown.prevent @click="cmd()?.deleteColumn().run()">
      <font-awesome-icon :icon="['fas', 'xmark']" class="w-3 h-3" /> Spalte
    </button>
    <button type="button" class="px-1.5 py-0.5 rounded hover:bg-red-100 dark:hover:bg-red-900/30 text-red-600 transition-colors" title="Zeile löschen" @mousedown.prevent @click="cmd()?.deleteRow().run()">
      <font-awesome-icon :icon="['fas', 'xmark']" class="w-3 h-3" /> Zeile
    </button>
    <button type="button" class="px-1.5 py-0.5 rounded hover:bg-red-100 dark:hover:bg-red-900/30 text-red-600 transition-colors" title="Tabelle löschen" @mousedown.prevent @click="cmd()?.deleteTable().run()">
      <font-awesome-icon :icon="['fas', 'trash']" class="w-3 h-3" /> Tabelle
    </button>
  </div>
</template>
