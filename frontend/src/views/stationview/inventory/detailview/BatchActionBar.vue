/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import type {InventoryArt} from '@/api/generated/schema'
import {showToast} from '@/util/toast'
import BatchArtModal from './batchactionbar/BatchArtModal.vue'
import BatchTagModal, {type BatchTagMode} from './batchactionbar/BatchTagModal.vue'

/**
 * What can be done to every ticked piece at once, held at the bottom of the list while anything is
 * ticked.
 *
 * <p>The selection stays after a change, so a kind and a tag can be given to the same pieces one
 * after the other. Setting a kind is offered only where the drawer is sorted into kinds. Ticked
 * pieces the search currently hides are changed as well, which is why the bar counts them.
 *
 * <p>It stands above the round buttons at the bottom right, and on a phone the actions are icons in
 * one line, so the bar does not cover the list it works on.
 */
const props = defineProps<{
  inventoryId: number
  itemIds: number[]
  arts: InventoryArt[]
  /** Whether the inventory is a drawer of different things, which is what kinds are for. */
  offersArt: boolean
  /** How many of the ticked pieces the search or a filter hides right now. */
  hiddenCount: number
}>()

const emit = defineEmits<{
  changed: []
  clear: []
}>()

const {t} = useI18n()

const artOpen = ref(false)
const tagOpen = ref(false)
const tagMode = ref<BatchTagMode>('add')

interface BarAction {
  key: string
  icon: string[]
  label: string
  run: () => void
}

const actions = computed<BarAction[]>(() => [
  ...(props.offersArt
      ? [{key: 'set-art', icon: ['fas', 'layer-group'], label: t('inventory.batch.setArt'), run: openArt}]
      : []),
  {key: 'add-tag', icon: ['fas', 'tag'], label: t('inventory.batch.addTag'), run: () => openTags('add')},
  {key: 'remove-tag', icon: ['fas', 'tags'], label: t('inventory.batch.removeTag'), run: () => openTags('remove')},
  {key: 'clear', icon: ['fas', 'xmark'], label: t('inventory.batch.clear'), run: () => emit('clear')},
])

function openArt() {
  artOpen.value = true
}

function openTags(mode: BatchTagMode) {
  tagMode.value = mode
  tagOpen.value = true
}

function artDone(changed: number) {
  showToast(t('inventory.batch.artDone', {count: changed}), 'success')
  emit('changed')
}

function tagsDone() {
  showToast(t(tagMode.value === 'add' ? 'inventory.batch.tagsAdded' : 'inventory.batch.tagsRemoved',
      {count: props.itemIds.length}), 'success')
  emit('changed')
}
</script>

<template>
  <div v-if="itemIds.length > 0" class="sticky bottom-20 z-10" data-testid="batch-action-bar">
    <NeutralContainer class="flex items-center justify-between gap-2 shadow-lg">
      <div class="min-w-0">
        <span class="text-sm font-medium">{{ t('inventory.batch.selected', {count: itemIds.length}) }}</span>
        <MutedText v-if="hiddenCount > 0" size="xs" tag="p" data-testid="batch-hidden-count">
          {{ t('inventory.batch.hidden', {count: hiddenCount}) }}
        </MutedText>
      </div>
      <div class="flex items-center gap-1 sm:hidden">
        <IconButton v-for="action in actions" :key="action.key" :data-testid="`batch-${action.key}`"
                    :icon="action.icon" :label="action.label" @click="action.run"/>
      </div>
      <div class="hidden flex-wrap justify-end gap-2 sm:flex">
        <SecondaryButton v-for="action in actions" :key="action.key" :icon="action.icon" @click="action.run">
          {{ action.label }}
        </SecondaryButton>
      </div>
    </NeutralContainer>
    <BatchArtModal v-model="artOpen" :arts="arts" :inventory-id="inventoryId" :item-ids="itemIds" @done="artDone"/>
    <BatchTagModal v-model="tagOpen" :inventory-id="inventoryId" :item-ids="itemIds" :mode="tagMode" @done="tagsDone"/>
  </div>
</template>
