/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import { search as kbSearch } from '@/api/knowledgeBase'
import type { SearchResult } from '@/api/knowledgeBase'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

const props = defineProps<{
  initialText: string
  initialUrl: string
  isEditing: boolean
  position: { top: number; left: number }
}>()

const emit = defineEmits<{
  apply: [url: string, text: string]
  remove: []
  cancel: []
}>()

const linkText = ref(props.initialText)
const linkUrl = ref(props.initialUrl)
const linkSearchQuery = ref(props.initialUrl)
const linkSearchResults = ref<SearchResult[]>([])
const linkSearching = ref(false)
let searchTimeout: ReturnType<typeof setTimeout> | null = null

function onSearchInput() {
  const q = linkSearchQuery.value.trim()
  if (!q || q.startsWith('http://') || q.startsWith('https://') || q.startsWith('/') || q.startsWith('#')) {
    linkSearchResults.value = []
    linkUrl.value = q
    return
  }
  linkUrl.value = q
  if (searchTimeout) clearTimeout(searchTimeout)
  searchTimeout = setTimeout(async () => {
    if (q.length < 2) { linkSearchResults.value = []; return }
    linkSearching.value = true
    try { linkSearchResults.value = await kbSearch(q) }
    catch { linkSearchResults.value = [] }
    finally { linkSearching.value = false }
  }, 300)
}

function selectResult(result: SearchResult) {
  linkUrl.value = `/station/knowledge/file/${result.file.id}`
  linkSearchQuery.value = result.file.name
  linkSearchResults.value = []
  if (!linkText.value) linkText.value = result.file.name
}
</script>

<template>
  <div
    class="absolute z-30 w-80 rounded-lg shadow-lg border border-[var(--border)] bg-[var(--bg)] p-3 space-y-2"
    :style="{ top: position.top + 'px', left: position.left + 'px' }"
  >
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2">
        <font-awesome-icon :icon="['fas', 'link']" class="text-[var(--primary)] w-3.5 h-3.5" />
        <span class="text-sm font-medium">Link</span>
      </div>
      <button type="button" class="text-[var(--text-muted)] hover:text-[var(--text)] cursor-pointer" @click="$emit('cancel')">
        <font-awesome-icon :icon="['fas', 'xmark']" class="w-3.5 h-3.5" />
      </button>
    </div>

    <div>
      <label class="block text-xs text-[var(--text-muted)] mb-0.5">Anzeigetext</label>
      <TextInput v-model="linkText" placeholder="Text des Links" class="!text-sm" />
    </div>

    <div>
      <label class="block text-xs text-[var(--text-muted)] mb-0.5">URL oder Datei suchen</label>
      <TextInput v-model="linkSearchQuery" placeholder="https://... oder Suchbegriff" class="!text-sm" @input="onSearchInput" />
    </div>

    <div v-if="linkSearchResults.length > 0" class="max-h-32 overflow-y-auto rounded border border-[var(--border)] divide-y divide-[var(--border)]">
      <button v-for="result in linkSearchResults" :key="result.file.id" type="button" class="w-full text-left px-2 py-1 text-xs hover:bg-[var(--bg-accent)] transition-colors flex items-center gap-2 cursor-pointer" @click="selectResult(result)">
        <font-awesome-icon :icon="['fas', 'file-lines']" class="w-3 h-3 text-[var(--primary)] flex-shrink-0" />
        <div class="min-w-0 flex-1">
          <div class="font-medium truncate">{{ result.file.name }}</div>
          <div class="text-[10px] text-[var(--text-muted)] truncate">{{ result.folderPath }}</div>
        </div>
      </button>
    </div>
    <div v-else-if="linkSearching" class="text-xs text-[var(--text-muted)]">
      <font-awesome-icon :icon="['fas', 'spinner']" spin class="mr-1" /> Suche...
    </div>

    <div class="flex items-center gap-2">
      <PrimaryButton class="!text-xs !py-1 !px-2" :disabled="!linkUrl" @click="$emit('apply', linkUrl, linkText)">
        <font-awesome-icon :icon="['fas', 'check']" class="mr-1" /> Einfügen
      </PrimaryButton>
      <SecondaryButton class="!text-xs !py-1 !px-2" @click="$emit('cancel')">Abbrechen</SecondaryButton>
      <button v-if="isEditing" type="button" class="text-xs text-red-500 hover:underline cursor-pointer ml-auto" @click="$emit('remove')">
        <font-awesome-icon :icon="['fas', 'link-slash']" class="mr-0.5" /> Entfernen
      </button>
    </div>
  </div>
</template>
