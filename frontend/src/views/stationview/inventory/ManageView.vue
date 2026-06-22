/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'
import {normaliseScannedPayload} from '@/components/scanner/useBarcodeScanner'
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
import { InventoryTypes } from '@/api/types'
import { inventory } from '@/api'
import type { InventorySummary } from '@/api/inventory'
import MutedText from '@/components/typography/MutedText.vue'

const { t } = useI18n()
const router = useRouter()

const summaries = ref<InventorySummary[]>([])
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
const deleteTarget = ref<InventorySummary | null>(null)

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    summaries.value = await inventory.listSummaries()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function viewDetail(inv: InventorySummary) {
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

function requestDelete(inv: InventorySummary) {
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

function editInventory(inv: InventorySummary) {
  router.push({ name: 'inventory-edit', params: { id: inv.id } })
}

interface ScanInputRef {
  $el: HTMLInputElement
}

const scanInputRef = ref<ScanInputRef | null>(null)
const scanInput = ref('')
const scanError = ref('')

async function onScanSubmit() {
  const query = normaliseScannedPayload(scanInput.value)
  if (!query) return
  scanError.value = ''
  const item = await inventory.findByInternalId(query)
  if (item) {
    scanInput.value = ''
    router.push({ name: 'inventory-item-detail', params: { id: item.id } })
  } else {
    scanError.value = t('inventory.manage.scanNotFound')
  }
}

function onScanDecoded(value: string) {
  scanInput.value = value
  onScanSubmit()
}

onMounted(async () => {
  await loadData()
  nextTick(() => {
    const el = scanInputRef.value?.$el
    if (el instanceof HTMLInputElement) el.focus()
  })
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <div class="flex items-center justify-between">
          <SectionHeader>{{ t('inventory.manage.title') }}</SectionHeader>
          <PrimaryButton :icon="['fas', 'plus']" @click="openCreate">
            {{ t('inventory.manage.create') }}
          </PrimaryButton>
        </div>

        <!-- Barcode / Internal ID scanner -->
        <NeutralContainer class="space-y-2">
          <FieldLabel>{{ t('inventory.manage.scanLabel') }}</FieldLabel>
          <form class="flex items-center gap-2" @submit.prevent="onScanSubmit">
            <TextInput ref="scanInputRef" v-model="scanInput" :placeholder="t('inventory.manage.scanPlaceholder')" class="flex-1" />
            <ScanButton @decoded="onScanDecoded"/>
            <PrimaryButton :icon="['fas', 'magnifying-glass']" type="submit">
              {{ t('inventory.manage.scanSubmit') }}
            </PrimaryButton>
          </form>
          <Alert v-if="scanError" variant="error" class="text-sm">{{ scanError }}</Alert>
        </NeutralContainer>

        <EmptyState v-if="summaries.length === 0">{{ t('inventory.manage.empty') }}</EmptyState>

        <div class="space-y-3">
          <NeutralContainer clickable v-for="inv in summaries" :key="inv.id" @click="viewDetail(inv)">
            <div class="flex items-center justify-between">
              <div>
                <span class="font-medium">{{ inv.name }}</span>
                <MutedText class="ml-2">{{ t('inventory.manage.type.' + (inv.inventoryType ?? InventoryTypes.INTERNAL)) }}</MutedText>
                <span v-if="inv.hasSizes" class="ml-2 text-xs text-secondary-accent dark:text-secondary">{{ t('inventory.manage.withSizes') }}</span>
              </div>
              <div class="flex items-center gap-2" @click.stop>
                <EditButton @click="editInventory(inv)" />
                <DeleteButton @click="requestDelete(inv)" />
              </div>
            </div>
            <MutedText tag="div" class="mt-1">
              {{ t('inventory.manage.itemCount', { count: inv.itemCount }) }}
              <template v-if="inv.lostCount > 0">
                &middot; <span class="text-error">{{ t('inventory.manage.lostCount', { count: inv.lostCount }) }}</span>
              </template>
              <template v-if="inv.lentOutCount > 0">
                &middot; <span class="text-secondary-accent dark:text-secondary">{{ t('inventory.manage.lentOutCount', { count: inv.lentOutCount }) }}</span>
              </template>
              <template v-if="inv.procurementCount > 0">
                &middot; <span class="text-info-accent dark:text-info">{{ t('inventory.manage.procurementCount', { count: inv.procurementCount }) }}</span>
              </template>
            </MutedText>
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
