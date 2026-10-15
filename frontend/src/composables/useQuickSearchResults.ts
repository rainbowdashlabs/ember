/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref, watch, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSession } from '@/composables/useSession'
import { useStationNav } from '@/composables/useStationNav'
import { useAdminNav } from '@/composables/useAdminNav'
import { useClusterNav } from '@/composables/useClusterNav'
import type { QuickSearchScope } from '@/composables/useQuickSearch'
import type { NavNode } from '@/data/sidebar/navTypes'
import { navPages } from '@/util/navTree'
import { StationModule, StationPermission } from '@/api/generated/schema'
import type { PersonIdentity } from '@/util/personIdentity'
import { listCompletions } from '@/api/stationMembers'
import { search as searchKb } from '@/api/knowledgeBase'
import { listUpcomingOccurrences } from '@/api/events'
import type {
  Inventory,
  MemberCompletion,
  SearchResultResponse as KbSearchResult,
  UpcomingEventOccurrence,
} from '@/api/generated/schema'
import { listInventories } from '@/api/inventory'
import { matchesWords } from '@/util/listSearch'

const MAX_PER_SECTION = 8
const SEARCH_DEBOUNCE_MS = 200
const MIN_KB_QUERY_LENGTH = 2

export interface PaletteResult {
  kind: 'page' | 'member' | 'kb' | 'event' | 'inventory'
  label: string
  sublabel?: string
  icon: string
  identity?: PersonIdentity
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
 * Pages are filtered in the browser from the scope's sidebar, so the palette offers exactly what the
 * sidebar shows the reader plus the few pages reached from inside others, and the inventories are filtered the same way and for the same reason. Members, knowledge
 * base files and events come from the server: members and inventories once per opening, the other
 * two debounced per keystroke. Every source is capped and every failure degrades to an empty
 * section, so one unavailable module cannot stop the palette from answering.
 *
 * @param query the current search text
 * @param scope which route set applies - the station palette or the admin one
 */
export function useQuickSearchResults(query: Ref<string>, scope: Ref<string>) {
  const { t } = useI18n()
  const { hasPermission, isModuleEnabled } = useSession()

  const members = ref<MemberCompletion[]>([])
  const inventories = ref<Inventory[]>([])
  const kbResults = ref<KbSearchResult[]>([])
  const eventResults = ref<UpcomingEventOccurrence[]>([])
  const dataLoading = ref(false)

  let kbDebounce: ReturnType<typeof setTimeout> | null = null
  let eventDebounce: ReturnType<typeof setTimeout> | null = null
  /** Whether the inventories of this opening are in hand, so a lost request is asked for again. */
  let inventoriesLoaded = false

  const inStation = computed(() => scope.value === 'station')
  const normalisedQuery = computed(() => query.value.trim().toLowerCase())

  const navs: Record<QuickSearchScope, Ref<NavNode[]>> = {
    station: useStationNav().nodes,
    admin: useAdminNav().nodes,
    cluster: useClusterNav().nodes,
  }

  const pageResults = computed<PaletteResult[]>(() =>
    navPages(navs[scope.value as QuickSearchScope]?.value ?? [])
      .filter(({label}) => !normalisedQuery.value || label.toLowerCase().includes(normalisedQuery.value))
      .slice(0, MAX_PER_SECTION)
      .map(page => ({
        kind: 'page' as const,
        label: page.label,
        sublabel: page.to,
        icon: page.icon,
        to: {path: page.to},
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

  const inventoryResults = computed<PaletteResult[]>(() => {
    if (!inStation.value || !normalisedQuery.value) return []
    return inventories.value
      .filter(entry => matchesWords(entry.name ?? '', normalisedQuery.value))
      .slice(0, MAX_PER_SECTION)
      .map(entry => ({
        kind: 'inventory' as const,
        label: entry.name ?? String(entry.id),
        sublabel: t(entry.homogeneous ? 'inventory.manage.kindStockName' : 'inventory.manage.kindCollectionName'),
        icon: 'warehouse',
        to: {name: 'inventory-detail', params: {id: entry.id}},
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
      label: event.name,
      sublabel: date,
      icon: 'calendar-days',
      to: {name: 'event-detail', params: {id: event.id}},
    }))
  })

  const sections = computed<PaletteSection[]>(() => [
    {key: 'pages', title: t('quickSearch.sectionPages'), items: pageResults.value},
    {key: 'members', title: t('quickSearch.sectionMembers'), items: memberResults.value},
    {key: 'events', title: t('quickSearch.sectionEvents'), items: eventResultEntries.value},
    {key: 'inventories', title: t('quickSearch.sectionInventories'), items: inventoryResults.value},
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

  /**
   * The inventories the palette filters, fetched once per opening.
   *
   * <p>A request that does not arrive leaves the section silently absent, which reads exactly like a
   * station that owns nothing, so a failed fetch is tried again on the next keystroke rather than
   * being the answer for as long as the palette stays open.
   */
  async function loadInventories() {
    if (inventoriesLoaded) return
    if (!inStation.value || !isModuleEnabled(StationModule.INVENTORY) || !hasPermission(StationPermission.INVENTORY_READ)) {
      inventories.value = []
      return
    }
    try {
      inventories.value = await listInventories()
      inventoriesLoaded = true
    } catch {
      inventories.value = []
    }
  }

  async function runKbSearch(q: string) {
    if (!inStation.value || !isModuleEnabled(StationModule.KNOWLEDGE_BASE) || q.length < MIN_KB_QUERY_LENGTH) {
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
    if (!inStation.value || !isModuleEnabled(StationModule.EVENTS)) {
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
    loadInventories()
  })

  /**
   * Loads what the palette needs on opening: the member and inventory lists it filters locally, and
   * the upcoming events shown before anything is typed.
   */
  async function loadForOpen() {
    dataLoading.value = true
    inventoriesLoaded = false
    await Promise.all([loadMembers(), loadInventories(), runEventSearch('')])
    dataLoading.value = false
  }

  return {dataLoading, sections, flatResults, loadForOpen}
}
