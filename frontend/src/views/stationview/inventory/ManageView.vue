/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Modal from '@/components/feedback/Modal.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type { Inventory, InventoryItem, ProcurementEntry } from '@/api/types'
import { InventoryTypes } from '@/api/types'
import { inventory, procurement } from '@/api'

const { t } = useI18n()
const router = useRouter()

const inventories = ref<Inventory[]>([])
const itemsByInventory = ref<Map<number, InventoryItem[]>>(new Map())
const openProcurement = ref<ProcurementEntry[]>([])
const loading = ref(true)
const error = ref('')

// Create modal
const showCreateModal = ref(false)
const createStep = ref<'basic' | 'sizes'>('basic')
const createName = ref('')
const createType = ref(InventoryTypes.INTERNAL)
const createHasSizes = ref(false)
const createSizes = ref<string[]>([])
const newSizeLabel = ref('')
const saving = ref(false)

// Delete
const showDeleteModal = ref(false)
const deleteTarget = ref<Inventory | null>(null)

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [inv, proc] = await Promise.all([
      inventory.listInventories(),
      procurement.listOpen(),
    ])
    inventories.value = inv
    openProcurement.value = proc
    const map = new Map<number, InventoryItem[]>()
    for (const i of inv) {
      map.set(i.id, await inventory.listItems(i.id))
    }
    itemsByInventory.value = map
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function itemCount(invId: number): number {
  return itemsByInventory.value.get(invId)?.length ?? 0
}

function lostCount(invId: number): number {
  return itemsByInventory.value.get(invId)?.filter(i => i.lostAt)?.length ?? 0
}

function procurementCount(invId: number): number {
  return openProcurement.value.filter(p => p.inventoryId === invId).length
}

function viewDetail(inv: Inventory) {
  router.push({ name: 'inventory-detail', params: { id: inv.id } })
}

function openCreate() {
  createStep.value = 'basic'
  createName.value = ''
  createType.value = InventoryTypes.INTERNAL
  createHasSizes.value = false
  createSizes.value = []
  newSizeLabel.value = ''
  showCreateModal.value = true
}

function nextStep() {
  if (createHasSizes.value) {
    createStep.value = 'sizes'
  } else {
    submitCreate()
  }
}

function addSize() {
  if (!newSizeLabel.value.trim()) return
  createSizes.value = [...createSizes.value, newSizeLabel.value.trim()]
  newSizeLabel.value = ''
}

function removeSize(index: number) {
  createSizes.value = createSizes.value.filter((_, i) => i !== index)
}

async function submitCreate() {
  saving.value = true
  error.value = ''
  try {
    const inv = await inventory.createInventory({
      name: createName.value,
      inventoryType: createType.value,
      hasSizes: createHasSizes.value,
    })
    if (createHasSizes.value && createSizes.value.length > 0) {
      for (let i = 0; i < createSizes.value.length; i++) {
        await inventory.createSize(inv.id, { label: createSizes.value[i], position: i })
      }
    }
    showCreateModal.value = false
    await loadData()
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}

function requestDelete(inv: Inventory) {
  deleteTarget.value = inv
  showDeleteModal.value = true
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  try {
    await inventory.deleteInventory(deleteTarget.value.id)
    showDeleteModal.value = false
    deleteTarget.value = null
    await loadData()
  } catch {
    error.value = t('common.error')
  }
}

function editInventory(inv: Inventory) {
  router.push({ name: 'inventory-edit', params: { id: inv.id } })
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <div class="flex items-center justify-between">
          <SectionHeader>{{ t('inventory.manage.title') }}</SectionHeader>
          <PrimaryButton @click="openCreate">
            <font-awesome-icon :icon="['fas', 'plus']" class="mr-2" />
            {{ t('inventory.manage.create') }}
          </PrimaryButton>
        </div>

        <EmptyState v-if="inventories.length === 0">{{ t('inventory.manage.empty') }}</EmptyState>

        <div class="space-y-3">
          <NeutralContainer v-for="inv in inventories" :key="inv.id" class="cursor-pointer hover:ring-1 hover:ring-primary/30 transition-all" @click="viewDetail(inv)">
            <div class="flex items-center justify-between">
              <div>
                <span class="font-medium">{{ inv.name }}</span>
                <span class="ml-2 text-xs text-(--text-muted)">{{ t('inventory.manage.type.' + (inv.inventoryType ?? InventoryTypes.INTERNAL)) }}</span>
                <span v-if="inv.hasSizes" class="ml-2 text-xs text-secondary-accent dark:text-secondary">{{ t('inventory.manage.withSizes') }}</span>
              </div>
              <div class="flex items-center gap-2" @click.stop>
                <EditButton @click="editInventory(inv)" />
                <DeleteButton @click="requestDelete(inv)" />
              </div>
            </div>
            <div class="text-xs text-(--text-muted) mt-1">
              {{ t('inventory.manage.itemCount', { count: itemCount(inv.id) }) }}
              <template v-if="lostCount(inv.id) > 0">
                &middot; <span class="text-error">{{ t('inventory.manage.lostCount', { count: lostCount(inv.id) }) }}</span>
              </template>
              <template v-if="procurementCount(inv.id) > 0">
                &middot; <span class="text-info-accent dark:text-info">{{ t('inventory.manage.procurementCount', { count: procurementCount(inv.id) }) }}</span>
              </template>
            </div>
          </NeutralContainer>
        </div>
      </template>

      <!-- Create modal -->
      <Modal v-model="showCreateModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('inventory.manage.create') }}</SectionHeader>

          <template v-if="createStep === 'basic'">
            <div class="space-y-1">
              <FieldLabel>{{ t('inventory.manage.name') }}</FieldLabel>
              <TextInput v-model="createName" :placeholder="t('inventory.manage.namePlaceholder')" />
            </div>

            <div class="space-y-1">
              <FieldLabel>{{ t('inventory.manage.typeLabel') }}</FieldLabel>
              <SelectInput v-model="createType">
                <option :value="InventoryTypes.INTERNAL">{{ t('inventory.manage.type.INTERNAL') }}</option>
                <option :value="InventoryTypes.EXTERNAL">{{ t('inventory.manage.type.EXTERNAL') }}</option>
                <option :value="InventoryTypes.MIXED">{{ t('inventory.manage.type.MIXED') }}</option>
              </SelectInput>
              <p class="text-xs text-(--text-muted)">{{ t('inventory.manage.typeHint') }}</p>
            </div>

            <div class="flex items-center justify-between">
              <div>
                <label class="text-sm font-medium">{{ t('inventory.manage.hasSizes') }}</label>
                <p class="text-xs text-(--text-muted)">{{ t('inventory.manage.hasSizesHint') }}</p>
              </div>
              <ToggleInput v-model="createHasSizes" />
            </div>

            <div class="flex justify-end gap-3">
              <SecondaryButton @click="showCreateModal = false">{{ t('common.cancel') }}</SecondaryButton>
              <PrimaryButton :disabled="!createName.trim()" @click="nextStep">
                {{ createHasSizes ? t('inventory.manage.next') : t('common.save') }}
              </PrimaryButton>
            </div>
          </template>

          <template v-if="createStep === 'sizes'">
            <p class="text-sm text-(--text-muted)">{{ t('inventory.manage.sizesHint') }}</p>

            <div class="flex items-center gap-2">
              <TextInput v-model="newSizeLabel" :placeholder="t('inventory.manage.sizeLabel')" class="flex-1" @keyup.enter="addSize" />
              <SecondaryButton :disabled="!newSizeLabel.trim()" @click="addSize">
                <font-awesome-icon :icon="['fas', 'plus']" />
              </SecondaryButton>
            </div>

            <div v-if="createSizes.length > 0" class="space-y-1">
              <div v-for="(size, idx) in createSizes" :key="idx" class="flex items-center justify-between rounded-lg px-3 py-2 border border-bg-light-accent dark:border-bg-dark-accent">
                <span class="text-sm">{{ size }}</span>
                <IconButton :icon="['fas', 'xmark']" :label="t('common.delete')" class="text-(--text-muted) hover:text-error text-sm" @click="removeSize(idx)"/>
              </div>
            </div>

            <div class="flex justify-between gap-3">
              <SecondaryButton @click="createStep = 'basic'">{{ t('inventory.manage.back') }}</SecondaryButton>
              <PrimaryButton :disabled="saving || createSizes.length === 0" @click="submitCreate">
                {{ saving ? t('common.loading') : t('common.save') }}
              </PrimaryButton>
            </div>
          </template>
        </div>
      </Modal>

      <ConfirmDeleteModal
        v-model="showDeleteModal"
        :message="t('inventory.manage.deleteConfirm', { name: deleteTarget?.name })"
        @confirm="confirmDelete"
      />
    </div>
  </ViewContent>
</template>
