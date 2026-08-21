/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref, watch, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSession } from '@/composables/useSession'
import { PALETTE_ROUTES, type PaletteRouteEntry } from '@/data/paletteRoutes'
import { StationModules, StationPermission, type MemberIdentity } from '@/api/types'
import { listCompletions, type MemberCompletion } from '@/api/stationMembers'
import { search as searchKb, type SearchResult as KbSearchResult } from '@/api/knowledgeBase'
import { listUpcomingOccurrences, type UpcomingEventOccurrence } from '@/api/events'

const MAX_PER_SECTION = 8
const SEARCH_DEBOUNCE_MS = 200
const MIN_KB_QUERY_LENGTH = 2

export interface PaletteResult {
  kind: 'page' | 'member' | 'kb' | 'event'
  label: string
  sublabel?: string
  icon: string
  identity?: MemberIdentity
  to: { name?: string; path?: string; params?: Record<string, string | number> }
}

export interface PaletteSection {
  key: string
  title: string
  items: PaletteResult[]
}

/**
 * What the quick-search palette offers for the current query.
 *
 * Pages are filtered in the browser from the static route list, because they are few and always
 * known. Members, knowledge base files and events come from the server: members once per opening,
 * the other two debounced per keystroke. Every source is capped and every failure degrades to an
 * empty section, so one unavailable module cannot stop the palette from answering.
 *
 * @param query the current search text
 * @param scope which route set applies - the station palette or the admin one
 */
export function useQuickSearchResults(query: Ref<string>, scope: Ref<string>) {
  const { t } = useI18n()
  const { hasPermission, hasClusterPermission, isModuleEnabled } = useSession()

  const members = ref<MemberCompletion[]>([])
  const kbResults = ref<KbSearchResult[]>([])
  const eventResults = ref<UpcomingEventOccurrence[]>([])
  const dataLoading = ref(false)

  let kbDebounce: ReturnType<typeof setTimeout> | null = null
  let eventDebounce: ReturnType<typeof setTimeout> | null = null

  function entryAllowed(entry: PaletteRouteEntry): boolean {
    if (entry.scope !== scope.value) return false
    if (entry.module && !isModuleEnabled(entry.module)) return false
    if (entry.scope === 'cluster') {
      // An association's pages answer to what it granted, which has nothing to do with any station
      if (entry.clusterPermission && !hasClusterPermission(entry.clusterPermission)) return false
      if (entry.clusterAnyPermission && !entry.clusterAnyPermission.some(p => hasClusterPermission(p))) return false
      return true
    }
    if (entry.permission && !hasPermission(entry.permission)) return false
    if (entry.anyPermission && !entry.anyPermission.some(p => hasPermission(p))) return false
    return true
  }

  const inStation = computed(() => scope.value === 'station')
  const normalisedQuery = computed(() => query.value.trim().toLowerCase())

  const pageResults = computed<PaletteResult[]>(() =>
    PALETTE_ROUTES.filter(entryAllowed)
      .map(entry => ({entry, label: t(entry.labelKey)}))
      .filter(({label}) => !normalisedQuery.value || label.toLowerCase().includes(normalisedQuery.value))
      .slice(0, MAX_PER_SECTION)
      .map(({entry, label}) => ({
        kind: 'page' as const,
        label,
        sublabel: entry.to,
        icon: entry.icon,
        to: {path: entry.to},
      })),
  )

  const memberResults = computed<PaletteResult[]>(() => {
    if (!inStation.value || !normalisedQuery.value) return []
    return members.value
      .filter(m => m.name.toLowerCase().includes(normalisedQuery.value))
      .slice(0, MAX_PER_SECTION)
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
    if (!inStation.value) return []
    return kbResults.value.slice(0, MAX_PER_SECTION).map(r => ({
      kind: 'kb' as const,
      label: r.file.name,
      sublabel: r.folderPath || r.snippet,
      icon: 'book-open',
      to: {name: 'kb-file', params: {id: r.file.id}},
    }))
  })

  const eventResultEntries = computed<PaletteResult[]>(() => {
    if (!inStation.value) return []
    return eventResults.value.slice(0, MAX_PER_SECTION).map(({event, date}) => ({
      kind: 'event' as const,
      label: event.name ?? `#${event.id}`,
      sublabel: date,
      icon: 'calendar-days',
      to: {name: 'event-detail', params: {id: event.id}},
    }))
  })

  const sections = computed<PaletteSection[]>(() => [
    {key: 'pages', title: t('quickSearch.sectionPages'), items: pageResults.value},
    {key: 'members', title: t('quickSearch.sectionMembers'), items: memberResults.value},
    {key: 'events', title: t('quickSearch.sectionEvents'), items: eventResultEntries.value},
    {key: 'kb', title: t('quickSearch.sectionKnowledge'), items: kbResultEntries.value},
  ].filter(section => section.items.length > 0))

  const flatResults = computed<PaletteResult[]>(() => sections.value.flatMap(s => s.items))

  async function loadMembers() {
    if (!inStation.value || !hasPermission(StationPermission.MEMBER_READ)) return
    try {
      members.value = await listCompletions()
    } catch {
      members.value = []
    }
  }

  async function runKbSearch(q: string) {
    if (!inStation.value || !isModuleEnabled(StationModules.KNOWLEDGE_BASE) || q.length < MIN_KB_QUERY_LENGTH) {
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
    if (!inStation.value || !isModuleEnabled(StationModules.EVENTS)) {
      eventResults.value = []
      return
    }
    try {
      eventResults.value = await listUpcomingOccurrences({search: q || undefined, limit: MAX_PER_SECTION})
    } catch {
      eventResults.value = []
    }
  }

  watch(query, (q) => {
    if (kbDebounce) clearTimeout(kbDebounce)
    if (eventDebounce) clearTimeout(eventDebounce)
    kbDebounce = setTimeout(() => runKbSearch(q.trim()), SEARCH_DEBOUNCE_MS)
    eventDebounce = setTimeout(() => runEventSearch(q.trim()), SEARCH_DEBOUNCE_MS)
  })

  /**
   * Loads what the palette needs on opening: the member list it filters locally, and the upcoming
   * events shown before anything is typed.
   */
  async function loadForOpen() {
    dataLoading.value = true
    await Promise.all([loadMembers(), runEventSearch('')])
    dataLoading.value = false
  }

  return {dataLoading, sections, flatResults, loadForOpen}
}
