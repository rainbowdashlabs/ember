/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import {inventory} from '@/api'
import type {Inventory, InventoryItem} from '@/api/types'
import {InventoryTypes, ItemSource} from '@/api/types'

const props = defineProps<{
  scannedCode: string
  context: 'container' | 'member'
}>()

const emit = defineEmits<{
  created: [item: InventoryItem]
  close: []
}>()

const {t} = useI18n()

const open = ref(true)
const inventories = ref<Inventory[]>([])
const loading = ref(true)
const error = ref('')
const submitting = ref(false)

const targetInventoryId = ref<number | 'new'>('new')
const newInventoryName = ref('')
const newInventoryType = ref<'INTERNAL' | 'EXTERNAL' | 'MIXED'>(InventoryTypes.INTERNAL)
const itemName = ref('')
const itemSource = ref<'INTERNAL' | 'EXTERNAL'>(ItemSource.INTERNAL)

const sortedInventories = computed(() => [...inventories.value].sort((a, b) =>
    (a.name ?? '').localeCompare(b.name ?? '')))

const selectedInventory = computed(() =>
    typeof targetInventoryId.value === 'number'
        ? inventories.value.find(i => i.id === targetInventoryId.value)
        : null)

const showSourcePicker = computed(() => selectedInventory.value?.inventoryType === InventoryTypes.MIXED)

async function load() {
  loading.value = true
  error.value = ''
  try {
    inventories.value = await inventory.listInventories()
    if (inventories.value.length > 0) {
      targetInventoryId.value = inventories.value[0].id
    }
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('inventory.unknownScan.errors.loadFailed')
  } finally {
    loading.value = false
  }
}

watch(selectedInventory, (inv) => {
  if (!inv) return
  if (inv.inventoryType === InventoryTypes.EXTERNAL) itemSource.value = ItemSource.EXTERNAL
  else if (inv.inventoryType === InventoryTypes.INTERNAL) itemSource.value = ItemSource.INTERNAL
})

async function submit() {
  if (!itemName.value.trim()) {
    error.value = t('inventory.unknownScan.errors.nameRequired')
    return
  }
  submitting.value = true
  error.value = ''
  try {
    let inventoryId: number
    if (targetInventoryId.value === 'new') {
      if (!newInventoryName.value.trim()) {
        error.value = t('inventory.unknownScan.errors.inventoryNameRequired')
        return
      }
      const created = await inventory.createInventory({
        name: newInventoryName.value.trim(),
        inventoryType: newInventoryType.value,
        hasSizes: false,
      })
      inventoryId = created.id
    } else {
      inventoryId = targetInventoryId.value
    }
    const item = await inventory.createItem(inventoryId, {
      internalId: props.scannedCode,
      name: itemName.value.trim(),
      itemSource: itemSource.value,
    })
    emit('created', item)
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('inventory.unknownScan.errors.createFailed')
  } finally {
    submitting.value = false
  }
}

function onClose() {
  open.value = false
  emit('close')
}

onMounted(load)
</script>

<template>
  <Modal v-model="open" size="md" @update:modelValue="(v) => { if (!v) onClose() }">
    <SubHeader class="mb-2">{{ t('inventory.unknownScan.title') }}</SubHeader>
    <p class="text-sm text-(--text-muted) mb-3">
      {{ context === 'member' ? t('inventory.unknownScan.introMember') : t('inventory.unknownScan.introContainer') }}
    </p>
    <p class="text-sm mb-3">
      <span class="text-(--text-muted)">{{ t('inventory.unknownScan.code') }}:</span>
      <code class="ml-2 font-mono">{{ scannedCode }}</code>
    </p>

    <Alert v-if="error" variant="error" class="mb-3">{{ error }}</Alert>

    <div v-if="loading" class="flex justify-center py-6">
      <Spinner size="md" />
    </div>
    <div v-else class="flex flex-col gap-3">
      <div class="space-y-1">
        <FieldLabel>{{ t('inventory.unknownScan.targetInventory') }}</FieldLabel>
        <SelectInput v-model="targetInventoryId">
          <option v-for="inv in sortedInventories" :key="inv.id" :value="inv.id">{{ inv.name }}</option>
          <option value="new">{{ t('inventory.unknownScan.createNewInventory') }}</option>
        </SelectInput>
      </div>

      <template v-if="targetInventoryId === 'new'">
        <div class="space-y-1">
          <FieldLabel>{{ t('inventory.unknownScan.newInventoryName') }}</FieldLabel>
          <TextInput v-model="newInventoryName" :placeholder="t('inventory.unknownScan.newInventoryPlaceholder')" />
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('inventory.unknownScan.newInventoryType') }}</FieldLabel>
          <SelectInput v-model="newInventoryType">
            <option :value="InventoryTypes.INTERNAL">{{ t('inventory.unknownScan.types.INTERNAL') }}</option>
            <option :value="InventoryTypes.EXTERNAL">{{ t('inventory.unknownScan.types.EXTERNAL') }}</option>
            <option :value="InventoryTypes.MIXED">{{ t('inventory.unknownScan.types.MIXED') }}</option>
          </SelectInput>
        </div>
      </template>

      <div class="space-y-1">
        <FieldLabel>{{ t('inventory.unknownScan.itemName') }}</FieldLabel>
        <TextInput v-model="itemName" :placeholder="t('inventory.unknownScan.itemNamePlaceholder')" />
      </div>

      <div v-if="showSourcePicker" class="space-y-1">
        <FieldLabel>{{ t('inventory.unknownScan.itemSource') }}</FieldLabel>
        <SelectInput v-model="itemSource">
          <option :value="ItemSource.INTERNAL">{{ t('inventory.unknownScan.sources.INTERNAL') }}</option>
          <option :value="ItemSource.EXTERNAL">{{ t('inventory.unknownScan.sources.EXTERNAL') }}</option>
        </SelectInput>
      </div>
    </div>

    <div class="flex justify-end gap-2 mt-4">
      <SecondaryButton @click="onClose">{{ t('common.cancel') }}</SecondaryButton>
      <PrimaryButton :disabled="submitting || loading" @click="submit">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-2" />
        {{ submitting ? t('common.saving') : t('inventory.unknownScan.create') }}
      </PrimaryButton>
    </div>
  </Modal>
</template>
