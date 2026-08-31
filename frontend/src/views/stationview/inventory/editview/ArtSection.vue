/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import LendingShareButton from '@/components/lending/LendingShareButton.vue'
import {inventoryArts} from '@/api'
import type {ArtStock, InventoryArt} from '@/api/inventoryArts'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {apiErrorMessage} from '@/util/apiError'

/**
 * The kinds of thing an inventory holds.
 *
 * Only shown for an inventory that holds a drawer of different things: an inventory of one thing in
 * many copies is structured by its sizes, and giving it kinds as well would make a requirement mean
 * something nobody chose.
 */
const props = defineProps<{
  inventoryId: number
}>()

const {t} = useI18n()
const routes = useInventoryRoutes()

const arts = ref<InventoryArt[]>([])
const stock = ref<ArtStock[]>([])
const error = ref('')

const showModal = ref(false)
const editingArt = ref<InventoryArt | null>(null)
const artName = ref('')
const artNote = ref('')

const stockByArt = computed(() => new Map(stock.value.map(row => [row.artId, row])))

async function load() {
  error.value = ''
  try {
    const [allArts, counts] = await Promise.all([
      inventoryArts.listArts(props.inventoryId),
      inventoryArts.artStock(props.inventoryId),
    ])
    arts.value = allArts
    stock.value = counts
  } catch (e) {
    error.value = apiErrorMessage(e) ?? t('common.error')
  }
}

function openAdd() {
  editingArt.value = null
  artName.value = ''
  artNote.value = ''
  showModal.value = true
}

function openEdit(art: InventoryArt) {
  editingArt.value = art
  artName.value = art.name
  artNote.value = art.note
  showModal.value = true
}

async function saveArt() {
  error.value = ''
  try {
    if (editingArt.value) {
      await inventoryArts.updateArt(props.inventoryId, editingArt.value.id, {
        name: artName.value,
        note: artNote.value,
        position: editingArt.value.position,
      })
    } else {
      await inventoryArts.createArt(props.inventoryId, {
        name: artName.value,
        note: artNote.value,
        position: arts.value.length * 10,
      })
    }
    showModal.value = false
    await load()
  } catch (e) {
    error.value = apiErrorMessage(e) ?? t('common.error')
    throw e
  }
}

const {
  show: showDeleteModal,
  target: deleteTarget,
  requestDelete,
  confirm: confirmDelete,
} = useConfirmDelete<InventoryArt>({
  onDelete: art => inventoryArts.deleteArt(props.inventoryId, art.id),
  onSuccess: () => load(),
  error,
})

watch(() => props.inventoryId, load, {immediate: true})
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="inventory-arts">
    <div class="flex items-center justify-between">
      <SubHeader>{{ t('inventory.art.title') }}</SubHeader>
      <div class="flex items-center gap-2">
        <SecondaryButton v-if="routes.tidy" :icon="['fas', 'broom']" data-testid="open-tidy"
                         @click="$router.push({name: routes.tidy, params: {id: String(props.inventoryId)}})">
          {{ t('inventory.art.tidyLink') }}
        </SecondaryButton>
        <SecondaryButton :icon="['fas', 'plus']" data-testid="add-art" @click="openAdd">
          {{ t('inventory.art.add') }}
        </SecondaryButton>
      </div>
    </div>
    <p class="text-sm text-(--text-muted)">{{ t('inventory.art.intro') }}</p>

    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <div v-for="art in arts" :key="art.id"
         class="flex items-center justify-between px-3 py-2 border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
      <div>
        <span class="text-sm font-medium">{{ art.name }}</span>
        <MutedText v-if="art.note" class="ml-2">{{ art.note }}</MutedText>
      </div>
      <div class="flex items-center gap-3">
        <MutedText size="sm">
          {{ t('inventory.art.pieces', {pieces: stockByArt.get(art.id)?.pieces ?? 0, free: stockByArt.get(art.id)?.free ?? 0}) }}
        </MutedText>
        <LendingShareButton :target-id="art.id" :target-name="art.name" target="art"/>
        <EditButton @click="openEdit(art)"/>
        <DeleteButton @click="requestDelete(art)"/>
      </div>
    </div>

    <div v-if="arts.length === 0" class="text-center text-(--text-muted) py-4 text-sm">
      {{ t('inventory.art.none2') }}
    </div>
  </NeutralContainer>

  <Modal v-model="showModal">
    <div class="space-y-4">
      <SectionHeader>{{ editingArt ? t('inventory.art.edit') : t('inventory.art.add') }}</SectionHeader>
      <div class="space-y-1">
        <FieldLabel>{{ t('inventory.art.name') }}</FieldLabel>
        <TextInput v-model="artName" data-testid="art-name" :placeholder="t('inventory.art.namePlaceholder')"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('inventory.art.note') }}</FieldLabel>
        <TextInput v-model="artNote" :placeholder="t('inventory.art.notePlaceholder')"/>
      </div>
      <div class="flex justify-end gap-3">
        <SecondaryButton @click="showModal = false">{{ t('common.cancel') }}</SecondaryButton>
        <SaveButton :disabled="!artName.trim()" :action="saveArt"/>
      </div>
    </div>
  </Modal>

  <ConfirmDeleteModal
      v-model="showDeleteModal"
      :message="t('inventory.art.deleteConfirm', {name: deleteTarget?.name})"
      @confirm="confirmDelete"
  />
</template>
