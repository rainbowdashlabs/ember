/*
*     SPDX-License-Identifier: AGPL-3.0-only
*
*     Copyright (C) RainbowDashLabs and Contributor
*/
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import type {
  ExchangeRequestEntry,
  ExchangeLogEntry,
  ExchangeStatus,
  Inventory,
  InventorySize,
  CreateExchangeRequest,
} from '@/api/types'
import { exchanges, inventory } from '@/api'
import { useSession } from '@/composables/useSession'

const { t } = useI18n()
const { canManageInventory } = useSession()

const requests = ref<ExchangeRequestEntry[]>([])
const inventories = ref<Inventory[]>([])
const loading = ref(true)
const error = ref('')

// Status labels and flow
const statusLabels: Record<ExchangeStatus, string> = {
  ANNOUNCED: 'Angekündigt',
  RECEIVED: 'Erhalten',
  SHIPPED: 'Versendet',
  ARRIVED: 'Angekommen',
  EXCHANGED: 'Getauscht',
}

const statusFlow: ExchangeStatus[] = ['ANNOUNCED', 'RECEIVED', 'SHIPPED', 'ARRIVED', 'EXCHANGED']

function nextStatuses(current: ExchangeStatus): ExchangeStatus[] {
  const idx = statusFlow.indexOf(current)
  if (idx < 0 || idx >= statusFlow.length - 1) return []
  return statusFlow.slice(idx + 1)
}

// Create modal
const showCreateModal = ref(false)
const createInventoryId = ref<string>('')
const createSizeId = ref<string>('')
const createItemId = ref<string>('')
const createReason = ref('')
const createSaving = ref(false)
const availableSizes = ref<InventorySize[]>([])

// Status update
const updatingId = ref<number | null>(null)
const updateTargetStatus = ref<string>('')
const updateNote = ref('')
const updateSaving = ref(false)

// Log modal
const showLogModal = ref(false)
const logEntries = ref<ExchangeLogEntry[]>([])
const logLoading = ref(false)
const logExchangeId = ref<number | null>(null)

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [r, inv] = await Promise.all([
      exchanges.listExchanges(),
      inventory.listInventories(),
    ])
    requests.value = r
    inventories.value = inv
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function onInventorySelected() {
  availableSizes.value = []
  createSizeId.value = ''
  createItemId.value = ''
  const id = Number(createInventoryId.value)
  if (!id) return
  try {
    availableSizes.value = await inventory.listSizes(id)
  } catch {
    // sizes not available
  }
}

function openCreateModal() {
  createInventoryId.value = ''
  createSizeId.value = ''
  createItemId.value = ''
  createReason.value = ''
  availableSizes.value = []
  showCreateModal.value = true
}

async function submitCreate() {
  createSaving.value = true
  error.value = ''
  try {
    const data: CreateExchangeRequest = {
      inventoryId: Number(createInventoryId.value),
      sizeId: createSizeId.value ? Number(createSizeId.value) : undefined,
      itemId: createItemId.value ? Number(createItemId.value) : undefined,
      reason: createReason.value,
    }
    await exchanges.createExchange(data)
    showCreateModal.value = false
    requests.value = await exchanges.listExchanges()
  } catch {
    error.value = t('common.error')
  } finally {
    createSaving.value = false
  }
}

function startStatusUpdate(request: ExchangeRequestEntry) {
  updatingId.value = request.id
  updateTargetStatus.value = ''
  updateNote.value = ''
}

function cancelStatusUpdate() {
  updatingId.value = null
}

async function submitStatusUpdate(id: number) {
  if (!updateTargetStatus.value) return
  updateSaving.value = true
  error.value = ''
  try {
    await exchanges.updateStatus(id, {
      status: updateTargetStatus.value,
      note: updateNote.value || undefined,
    })
    updatingId.value = null
    requests.value = await exchanges.listExchanges()
  } catch {
    error.value = t('common.error')
  } finally {
    updateSaving.value = false
  }
}

async function deleteRequest(id: number) {
  error.value = ''
  try {
    await exchanges.deleteExchange(id)
    requests.value = await exchanges.listExchanges()
  } catch {
    error.value = t('common.error')
  }
}

async function openLog(id: number) {
  logExchangeId.value = id
  logLoading.value = true
  showLogModal.value = true
  logEntries.value = []
  try {
    logEntries.value = await exchanges.getLogs(id)
  } catch {
    error.value = t('common.error')
  } finally {
    logLoading.value = false
  }
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('de-DE', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  })
}

const canCreate = computed(() => !!createInventoryId.value && !!createReason.value.trim())

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <SectionHeader>{{ t('exchanges.title') }}</SectionHeader>
        <PrimaryButton @click="openCreateModal">
          <font-awesome-icon :icon="['fas', 'plus']" class="mr-2" />
          {{ t('exchanges.create') }}
        </PrimaryButton>
      </div>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <div v-if="!loading && requests.length === 0" class="text-center text-(--text-muted) py-8">
        {{ t('exchanges.empty') }}
      </div>

      <div v-if="!loading" class="space-y-3">
        <NeutralContainer v-for="req in requests" :key="req.id" class="space-y-3">
          <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
            <div class="space-y-1">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-medium">{{ req.inventoryName }}</span>
                <span v-if="req.sizeLabel" class="text-sm text-(--text-muted)">({{ req.sizeLabel }})</span>
                <InfoBadge v-if="req.status === 'ANNOUNCED'">{{ statusLabels[req.status] }}</InfoBadge>
                <PrimaryBadge v-else-if="req.status === 'RECEIVED' || req.status === 'ARRIVED'">{{ statusLabels[req.status] }}</PrimaryBadge>
                <SecondaryBadge v-else-if="req.status === 'SHIPPED'">{{ statusLabels[req.status] }}</SecondaryBadge>
                <SuccessBadge v-else-if="req.status === 'EXCHANGED'">{{ statusLabels[req.status] }}</SuccessBadge>
              </div>
              <div class="text-sm text-(--text-muted)">
                <span v-if="canManageInventory()">{{ req.memberName }} &mdash; </span>
                {{ formatDate(req.createdAt) }}
              </div>
              <div v-if="req.reason" class="text-sm">{{ req.reason }}</div>
            </div>

            <div class="flex items-center gap-2 shrink-0">
              <SecondaryButton size="sm" @click="openLog(req.id)">
                <font-awesome-icon :icon="['fas', 'clock-rotate-left']" class="mr-1" />
                {{ t('exchanges.log') }}
              </SecondaryButton>
              <DeleteButton @click="deleteRequest(req.id)" />
            </div>
          </div>

          <!-- Status update (management only) -->
          <div v-if="canManageInventory() && req.status !== 'EXCHANGED'">
            <div v-if="updatingId === req.id" class="flex flex-col sm:flex-row gap-2 items-start sm:items-end">
              <div class="space-y-1 w-full sm:w-auto">
                <label class="block text-xs font-medium text-(--text-muted)">{{ t('exchanges.newStatus') }}</label>
                <SelectInput v-model="updateTargetStatus">
                  <option value="" disabled>{{ t('exchanges.selectStatus') }}</option>
                  <option v-for="s in nextStatuses(req.status)" :key="s" :value="s">{{ statusLabels[s] }}</option>
                </SelectInput>
              </div>
              <div class="space-y-1 w-full sm:flex-1">
                <label class="block text-xs font-medium text-(--text-muted)">{{ t('exchanges.note') }}</label>
                <TextInput v-model="updateNote" :placeholder="t('exchanges.notePlaceholder')" />
              </div>
              <div class="flex gap-2">
                <PrimaryButton :disabled="updateSaving || !updateTargetStatus" @click="submitStatusUpdate(req.id)">
                  {{ updateSaving ? t('common.loading') : t('exchanges.updateStatus') }}
                </PrimaryButton>
                <SecondaryButton @click="cancelStatusUpdate">{{ t('common.cancel') }}</SecondaryButton>
              </div>
            </div>
            <SecondaryButton v-else @click="startStatusUpdate(req)">
              <font-awesome-icon :icon="['fas', 'arrow-right']" class="mr-1" />
              {{ t('exchanges.changeStatus') }}
            </SecondaryButton>
          </div>
        </NeutralContainer>
      </div>

      <!-- Create modal -->
      <Modal v-model="showCreateModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('exchanges.createTitle') }}</SectionHeader>

          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('exchanges.inventory') }}</label>
            <SelectInput v-model="createInventoryId" @change="onInventorySelected">
              <option value="" disabled>{{ t('exchanges.selectInventory') }}</option>
              <option v-for="inv in inventories" :key="inv.id" :value="String(inv.id)">{{ inv.name }}</option>
            </SelectInput>
          </div>

          <div v-if="availableSizes.length > 0" class="space-y-1">
            <label class="block text-sm font-medium">{{ t('exchanges.size') }}</label>
            <SelectInput v-model="createSizeId">
              <option value="">{{ t('exchanges.noSize') }}</option>
              <option v-for="size in availableSizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
            </SelectInput>
          </div>

          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('exchanges.itemId') }}</label>
            <TextInput v-model="createItemId" :placeholder="t('exchanges.itemIdPlaceholder')" />
          </div>

          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('exchanges.reason') }}</label>
            <TextAreaInput v-model="createReason" :placeholder="t('exchanges.reasonPlaceholder')" />
          </div>

          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showCreateModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="createSaving || !canCreate" @click="submitCreate">
              {{ createSaving ? t('common.loading') : t('exchanges.submit') }}
            </PrimaryButton>
          </div>
        </div>
      </Modal>

      <!-- Log modal -->
      <Modal v-model="showLogModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('exchanges.logTitle') }}</SectionHeader>
          <Spinner v-if="logLoading" size="md" />
          <div v-else-if="logEntries.length === 0" class="text-sm text-(--text-muted)">
            {{ t('exchanges.noLogs') }}
          </div>
          <div v-else class="space-y-2">
            <NeutralContainer v-for="entry in logEntries" :key="entry.id" class="text-sm space-y-1">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-medium">{{ statusLabels[entry.oldStatus as ExchangeStatus] ?? entry.oldStatus }}</span>
                <font-awesome-icon :icon="['fas', 'arrow-right']" class="text-(--text-muted)" />
                <span class="font-medium">{{ statusLabels[entry.newStatus as ExchangeStatus] ?? entry.newStatus }}</span>
              </div>
              <div class="text-(--text-muted)">
                {{ entry.changedByName }} &mdash; {{ formatDate(entry.changedAt) }}
              </div>
              <div v-if="entry.note">{{ entry.note }}</div>
            </NeutralContainer>
          </div>
          <div class="flex justify-end">
            <SecondaryButton @click="showLogModal = false">{{ t('common.close') }}</SecondaryButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
