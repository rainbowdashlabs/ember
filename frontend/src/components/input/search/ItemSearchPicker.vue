/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import EntitySearchPicker from './EntitySearchPicker.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import {normaliseScannedPayload} from '@/components/scanner/useBarcodeScanner'
import {inventory, inventoryContainers, stationMembers} from '@/api'
import type {InventoryItem, InventorySize, Inventory, StationMember} from '@/api/types'
import type {InventoryContainer} from '@/api/inventoryContainers'

const model = defineModel<number | null>()

const props = defineProps<{
  inventoryId?: number | null
  excludeAssigned?: boolean
  excludeLost?: boolean
  excludeContainerless?: boolean
  placeholder?: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  pick: [item: InventoryItem]
  scanNotFound: [code: string]
}>()

const {t} = useI18n()

const items = ref<InventoryItem[]>([])
const containers = ref<InventoryContainer[]>([])
const members = ref<StationMember[]>([])
const inventories = ref<Inventory[]>([])
const sizes = ref<InventorySize[]>([])
const ready = ref(false)
const scanError = ref('')

const containerById = computed(() => new Map(containers.value.map(c => [c.id, c])))
const memberById = computed(() => new Map(members.value.map(m => [m.id, m])))
const inventoryById = computed(() => new Map(inventories.value.map(i => [i.id, i])))
const sizeById = computed(() => new Map(sizes.value.map(s => [s.id, s])))
const itemById = computed(() => new Map(items.value.map(i => [i.id, i])))

function pathFor(containerId: number | null | undefined): string {
  if (containerId == null) return ''
  const segments: string[] = []
  let cursor: number | null | undefined = containerId
  const seen = new Set<number>()
  while (cursor != null && !seen.has(cursor)) {
    seen.add(cursor)
    const node = containerById.value.get(cursor)
    if (!node) break
    segments.unshift(node.name)
    cursor = node.parentId ?? null
  }
  return segments.join(' / ')
}

function inventoryName(id: number): string {
  return inventoryById.value.get(id)?.name ?? t('inventory.itemPicker.unknownInventory', {id})
}

function memberName(id: number | null | undefined): string {
  if (id == null) return ''
  const m = memberById.value.get(id)
  return m?.name?.trim() || t('inventory.itemPicker.unknownMember', {id})
}

function sizeLabel(item: InventoryItem): string {
  if (!item.sizeId) return ''
  return sizeById.value.get(item.sizeId)?.label ?? ''
}

function displayName(item: InventoryItem): string {
  const base = (item.name ?? '').trim() || item.internalId || `#${item.id}`
  const size = sizeLabel(item)
  return size ? `${base} · ${size}` : base
}

type ItemState = 'member' | 'storage' | 'lost' | 'free'

function itemState(item: InventoryItem): ItemState {
  if (item.lostAt) return 'lost'
  if (item.assignedTo != null) return 'member'
  if (item.containerId != null) return 'storage'
  return 'free'
}

function locationLabel(item: InventoryItem): string {
  switch (itemState(item)) {
    case 'lost': return t('inventory.itemPicker.lost')
    case 'member': return t('inventory.itemPicker.heldBy', {name: memberName(item.assignedTo)})
    case 'storage': {
      const path = pathFor(item.containerId)
      return path ? t('inventory.itemPicker.storedAt', {path}) : t('inventory.itemPicker.unassigned')
    }
    default: return t('inventory.itemPicker.unassigned')
  }
}

function stateIcon(item: InventoryItem): string[] {
  switch (itemState(item)) {
    case 'member': return ['fas', 'user']
    case 'storage': return ['fas', 'warehouse']
    case 'lost': return ['fas', 'triangle-exclamation']
    default: return ['fas', 'cube']
  }
}

function stateIconClass(item: InventoryItem): string {
  switch (itemState(item)) {
    case 'member': return 'text-secondary-accent dark:text-secondary'
    case 'storage': return 'text-primary'
    case 'lost': return 'text-error'
    default: return 'text-(--text-muted)'
  }
}

function stateBadge(item: InventoryItem): {text: string; variant: 'success' | 'info' | 'error' | 'neutral' | 'warning'} {
  switch (itemState(item)) {
    case 'member': return {text: t('inventory.itemPicker.badgeMember'), variant: 'info'}
    case 'storage': return {text: t('inventory.itemPicker.badgeStorage'), variant: 'success'}
    case 'lost': return {text: t('inventory.itemPicker.badgeLost'), variant: 'error'}
    default: return {text: t('inventory.itemPicker.badgeFree'), variant: 'neutral'}
  }
}

function subtitle(item: InventoryItem): string {
  const parts: string[] = []
  if (item.internalId) parts.push(item.internalId)
  parts.push(inventoryName(item.inventoryId))
  parts.push(locationLabel(item))
  return parts.join(' · ')
}

function passesFilters(item: InventoryItem): boolean {
  if (props.inventoryId != null && item.inventoryId !== props.inventoryId) return false
  if (props.excludeAssigned && item.assignedTo != null) return false
  if (props.excludeLost && item.lostAt) return false
  if (props.excludeContainerless && item.containerId == null && item.assignedTo == null) return false
  return true
}

const filtered = computed(() => items.value.filter(passesFilters))

interface SearchIndex {
  id: string
  name: string
  size: string
  inventory: string
  location: string
}

const searchIndex = computed(() => {
  const map = new Map<number, SearchIndex>()
  for (const item of items.value) {
    map.set(item.id, {
      id: (item.internalId ?? '').toLowerCase(),
      name: (item.name ?? '').toLowerCase(),
      size: sizeLabel(item).toLowerCase(),
      inventory: inventoryName(item.inventoryId).toLowerCase(),
      location: locationLabel(item).toLowerCase(),
    })
  }
  return map
})

function escapeRegex(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function scoreToken(idx: SearchIndex, token: string): number {
  if (idx.id === token) return 200
  if (idx.id.startsWith(token)) return 150
  if (idx.name.startsWith(token)) return 100
  if (idx.size === token) return 90
  if (new RegExp(`\\b${escapeRegex(token)}`).test(idx.name)) return 85
  if (idx.id.includes(token)) return 70
  if (idx.name.includes(token)) return 60
  if (idx.size.includes(token)) return 50
  if (idx.inventory.includes(token)) return 30
  if (idx.location.includes(token)) return 20
  return -1
}

function scoreItem(item: InventoryItem, tokens: string[]): number {
  const idx = searchIndex.value.get(item.id)
  if (!idx) return -1
  let total = 0
  for (const t of tokens) {
    const s = scoreToken(idx, t)
    if (s < 0) return -1
    total += s
  }
  return total
}

async function searchFn(query: string): Promise<InventoryItem[]> {
  if (!ready.value) return []
  const tokens = query.toLowerCase().split(/\s+/).filter(Boolean)
  if (tokens.length === 0) {
    return filtered.value
        .slice()
        .sort((a, b) => displayName(a).localeCompare(displayName(b), 'de'))
        .slice(0, 25)
  }
  const scored: Array<[InventoryItem, number]> = []
  for (const item of filtered.value) {
    const s = scoreItem(item, tokens)
    if (s < 0) continue
    scored.push([item, s])
  }
  scored.sort((a, b) => {
    if (a[1] !== b[1]) return b[1] - a[1]
    return displayName(a[0]).localeCompare(displayName(b[0]), 'de')
  })
  return scored.slice(0, 25).map(p => p[0])
}

const innerModel = computed<string | null>({
  get: () => model.value != null ? String(model.value) : null,
  set: v => { model.value = v != null && v !== '' ? Number(v) : null },
})

const selectedDisplay = computed(() => {
  if (model.value == null) return null
  const item = itemById.value.get(model.value)
  return item ? displayName(item) : `#${model.value}`
})

async function load() {
  ready.value = false
  try {
    const [is, cs, ms, invs, szs] = await Promise.all([
      inventory.listAllItems(),
      inventoryContainers.listContainers(),
      stationMembers.listMembers(),
      inventory.listInventories(),
      inventory.listAllSizes(),
    ])
    items.value = is
    containers.value = cs
    members.value = ms
    inventories.value = invs
    sizes.value = szs
  } catch {
    items.value = []
  } finally {
    ready.value = true
  }
}

function pickItem(item: InventoryItem) {
  model.value = item.id
  emit('pick', item)
}

function onScan(value: string) {
  const term = normaliseScannedPayload(value).trim()
  if (!term) return
  scanError.value = ''
  const match = items.value.find(i => (i.internalId ?? '').toLowerCase() === term.toLowerCase())
  if (match && passesFilters(match)) {
    pickItem(match)
    return
  }
  emit('scanNotFound', term)
  if (!match) scanError.value = t('inventory.itemPicker.scanNotFound', {scan: term})
}

onMounted(load)
watch(() => model.value, val => { if (val == null) scanError.value = '' })
</script>

<template>
  <div class="space-y-2">
    <div class="flex items-stretch gap-2">
      <div class="flex-1">
        <EntitySearchPicker
            v-model="innerModel"
            :search-fn="searchFn"
            :display-fn="displayName"
            :subtitle-fn="subtitle"
            :key-fn="(item: InventoryItem) => item.id"
            :icon-fn="stateIcon"
            :icon-class-fn="stateIconClass"
            :badge-fn="stateBadge"
            :selected-display="selectedDisplay"
            :placeholder="placeholder ?? t('inventory.itemPicker.placeholder')"
            :disabled="disabled"
            :empty-label="t('inventory.itemPicker.emptyNoMatch')"
            @pick="pickItem"
        />
      </div>
      <ScanButton :disabled="disabled" @decoded="onScan" />
    </div>
    <Alert v-if="scanError" variant="error">{{ scanError }}</Alert>
  </div>
</template>
