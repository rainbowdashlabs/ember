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
import {useSession} from '@/composables/useSession'
import {PALETTE_ROUTES, type PaletteRouteEntry} from '@/data/paletteRoutes'
import {StationModules, StationPermission, type MemberIdentity} from '@/api/types'
import {listCompletions, type MemberCompletion} from '@/api/stationMembers'
import {search as searchKb, type SearchResult as KbSearchResult} from '@/api/knowledgeBase'
import {listUpcomingOccurrences, type UpcomingEventOccurrence} from '@/api/events'

interface PaletteResult {
    kind: 'page' | 'member' | 'kb' | 'event'
    label: string
    sublabel?: string
    icon: string
    identity?: MemberIdentity
    to: { name?: string; path?: string; params?: Record<string, string | number> }
}

const {t} = useI18n()
const router = useRouter()
const {isOpen, scope, close} = useQuickSearch()
const {hasPermission, isModuleEnabled} = useSession()

const query = ref('')
const highlightedIndex = ref(0)
const inputWrapper = ref<HTMLDivElement | null>(null)

const members = ref<MemberCompletion[]>([])
const kbResults = ref<KbSearchResult[]>([])
const eventResults = ref<UpcomingEventOccurrence[]>([])
const dataLoading = ref(false)

let kbDebounce: ReturnType<typeof setTimeout> | null = null
let eventDebounce: ReturnType<typeof setTimeout> | null = null

function entryAllowed(entry: PaletteRouteEntry): boolean {
    if (entry.scope !== scope.value) return false
    if (entry.module && !isModuleEnabled(entry.module)) return false
    if (entry.permission && !hasPermission(entry.permission)) return false
    if (entry.anyPermission && !entry.anyPermission.some(p => hasPermission(p))) return false
    return true
}

const allowedRoutes = computed(() => PALETTE_ROUTES.filter(entryAllowed))

const pageResults = computed<PaletteResult[]>(() => {
    const q = query.value.trim().toLowerCase()
    const items = allowedRoutes.value
        .map(entry => ({entry, label: t(entry.labelKey)}))
        .filter(({label}) => q === '' || label.toLowerCase().includes(q))
        .slice(0, 8)
    return items.map(({entry, label}) => ({
        kind: 'page' as const,
        label,
        sublabel: entry.to,
        icon: entry.icon,
        to: {path: entry.to},
    }))
})

const memberResults = computed<PaletteResult[]>(() => {
    if (scope.value !== 'station') return []
    const q = query.value.trim().toLowerCase()
    if (q === '') return []
    return members.value
        .filter(m => m.name.toLowerCase().includes(q))
        .slice(0, 8)
        .map(m => ({
            kind: 'member' as const,
            label: m.name,
            sublabel: m.displayTag?.name ?? undefined,
            icon: 'user',
            identity: {
                stationUid: m.stationUid,
                memberUid: m.memberUid,
                name: m.name,
                nameColor: m.nameColor ?? null,
                displayTag: m.displayTag ?? null,
            },
            to: {name: 'members-detail', params: {id: m.id}},
        }))
})

const kbResultEntries = computed<PaletteResult[]>(() => {
    if (scope.value !== 'station') return []
    return kbResults.value.slice(0, 8).map(r => ({
        kind: 'kb' as const,
        label: r.file.name,
        sublabel: r.folderPath || r.snippet,
        icon: 'book-open',
        to: {name: 'kb-file', params: {id: r.file.id}},
    }))
})

const eventResultEntries = computed<PaletteResult[]>(() => {
    if (scope.value !== 'station') return []
    return eventResults.value.slice(0, 8).map(({event, date}) => ({
        kind: 'event' as const,
        label: event.name ?? `#${event.id}`,
        sublabel: date,
        icon: 'calendar-days',
        to: {name: 'event-detail', params: {id: event.id}},
    }))
})

interface PaletteSection {
    key: string
    title: string
    items: PaletteResult[]
}

const sections = computed<PaletteSection[]>(() => {
    const out: PaletteSection[] = []
    if (pageResults.value.length > 0) out.push({key: 'pages', title: t('quickSearch.sectionPages'), items: pageResults.value})
    if (memberResults.value.length > 0) out.push({key: 'members', title: t('quickSearch.sectionMembers'), items: memberResults.value})
    if (eventResultEntries.value.length > 0) out.push({key: 'events', title: t('quickSearch.sectionEvents'), items: eventResultEntries.value})
    if (kbResultEntries.value.length > 0) out.push({key: 'kb', title: t('quickSearch.sectionKnowledge'), items: kbResultEntries.value})
    return out
})

const flatResults = computed<PaletteResult[]>(() => sections.value.flatMap(s => s.items))

watch(flatResults, () => {
    highlightedIndex.value = 0
})

async function loadMembers() {
    if (scope.value !== 'station') return
    if (!hasPermission(StationPermission.MEMBER_READ)) return
    try {
        members.value = await listCompletions()
    } catch {
        members.value = []
    }
}

async function runKbSearch(q: string) {
    if (scope.value !== 'station' || !isModuleEnabled(StationModules.KNOWLEDGE_BASE) || q.length < 2) {
        kbResults.value = []
        return
    }
    try {
        kbResults.value = await searchKb(q)
    } catch {
        kbResults.value = []
    }
}

async function runEventSearch(q: string) {
    if (scope.value !== 'station' || !isModuleEnabled(StationModules.EVENTS)) {
        eventResults.value = []
        return
    }
    try {
        eventResults.value = await listUpcomingOccurrences({search: q || undefined, limit: 8})
    } catch {
        eventResults.value = []
    }
}

watch(query, (q) => {
    if (kbDebounce) clearTimeout(kbDebounce)
    if (eventDebounce) clearTimeout(eventDebounce)
    kbDebounce = setTimeout(() => runKbSearch(q.trim()), 200)
    eventDebounce = setTimeout(() => runEventSearch(q.trim()), 200)
})

watch(isOpen, async (open) => {
    if (!open) {
        query.value = ''
        highlightedIndex.value = 0
        return
    }
    dataLoading.value = true
    await Promise.all([loadMembers(), runEventSearch('')])
    dataLoading.value = false
    await nextTick()
    const input = inputWrapper.value?.querySelector('input')
    input?.focus()
})

function activateResult(result: PaletteResult) {
    close()
    router.push(result.to)
}

function indexOfItem(sectionIdx: number, itemIdx: number): number {
    let count = 0
    for (let i = 0; i < sectionIdx; i++) count += sections.value[i].items.length
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
      <div v-if="isOpen" class="fixed inset-0 z-50 flex flex-col sm:items-start sm:justify-center sm:pt-[10vh]" @keydown="onKeydown">
        <div class="absolute inset-0 bg-black/50" @click="close"/>
        <div
            class="relative z-10 w-full flex flex-col h-[100dvh] sm:h-auto sm:max-h-[70vh] sm:max-w-2xl sm:mx-auto sm:rounded-theme border-(--border) bg-(--bg) sm:border shadow-xl"
        >
          <div class="flex items-center gap-3 border-b border-(--border) px-4 py-3 shrink-0">
            <font-awesome-icon :icon="['fas', 'magnifying-glass']" class="h-4 w-4 text-(--text-muted)"/>
            <div ref="inputWrapper" class="flex-1">
              <TextInput v-model="query" borderless :placeholder="t('quickSearch.placeholder')"/>
            </div>
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

            <div v-for="(section, sIdx) in sections" :key="section.key" class="mb-2">
              <p class="px-2 pt-2 text-xs font-semibold uppercase tracking-wide text-(--text-muted)">{{ section.title }}</p>
              <div
                  v-for="(item, iIdx) in section.items"
                  :key="`${section.key}-${iIdx}`"
                  role="button"
                  tabindex="0"
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
