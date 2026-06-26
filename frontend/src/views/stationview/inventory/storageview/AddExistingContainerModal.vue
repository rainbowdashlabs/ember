/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'
import {normaliseScannedPayload} from '@/components/scanner/useBarcodeScanner'
import {mapContainerError} from '@/views/stationview/inventory/storageview/containerErrors'
import {inventoryContainers} from '@/api'
import type {InventoryContainer, InventoryContainerKind} from '@/api/inventoryContainers'

const props = defineProps<{
  targetContainerId: number
  containers: InventoryContainer[]
  kinds: InventoryContainerKind[]
}>()

const emit = defineEmits<{
  moved: []
  close: []
}>()

const {t} = useI18n()

const open = ref(true)
const search = ref('')
const submitting = ref(false)
const error = ref('')
const movedIds = ref<Set<number>>(new Set())

const descendantIds = ref<Set<number>>(new Set())

const kindById = computed(() => {
  const map = new Map<number, InventoryContainerKind>()
  for (const k of props.kinds) map.set(k.id, k)
  return map
})

const ancestorIds = computed(() => {
  const set = new Set<number>()
  const byId = new Map<number, InventoryContainer>()
  for (const c of props.containers) byId.set(c.id, c)
  let cursor = byId.get(props.targetContainerId)?.parentId ?? null
  while (cursor != null && !set.has(cursor)) {
    set.add(cursor)
    cursor = byId.get(cursor)?.parentId ?? null
  }
  return set
})

const eligibleContainers = computed(() => {
  return props.containers.filter(c =>
      c.id !== props.targetContainerId
      && !descendantIds.value.has(c.id)
      && !ancestorIds.value.has(c.id)
      && c.parentId !== props.targetContainerId)
})

const filtered = computed(() => {
  const term = search.value.trim().toLowerCase()
  const base = eligibleContainers.value
  if (!term) return base
  return base.filter(c =>
      c.name.toLowerCase().includes(term)
      || (c.internalId ?? '').toLowerCase().includes(term)
      || (c.description ?? '').toLowerCase().includes(term))
})

async function loadDescendants() {
  try {
    const subtree = await inventoryContainers.getContainerContents(props.targetContainerId, true)
    descendantIds.value = new Set(subtree.children.map(c => c.id))
  } catch {
    descendantIds.value = new Set()
  }
}

async function moveHere(c: InventoryContainer) {
  submitting.value = true
  error.value = ''
  try {
    await inventoryContainers.updateContainer(c.id, {
      parentId: props.targetContainerId,
      internalId: c.internalId ?? null,
      name: c.name,
      kindId: c.kindId,
      description: c.description ?? '',
    })
    movedIds.value.add(c.id)
    emit('moved')
  } catch (e: any) {
    error.value = mapContainerError(t, e, 'inventory.storage.addExisting.moveFailed')
  } finally {
    submitting.value = false
  }
}

function isEligible(c: InventoryContainer): boolean {
  return c.id !== props.targetContainerId
      && !descendantIds.value.has(c.id)
      && !ancestorIds.value.has(c.id)
      && c.parentId !== props.targetContainerId
}

async function onScan(value: string) {
  const term = normaliseScannedPayload(value).trim()
  if (!term || submitting.value) return
  error.value = ''
  const container = await inventoryContainers.resolveContainerByScan(term)
  if (!container) {
    error.value = t('inventory.storage.addExisting.scanNotFound', {scan: term})
    return
  }
  if (container.id === props.targetContainerId) {
    error.value = t('inventory.storage.scan.selfTarget')
    return
  }
  if (container.parentId === props.targetContainerId) {
    error.value = t('inventory.storage.scan.containerAlreadyHere', {name: container.name})
    return
  }
  if (!isEligible(container)) {
    error.value = t('inventory.storage.addExisting.scanIneligible', {name: container.name})
    return
  }
  await moveHere(container)
}

function onClose() {
  open.value = false
  emit('close')
}

onMounted(loadDescendants)
</script>

<template>
  <Modal v-model="open" size="lg" @update:modelValue="(v) => { if (!v) onClose() }">
    <SubHeader class="mb-2">{{ t('inventory.storage.addExisting.title') }}</SubHeader>
    <p class="text-xs text-(--text-muted) mb-3">{{ t('inventory.storage.addExisting.intro') }}</p>

    <Alert v-if="error" variant="error" class="mb-3">{{ error }}</Alert>

    <div class="flex items-center gap-2 mb-3">
      <SearchInput v-model="search" :placeholder="t('inventory.storage.addExisting.searchPlaceholder')" class="flex-1" />
      <ScanButton @decoded="onScan" />
    </div>

    <EmptyState v-if="filtered.length === 0" :message="t('inventory.storage.addExisting.empty')" />
    <ul v-else class="divide-y divide-(--bg-accent) max-h-96 overflow-y-auto">
      <li v-for="c in filtered" :key="c.id" class="py-2 flex items-center gap-3 text-sm">
        <font-awesome-icon :icon="['fas', kindById.get(c.kindId ?? -1)?.icon ?? 'box']" class="w-4 text-(--text-muted)" />
        <span class="font-medium">{{ c.name }}</span>
        <span v-if="c.internalId" class="text-xs text-(--text-muted)">{{ c.internalId }}</span>
        <span v-if="movedIds.has(c.id)" class="ml-auto text-xs text-success">{{ t('inventory.storage.addExisting.moved') }}</span>
        <PrimaryButton
            v-else
            size="sm"
            class="ml-auto"
            :disabled="submitting"
            @click="moveHere(c)"
        >
          <font-awesome-icon :icon="['fas', 'arrow-right']" class="mr-1" />
          {{ t('inventory.storage.addExisting.moveHere') }}
        </PrimaryButton>
      </li>
    </ul>

    <div class="flex justify-end mt-4">
      <SecondaryButton @click="onClose">{{ t('common.close') }}</SecondaryButton>
    </div>
  </Modal>
</template>
