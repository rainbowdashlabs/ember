/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {inventoryCollections} from '@/api'
import type {InventoryCollection} from '@/api/inventoryCollections'
import {useAsyncAction} from '@/composables/useAsyncAction'

/**
 * Puts a piece into a collection from the piece's own screen, so a kit can be filled from either
 * side: from the collection, or from the drawer somebody happens to be standing in front of.
 */
const props = defineProps<{
  itemId: number
}>()

const {t} = useI18n()

const show = ref(false)
const collections = ref<InventoryCollection[]>([])
const chosen = ref('')
const added = ref(false)

async function open() {
  added.value = false
  chosen.value = ''
  collections.value = await inventoryCollections.list()
  show.value = true
}

const {running, error, run: submit} = useAsyncAction(async () => {
  if (!chosen.value) return
  await inventoryCollections.addItemLine(Number(chosen.value), props.itemId)
  added.value = true
  show.value = false
}, {formatError: () => t('common.error')})
</script>

<template>
  <SecondaryButton :icon="['fas', 'box-open']" data-testid="item-add-to-collection" @click="open">
    {{ t('inventory.collections.addToCollection') }}
  </SecondaryButton>

  <Alert v-if="added" variant="success">{{ t('inventory.collections.addedToCollection') }}</Alert>

  <Modal v-model="show" size="md">
    <div class="space-y-4">
      <SubHeader>{{ t('inventory.collections.addToCollection') }}</SubHeader>
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <EmptyState v-if="collections.length === 0">{{ t('inventory.collections.noCollections') }}</EmptyState>
      <SelectInput v-else v-model="chosen" data-testid="item-collection-choice">
        <option value="">{{ t('inventory.collections.selectCollection') }}</option>
        <option v-for="collection in collections" :key="collection.id" :value="String(collection.id)">
          {{ collection.name }}
        </option>
      </SelectInput>
      <div class="flex justify-end gap-2">
        <SecondaryButton data-cancel @click="show = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="running || !chosen" data-testid="item-collection-submit" @click="submit">
          {{ t('inventory.collections.addToCollection') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
