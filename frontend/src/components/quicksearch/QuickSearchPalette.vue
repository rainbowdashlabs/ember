/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, nextTick, ref, watch} from 'vue'
import {useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import IconButton from '@/components/button/IconButton.vue'
import {useQuickSearch} from '@/composables/useQuickSearch'
import {useQuickSearchResults, type PaletteResult} from '@/composables/useQuickSearchResults'

const {t} = useI18n()
const router = useRouter()
const {isOpen, scope, close} = useQuickSearch()

const query = ref('')
const highlightedIndex = ref(0)
const inputWrapper = ref<HTMLDivElement | null>(null)

const {dataLoading, sections, flatResults, loadForOpen} = useQuickSearchResults(query, scope)

watch(flatResults, () => {
    highlightedIndex.value = 0
})

watch(isOpen, async (open) => {
    if (!open) {
        query.value = ''
        highlightedIndex.value = 0
        return
    }
    await loadForOpen()
    await nextTick()
    inputWrapper.value?.querySelector('input')?.focus()
})

function activateResult(result: PaletteResult) {
    close()
    router.push(result.to)
}

function indexOfItem(sectionIdx: number, itemIdx: number): number {
    let count = 0
    for (let i = 0; i < sectionIdx; i++) count += sections.value[i]?.items.length ?? 0
    return count + itemIdx
}

function moveHighlight(delta: number) {
    const total = flatResults.value.length
    if (total === 0) return
    highlightedIndex.value = (highlightedIndex.value + delta + total) % total
}

function onKeydown(event: KeyboardEvent) {
    if (event.key === 'ArrowDown') {
        event.preventDefault()
        moveHighlight(1)
    } else if (event.key === 'ArrowUp') {
        event.preventDefault()
        moveHighlight(-1)
    } else if (event.key === 'Enter') {
        const target = flatResults.value[highlightedIndex.value]
        if (target) {
            event.preventDefault()
            activateResult(target)
        }
    } else if (event.key === 'Escape') {
        event.preventDefault()
        close()
    }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="palette">
      <div v-if="isOpen" data-testid="quick-search" class="fixed inset-0 z-50 flex flex-col sm:items-start sm:justify-center sm:pt-[10vh]" @keydown="onKeydown">
        <div class="absolute inset-0 bg-black/50" @click="close"/>
        <div
            class="relative z-10 w-full flex flex-col h-[100dvh] sm:h-auto sm:max-h-[70vh] sm:max-w-2xl sm:mx-auto sm:rounded-theme border-(--border) bg-(--bg) sm:border shadow-xl"
        >
          <div class="flex items-center gap-3 border-b border-(--border) px-4 py-3 shrink-0">
            <font-awesome-icon :icon="['fas', 'magnifying-glass']" class="h-4 w-4 text-(--text-muted)"/>
            <div ref="inputWrapper" class="flex-1">
              <TextInput v-model="query" borderless :placeholder="t('quickSearch.placeholder')"/>
            </div>
            <kbd class="hidden sm:inline-flex px-1.5 py-0.5 text-xs font-mono rounded
                        border border-bg-light-accent dark:border-bg-dark-accent
                        bg-bg-light dark:bg-bg-dark text-(--text-muted)">
              {{ t('quickSearch.shortcut') }}
            </kbd>
            <IconButton
                :icon="['fas', 'xmark']"
                :label="t('quickSearch.hintClose')"
                class="sm:hidden text-(--text-muted)"
                @click="close"
            />
          </div>

          <div class="flex-1 overflow-y-auto px-2 py-2">
            <div v-if="sections.length === 0" class="py-10 text-center text-sm text-(--text-muted)">
              {{ query.trim() === '' ? t('quickSearch.startTyping') : t('quickSearch.noResults') }}
            </div>

            <div v-for="(section, sIdx) in sections" :key="section.key" class="mb-2" :data-testid="`palette-section-${section.key}`">
              <p class="px-2 pt-2 text-xs font-semibold uppercase tracking-wide text-(--text-muted)">{{ section.title }}</p>
              <div
                  v-for="(item, iIdx) in section.items"
                  :key="`${section.key}-${iIdx}`"
                  role="button"
                  tabindex="0"
                  data-testid="palette-result"
                  :class="['flex items-center gap-3 rounded-theme px-2 py-2 cursor-pointer transition-colors', indexOfItem(sIdx, iIdx) === highlightedIndex ? 'bg-primary/10 text-primary' : 'hover:bg-(--bg-accent)']"
                  @mouseenter="highlightedIndex = indexOfItem(sIdx, iIdx)"
                  @click="activateResult(item)"
                  @keydown.enter.prevent="activateResult(item)"
              >
                <UserAvatar v-if="item.identity" :identity="item.identity" :name="item.label" size="sm"/>
                <font-awesome-icon v-else :icon="['fas', item.icon]" class="h-4 w-4 shrink-0"/>
                <div class="min-w-0 flex-1">
                  <p class="text-sm font-medium truncate">{{ item.label }}</p>
                  <p v-if="item.sublabel" class="text-xs text-(--text-muted) truncate">{{ item.sublabel }}</p>
                </div>
              </div>
            </div>
          </div>

          <div class="hidden sm:flex items-center justify-between border-t border-(--border) px-4 py-2 text-xs text-(--text-muted) shrink-0">
            <span class="flex items-center gap-3">
              <span class="flex items-center gap-1">
                <kbd class="px-1.5 py-0.5 rounded bg-(--bg-accent)">↑</kbd>
                <kbd class="px-1.5 py-0.5 rounded bg-(--bg-accent)">↓</kbd>
                {{ t('quickSearch.hintNavigate') }}
              </span>
              <span class="flex items-center gap-1">
                <kbd class="px-1.5 py-0.5 rounded bg-(--bg-accent)">↵</kbd>
                {{ t('quickSearch.hintOpen') }}
              </span>
              <span class="flex items-center gap-1">
                <kbd class="px-1.5 py-0.5 rounded bg-(--bg-accent)">Esc</kbd>
                {{ t('quickSearch.hintClose') }}
              </span>
            </span>
            <span v-if="dataLoading">{{ t('quickSearch.loading') }}</span>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.palette-enter-active,
.palette-leave-active {
  transition: opacity 0.15s ease;
}
.palette-enter-from,
.palette-leave-to {
  opacity: 0;
}
</style>
