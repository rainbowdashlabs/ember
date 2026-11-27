/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {inventory, inventoryArts, inventoryTags} from '@/api'
import type {
  InventoryArt,
  InventoryDetail,
  InventoryItem,
  InventorySize,
  InventoryTag,
  ItemLocationResponse,
  TagResponse,
} from '@/api/generated/schema'
import type {MemberLike} from '@/components/input/select/memberOption'
import GearGlyph from '@/components/inventory/GearGlyph.vue'
import AuthImage from '@/components/display/AuthImage.vue'
import {itemPictureUrl} from '@/api/inventoryPictures'
import {pieceGlyph} from '@/util/glyph'
import ItemEditForm from './ItemEditForm.vue'
import ItemMetadataDisplay from './ItemMetadataDisplay.vue'
import ItemOwnerField from '../ItemOwnerField.vue'
import {useItemOwnerChoice} from '../useItemOwnerChoice'
import {parseItemMetadata} from '../detailview/itemMetadata'
import {describeFailure, type Failure} from '@/util/failure'

const props = defineProps<{
  item: InventoryItem
  sizes: InventorySize[]
  members: MemberLike[]
  location: ItemLocationResponse | null
  canEditItem: boolean
  /** The piece's own photo, shown before its kind's and its inventory's, or null where it has none. */
  ownPictureKey?: string | null
}>()

const emit = defineEmits<{
  updated: [item: InventoryItem]
  error: [failure: Failure]
}>()

const {t} = useI18n()

const editing = ref(false)
const editName = ref('')
const editInternalId = ref('')
const editSizeId = ref('')
const editArtId = ref<number | null>(null)
const editArtDraft = ref('')
const arts = ref<InventoryArt[]>([])
const owningInventory = ref<InventoryDetail | null>(null)
const itemTags = ref<InventoryTag[]>([])
const stationTags = ref<TagResponse[]>([])
const editTagNames = ref<string[]>([])
const ownerChoice = useItemOwnerChoice(() => props.item)
const {owner: editOwner, offered: ownerOffered, atRest: ownerAtRest} = ownerChoice

/**
 * The kinds this piece's inventory holds. An empty list is the ordinary answer, because most
 * inventories hold one thing in many copies and have none, so nothing here insists on a kind.
 */
async function loadArts() {
  try {
    arts.value = await inventoryArts.listArts(props.item.inventoryId)
  } catch {
    arts.value = []
  }
}

/** The inventory the piece lies in, read for its picture. Without it the piece keeps a plain shape. */
async function loadInventory() {
  try {
    owningInventory.value = await inventory.getInventory(props.item.inventoryId)
  } catch {
    owningInventory.value = null
  }
}

watch(() => props.item.inventoryId, () => Promise.all([loadArts(), loadInventory()]), {immediate: true})

/** The tags this piece carries. A failure shows it without tags rather than without its page. */
async function loadItemTags() {
  try {
    itemTags.value = await inventoryTags.itemTags(props.item.id)
  } catch {
    itemTags.value = []
  }
}

watch(() => props.item.id, loadItemTags, {immediate: true})

/** The station's tags to pick from, fetched when editing starts since only the form offers them. */
async function loadStationTags() {
  try {
    stationTags.value = await inventoryTags.listTags()
  } catch {
    stationTags.value = []
  }
}

const showArt = computed(() => arts.value.length > 0 || props.item.artId != null)

const itemTagNames = computed(() => itemTags.value.map(tag => tag.name))

const art = computed(() => arts.value.find(candidate => candidate.id === props.item.artId) ?? null)

const artName = computed(() => art.value?.name ?? null)

/** The kind's picture where the kind has one, otherwise the inventory's. */
const glyph = computed(() => pieceGlyph(art.value, owningInventory.value))

/** The photo the piece is shown with: its own, otherwise the kind's, otherwise the inventory's. */
const pictureSrc = computed(() => {
  const key = props.ownPictureKey ?? art.value?.pictureKey ?? owningInventory.value?.pictureKey
  return key ? itemPictureUrl(props.item.id, key, 512) : null
})

function startEdit() {
  editName.value = props.item.name ?? ''
  editInternalId.value = props.item.internalId ?? ''
  editSizeId.value = props.item.sizeId != null ? String(props.item.sizeId) : ''
  editArtId.value = props.item.artId ?? null
  editArtDraft.value = ''
  editTagNames.value = [...itemTagNames.value]
  ownerChoice.reset()
  void loadStationTags()
  editing.value = true
}

/**
 * Writes the piece's description back.
 *
 * <p>A kind typed into the picker reaches the server only now, together with the piece it was typed for. The
 * tags are written after the description, and a changed owner is handed over last.
 */
async function saveEdit() {
  try {
    const resolvedArt = editArtDraft.value
        ? await inventoryArts.ensureArt(props.item.inventoryId, arts.value, editArtDraft.value)
        : editArtId.value
    const updated = await inventory.updateItem(props.item.id, {
      name: editName.value,
      internalId: editInternalId.value,
      sizeId: editSizeId.value ? Number(editSizeId.value) : undefined,
      artId: showArt.value ? resolvedArt : props.item.artId ?? null,
      metadata: parseItemMetadata(props.item.metadata),
    })
    itemTags.value = await inventoryTags.setItemTags(props.item.id, editTagNames.value)
    const reowned = await ownerChoice.apply()
    editing.value = false
    if (editArtDraft.value) await loadArts()
    emit('updated', reowned ?? updated)
  } catch (e) {
    emit('error', describeFailure(e, t))
    throw e
  }
}

const assignedMemberIdentity = computed(() => {
  const id = props.item.assignedTo
  if (!id) return null
  return props.members.find(m => m.id === id)?.identity ?? null
})
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex items-center justify-between">
      <div class="flex min-w-0 items-center gap-2">
        <GearGlyph :glyph="glyph" size="lg"/>
        <SubHeader>{{ props.item.name }}</SubHeader>
      </div>
      <SecondaryButton v-if="props.canEditItem && !editing" data-testid="item-edit"
                       :icon="['fas', 'pen']" @click="startEdit">
        {{ t('itemDetail.edit') }}
      </SecondaryButton>
    </div>

    <AuthImage v-if="pictureSrc" :src="pictureSrc" alt="" data-testid="item-picture"
               class="max-h-64 w-full rounded-theme border border-(--border) object-contain sm:w-auto"/>

    <ItemEditForm
      v-if="editing"
      v-model:name="editName"
      v-model:internal-id="editInternalId"
      v-model:size-id="editSizeId"
      v-model:art-id="editArtId"
      v-model:art-draft="editArtDraft"
      v-model:tag-names="editTagNames"
      :tags="stationTags"
      :sizes="props.sizes"
      :arts="arts"
      :show-art="showArt"
      :save="saveEdit"
      @cancel="editing = false"
    >
      <ItemOwnerField v-if="ownerOffered" v-model="editOwner" :at-rest="ownerAtRest"/>
    </ItemEditForm>

    <ItemMetadataDisplay
      v-else
      :item="props.item"
      :sizes="props.sizes"
      :art-name="artName"
      :tag-names="itemTagNames"
      :location="props.location"
      :assigned-member-identity="assignedMemberIdentity"
    />
  </NeutralContainer>
</template>
