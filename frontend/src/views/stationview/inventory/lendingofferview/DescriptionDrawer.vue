/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import SidePanel from '@/components/feedback/SidePanel.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import IconButton from '@/components/button/IconButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import InventoryPictureField from '@/components/inventory/InventoryPictureField.vue'
import PartnerDescriptionField from '@/components/inventory/PartnerDescriptionField.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {inventory, inventoryArts, inventoryPictures} from '@/api'
import {useAsyncAction} from '@/composables/useAsyncAction'
import type {CatalogRow} from './catalogRows'

/**
 * The text and photo partners see of one product, kind or piece, edited beside the catalog: the text
 * with its preview is saved on request, a photo as soon as it is picked.
 */
const open = defineModel<boolean>({required: true})

const props = defineProps<{
  row: CatalogRow
}>()

const emit = defineEmits<{
  changed: []
}>()

const {t} = useI18n()
const text = ref(props.row.description)
const pictureKey = ref(props.row.pictureKey)
watch(() => props.row, row => {
  text.value = row.description
  pictureKey.value = row.pictureKey
})

const src = computed(() => pictureKey.value ? inventoryPictures.pictureUrl(pictureKey.value, 256) : null)

function upload(file: File): Promise<string> {
  const {target, id, inventoryId} = props.row
  if (target === 'inventory') return inventoryPictures.uploadInventoryPicture(id, file)
  if (target === 'art') return inventoryPictures.uploadArtPicture(inventoryId, id, file)
  return inventoryPictures.uploadItemPicture(id, file)
}

function remove(): Promise<void> {
  const {target, id, inventoryId} = props.row
  if (target === 'inventory') return inventoryPictures.removeInventoryPicture(id)
  if (target === 'art') return inventoryPictures.removeArtPicture(inventoryId, id)
  return inventoryPictures.removeItemPicture(id)
}

function pictureChanged(key: string | null) {
  pictureKey.value = key
  emit('changed')
}

const {run: save, running, failure} = useAsyncAction(async () => {
  const {target, id, inventoryId} = props.row
  if (target === 'inventory') await inventory.describeInventory(id, text.value)
  else if (target === 'art') await inventoryArts.describeArt(inventoryId, id, text.value)
  else await inventory.describeItem(id, text.value)
  emit('changed')
  open.value = false
})
</script>

<template>
  <SidePanel v-model="open" data-confirm-always>
    <div class="space-y-4" data-testid="lending-offer-drawer">
      <div class="flex items-center justify-between gap-2">
        <SectionHeader>{{ row.name }}</SectionHeader>
        <IconButton :icon="['fas', 'xmark']" :label="t('common.close')" @click="open = false"/>
      </div>
      <PartnerDescriptionField v-model="text"/>
      <InventoryPictureField :src="src" :upload="upload" :remove="remove" @changed="pictureChanged"/>
      <FailureAlert :failure="failure"/>
      <ButtonRow pair align="end">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :icon="['fas', 'floppy-disk']" :disabled="running" @click="save()">{{ t('common.save') }}</PrimaryButton>
      </ButtonRow>
    </div>
  </SidePanel>
</template>
