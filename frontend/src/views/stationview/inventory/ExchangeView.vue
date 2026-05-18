/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
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
  ExchangeStatusName,
  Inventory,
  InventoryItem,
  InventorySize,
  CreateExchangeRequest,
} from '@/api/types'
import type { ProfileField, StationMember } from '@/api/types'
import { InventoryTypes, ExchangeStatus } from '@/api/types'
import { exchanges, inventory, procurement, stationMembers, profileFields, managedMembers } from '@/api'
import { useSession } from '@/composables/useSession'
import { useStations } from '@/composables/useStations'

const { t } = useI18n()
const router = useRouter()
const { canManageInventory, isMemberManager, sessionInfo } = useSession()
const { activeStation } = useStations()

import type { ManagedMember } from '@/api/managedMembers'

const requests = ref<ExchangeRequestEntry[]>([])
const inventories = ref<Inventory[]>([])
const members = ref<StationMember[]>([])
const managed = ref<ManagedMember[]>([])
const membersWithItems = ref<Set<number>>(new Set())
const loading = ref(true)
const error = ref('')
const exportMode = ref(false)
const selectedForExport = ref<Set<number>>(new Set())
const selectedExportFields = ref<Set<number>>(new Set())
const allFields = ref<ProfileField[]>([])
const exporting = ref(false)

const showMemberColumn = computed(() => canManageInventory() || isMemberManager())

const membersWithItemsList = computed(() =>
  members.value.filter(m => membersWithItems.value.has(m.id))
)
const managedWithItemsList = computed(() =>
  managed.value.filter(m => membersWithItems.value.has(m.id))
)

// Status labels and flow
function statusLabel(status: ExchangeStatusName): string {
  return t(`exchanges.status.${status}`)
}

const internalFlow: ExchangeStatusName[] = [ExchangeStatus.ANNOUNCED, ExchangeStatus.RECEIVED, ExchangeStatus.EXCHANGED]
const externalFlow: ExchangeStatusName[] = [ExchangeStatus.ANNOUNCED, ExchangeStatus.RECEIVED, ExchangeStatus.SHIPPED, ExchangeStatus.ARRIVED, ExchangeStatus.EXCHANGED]

function getFlow(inventoryType: string): ExchangeStatusName[] {
  return inventoryType === InventoryTypes.INTERNAL ? internalFlow : externalFlow
}

function nextStatuses(current: ExchangeStatusName, inventoryType: string): ExchangeStatusName[] {
  const flow = getFlow(inventoryType)
  const idx = flow.indexOf(current)
  if (idx < 0 || idx >= flow.length - 1) return []
  return flow.slice(idx + 1)
}

// Create modal - step wizard
const showCreateModal = ref(false)
const createStep = ref(1)
const createMemberId = ref<string>('')
const createItemId = ref<string>('')
const createNewSizeId = ref<string>('')
const createReason = ref('')
const createSaving = ref(false)
const createMemberItems = ref<{ id: number, inventoryId: number, name: string, internalId: string, sizeId: number | null, sizeName: string | null, inventoryName: string }[]>([])
const createItemSizes = ref<InventorySize[]>([])
const createLoadingItems = ref(false)

const selectedCreateItem = computed(() => {
  const id = Number(createItemId.value)
  return createMemberItems.value.find(i => i.id === id) ?? null
})

const needsSizeStep = computed(() => createItemSizes.value.length > 0)

// Total steps: 1=member, 2=item, 3=size(conditional), 4=reason
const totalCreateSteps = computed(() => needsSizeStep.value ? 4 : 3)
const isReasonStep = computed(() => createStep.value === totalCreateSteps.value)

// Status update
const updatingId = ref<number | null>(null)
const updateTargetStatus = ref<string>('')
const updateNote = ref('')
const updateSaving = ref(false)
const updateExchangedItemId = ref<string>('')
const availableItems = ref<InventoryItem[]>([])
const createNewItemForExchange = ref(false)
const newItemName = ref('')
const procurementCreatedForExchange = ref(false)

// Log modal
const showLogModal = ref(false)
const logEntries = ref<ExchangeLogEntry[]>([])
const logLoading = ref(false)
const logExchangeId = ref<number | null>(null)

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const stationId = activeStation.value?.stationId
    const [r, inv, m, mgd] = await Promise.all([
      exchanges.listExchanges(),
      canManageInventory() ? inventory.listInventories() : Promise.resolve([]),
      stationId && canManageInventory() ? stationMembers.listMembers(stationId) : Promise.resolve([]),
      isMemberManager() ? managedMembers.listManaged() : Promise.resolve([]),
    ])
    requests.value = r
    inventories.value = inv
    members.value = m
    managed.value = mgd
    // Build set of members who have assigned items
    const withItems = new Set<number>()
    if (inv.length > 0) {
      const itemArrays = await Promise.all(inv.map(i => inventory.listItems(i.id)))
      for (const items of itemArrays) {
        for (const item of items) {
          if (item.assignedTo && !item.lostAt) withItems.add(item.assignedTo)
        }
      }
    } else if (mgd.length > 0) {
      // Member manager without inventory management: check managed members' items
      const selfId = sessionInfo.value?.member?.id
      const idsToCheck = selfId ? [selfId, ...mgd.map(m => m.id)] : mgd.map(m => m.id)
      const results = await Promise.all(idsToCheck.map(async id => {
        try {
          const items = id === selfId ? await inventory.myItems() : await managedMembers.getMemberInventory(id)
          return items.some(i => !i.lostAt) ? id : null
        } catch { return null }
      }))
      for (const id of results) { if (id) withItems.add(id) }
    }
    membersWithItems.value = withItems
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function openCreateModal() {
  createStep.value = 1
  createMemberId.value = ''
  createItemId.value = ''
  createNewSizeId.value = ''
  createReason.value = ''
  createMemberItems.value = []
  createItemSizes.value = []
  showCreateModal.value = true
  // For non-managers, skip member selection and load own items
  if (!canManageInventory() && !(isMemberManager() && managed.value.length > 0)) {
    createMemberId.value = String(sessionInfo.value?.member?.id ?? '')
    createStep.value = 2
    loadCreateMemberItems()
  }
}

async function loadCreateMemberItems() {
  createLoadingItems.value = true
  createMemberItems.value = []
  createItemId.value = ''
  createNewSizeId.value = ''
  createItemSizes.value = []
  const mid = Number(createMemberId.value)
  if (!mid) { createLoadingItems.value = false; return }
  try {
    const isOwnMember = mid === sessionInfo.value?.member?.id
    let items
    if (isOwnMember) {
      items = await inventory.myItems()
    } else if (canManageInventory()) {
      items = await inventory.memberItems(mid)
    } else {
      items = await managedMembers.getMemberInventory(mid)
    }
    const activeExchangeItemIds = new Set(
      requests.value
        .filter(r => r.status !== ExchangeStatus.EXCHANGED && r.itemId)
        .map(r => r.itemId!)
    )
    createMemberItems.value = items
        .filter(i => !i.lostAt && !activeExchangeItemIds.has(i.id))
        .map(i => ({ id: i.id, inventoryId: i.inventoryId, name: i.name ?? '', internalId: i.internalId ?? '', sizeId: i.sizeId ?? null, sizeName: i.sizeName ?? null, inventoryName: i.inventoryName }))
  } catch { /* ignore */ }
  createLoadingItems.value = false
}

async function onCreateItemSelected() {
  createItemSizes.value = []
  createNewSizeId.value = ''
  const item = selectedCreateItem.value
  if (!item) return
  try {
    createItemSizes.value = await inventory.listSizes(item.inventoryId)
  } catch { /* ignore */ }
}

function createStepNext() {
  if (createStep.value === 1) {
    createStep.value = 2
    loadCreateMemberItems()
  } else if (createStep.value === 2) {
    onCreateItemSelected()
    createStep.value = needsSizeStep.value ? 3 : totalCreateSteps.value
  } else if (createStep.value === 3 && needsSizeStep.value) {
    createStep.value = 4
  }
}

// Re-evaluate size step after loading sizes (async)
async function createStepNextFromItem() {
  await onCreateItemSelected()
  // After loading sizes, go to the right step
  createStep.value = needsSizeStep.value ? 3 : totalCreateSteps.value
}

function createStepBack() {
  if (createStep.value > 1) {
    createStep.value = createStep.value - 1
  }
}

async function submitCreate() {
  createSaving.value = true
  error.value = ''
  try {
    const item = selectedCreateItem.value
    const data: CreateExchangeRequest = {
      memberId: Number(createMemberId.value) || undefined,
      inventoryId: item?.inventoryId ?? 0,
      itemId: item?.id ?? undefined,
      oldSizeId: item?.sizeId ?? undefined,
      newSizeId: createNewSizeId.value ? Number(createNewSizeId.value) : undefined,
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

async function startStatusUpdate(request: ExchangeRequestEntry) {
  updatingId.value = request.id
  updateTargetStatus.value = ''
  updateNote.value = ''
  updateExchangedItemId.value = ''
  procurementCreatedForExchange.value = false
  createNewItemForExchange.value = false
  newItemName.value = request.inventoryName
  // Load available unassigned items for this inventory
  try {
    const allItems = await inventory.listItems(request.inventoryId)
    availableItems.value = allItems.filter(i => !i.assignedTo && !i.lostAt)
  } catch { availableItems.value = [] }
}

function cancelStatusUpdate() {
  updatingId.value = null
}

async function submitStatusUpdate(id: number) {
  if (!updateTargetStatus.value) return
  updateSaving.value = true
  error.value = ''
  try {
    let exchangedItemId: number | undefined = updateExchangedItemId.value ? Number(updateExchangedItemId.value) : undefined
    // Create new item if requested (for external/mixed inventories)
    if (createNewItemForExchange.value && newItemName.value.trim()) {
      const req = requests.value.find(r => r.id === id)
      if (req) {
        const newItem = await inventory.createItem(req.inventoryId, {
          name: newItemName.value.trim(),
          internalId: '',
          sizeId: req.newSizeId ?? req.oldSizeId ?? undefined,
        })
        exchangedItemId = newItem.id
      }
    }
    await exchanges.updateStatus(id, {
      status: updateTargetStatus.value,
      note: updateNote.value || undefined,
      exchangedItemId,
    })
    updatingId.value = null
    createNewItemForExchange.value = false
    newItemName.value = ''
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

async function createProcurementFromExchange(req: ExchangeRequestEntry) {
  try {
    procurementCreatedForExchange.value = false
    await procurement.createProcurement({
      inventoryId: req.inventoryId,
      memberId: req.memberId,
      sizeId: req.newSizeId ?? req.oldSizeId ?? undefined,
      notes: t('exchanges.procurementFromExchange', { reason: req.reason }),
    })
    procurementCreatedForExchange.value = true
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

const canCreate = computed(() => {
  if (!createItemId.value || !createReason.value.trim()) return false
  if (needsSizeStep.value && !createNewSizeId.value) return false
  return true
})

async function enterExportMode() {
  exportMode.value = true
  selectedForExport.value = new Set(requests.value.map(r => r.id))
  selectedExportFields.value = new Set()
  try {
    allFields.value = await profileFields.listFields()
  } catch { allFields.value = [] }
}

function cancelExport() {
  exportMode.value = false
  selectedForExport.value = new Set()
  selectedExportFields.value = new Set()
}

function toggleExportField(fieldId: number) {
  const s = new Set(selectedExportFields.value)
  if (s.has(fieldId)) s.delete(fieldId)
  else s.add(fieldId)
  selectedExportFields.value = s
}

function toggleExportSelection(id: number) {
  const newSet = new Set(selectedForExport.value)
  if (newSet.has(id)) newSet.delete(id)
  else newSet.add(id)
  selectedForExport.value = newSet
}

function toggleSelectAll() {
  if (selectedForExport.value.size === requests.value.length) {
    selectedForExport.value = new Set()
  } else {
    selectedForExport.value = new Set(requests.value.map(r => r.id))
  }
}

async function exportSelected() {
  exporting.value = true
  try {
    const blob = await exchanges.exportPdf([...selectedForExport.value], [...selectedExportFields.value])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'exchange-requests.pdf'
    a.click()
    URL.revokeObjectURL(url)
    exportMode.value = false
    selectedForExport.value = new Set()
  } catch {
    error.value = t('common.error')
  } finally {
    exporting.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex items-center justify-between flex-wrap gap-2">
        <SectionHeader>{{ t('exchanges.title') }}</SectionHeader>
        <div class="flex items-center gap-2">
          <template v-if="exportMode">
            <SecondaryButton :disabled="exporting || selectedForExport.size === 0" @click="exportSelected">
              <font-awesome-icon :icon="['fas', 'download']" class="mr-1" />
              {{ exporting ? t('common.loading') : t('exchanges.download') }} ({{ selectedForExport.size }})
            </SecondaryButton>
            <SecondaryButton @click="cancelExport">
              {{ t('common.cancel') }}
            </SecondaryButton>
          </template>
          <template v-else>
            <SecondaryButton v-if="canManageInventory() && requests.length > 0" @click="enterExportMode">
              <font-awesome-icon :icon="['fas', 'file-export']" class="mr-1" />
              {{ t('exchanges.export') }}
            </SecondaryButton>
            <PrimaryButton @click="openCreateModal">
              <font-awesome-icon :icon="['fas', 'plus']" class="mr-2" />
              {{ t('exchanges.create') }}
            </PrimaryButton>
          </template>
        </div>
      </div>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <!-- Export field picker -->
      <NeutralContainer v-if="exportMode && allFields.length > 0" class="space-y-2">
        <p class="text-sm font-medium">{{ t('exchanges.exportFieldsHint') }}</p>
        <div class="flex flex-wrap gap-2">
          <label v-for="field in allFields" :key="field.id" class="inline-flex items-center gap-1.5 text-sm cursor-pointer">
            <input type="checkbox" :checked="selectedExportFields.has(field.id)" @change="toggleExportField(field.id)" />
            {{ field.name }}
          </label>
        </div>
      </NeutralContainer>

      <div v-if="!loading && requests.length === 0" class="text-center text-(--text-muted) py-8">
        {{ t('exchanges.empty') }}
      </div>

      <!-- Exchange requests table -->
      <NeutralContainer v-if="!loading && requests.length > 0" class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
              <th v-if="exportMode" class="px-1 py-2 w-8">
                <input type="checkbox" :checked="selectedForExport.size === requests.length && requests.length > 0" @change="toggleSelectAll" />
              </th>
              <th v-if="showMemberColumn" class="px-3 py-2 font-medium">{{ t('exchanges.colMember') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('exchanges.colInventory') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('exchanges.colOldSize') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('exchanges.colNewSize') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('exchanges.colStatus') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('exchanges.colReason') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('exchanges.colDate') }}</th>
              <th class="px-3 py-2"></th>
            </tr>
          </thead>
          <tbody>
            <template v-for="req in requests" :key="req.id">
              <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
                <td v-if="exportMode" class="px-1 py-2.5 w-8">
                  <input type="checkbox" :checked="selectedForExport.has(req.id)" @change="toggleExportSelection(req.id)" />
                </td>
                <td v-if="showMemberColumn" class="px-3 py-2.5">
                  <button
                      v-if="canManageInventory()"
                      class="text-primary hover:underline cursor-pointer"
                      @click="router.push({ name: 'inventory-member', params: { memberId: req.memberId } })"
                  >{{ req.memberName }}</button>
                  <span v-else>{{ req.memberName }}</span>
                </td>
                <td class="px-3 py-2.5 font-medium">{{ req.inventoryName }}</td>
                <td class="px-3 py-2.5">{{ req.oldSizeLabel ?? t('common.unisize') }}</td>
                <td class="px-3 py-2.5">{{ req.newSizeLabel ?? t('common.unisize') }}</td>
                <td class="px-3 py-2.5">
                  <InfoBadge v-if="req.status === ExchangeStatus.ANNOUNCED">{{ statusLabel(req.status) }}</InfoBadge>
                  <PrimaryBadge v-else-if="req.status === ExchangeStatus.RECEIVED || req.status === ExchangeStatus.ARRIVED">{{ statusLabel(req.status) }}</PrimaryBadge>
                  <SecondaryBadge v-else-if="req.status === ExchangeStatus.SHIPPED">{{ statusLabel(req.status) }}</SecondaryBadge>
                  <SuccessBadge v-else-if="req.status === ExchangeStatus.EXCHANGED">{{ statusLabel(req.status) }}</SuccessBadge>
                </td>
                <td class="px-3 py-2.5 text-(--text-muted) max-w-48 truncate">{{ req.reason }}</td>
                <td class="px-3 py-2.5 text-(--text-muted)">
                  {{ formatDate(req.createdAt) }}
                  <span v-if="req.createdByName" class="block text-xs italic">{{ t('common.createdBy', { name: req.createdByName }) }}</span>
                </td>
                <td class="px-3 py-2.5 text-right">
                  <div class="flex items-center justify-end gap-1">
                    <SecondaryButton class="text-xs" @click="openLog(req.id)">
                      <font-awesome-icon :icon="['fas', 'clock-rotate-left']" />
                    </SecondaryButton>
                    <SecondaryButton v-if="canManageInventory() && req.status !== ExchangeStatus.EXCHANGED" class="text-xs" @click="startStatusUpdate(req)">
                      <font-awesome-icon :icon="['fas', 'arrow-right']" />
                    </SecondaryButton>
                    <DeleteButton v-if="canManageInventory()" @click="deleteRequest(req.id)" />
                  </div>
                </td>
              </tr>
              <!-- Inline status update row -->
              <tr v-if="updatingId === req.id" class="bg-(--bg-accent)/30">
                <td :colspan="(showMemberColumn ? 8 : 7) + (exportMode ? 1 : 0)" class="px-3 py-3">
                  <div class="flex flex-col gap-2 items-stretch">
                    <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
                    <div class="space-y-1">
                      <label class="block text-xs font-medium text-(--text-muted)">{{ t('exchanges.newStatus') }}</label>
                      <SelectInput v-model="updateTargetStatus">
                        <option value="" disabled>{{ t('exchanges.selectStatus') }}</option>
                        <option v-for="s in nextStatuses(req.status, req.inventoryType)" :key="s" :value="s">{{ statusLabel(s) }}</option>
                      </SelectInput>
                    </div>
                    <div v-if="updateTargetStatus === ExchangeStatus.EXCHANGED" class="space-y-1 sm:col-span-2">
                      <label class="block text-xs font-medium text-(--text-muted)">{{ t('exchanges.exchangedItem') }}</label>
                      <template v-if="!createNewItemForExchange">
                        <SelectInput v-model="updateExchangedItemId">
                          <option value="">{{ t('exchanges.noItem') }}</option>
                          <option v-for="item in availableItems" :key="item.id" :value="String(item.id)">
                            {{ item.name }} {{ item.internalId ? `(${item.internalId})` : '' }}
                          </option>
                        </SelectInput>
                        <div class="flex gap-2 mt-1">
                          <SecondaryButton v-if="req.inventoryType !== 'internal'" class="text-xs" @click="createNewItemForExchange = true">
                            <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
                            {{ t('exchanges.createNewItem') }}
                          </SecondaryButton>
                          <template v-if="availableItems.length === 0">
                            <span v-if="procurementCreatedForExchange" class="text-xs text-success">
                              <font-awesome-icon :icon="['fas', 'check']" class="mr-1" />
                              {{ t('exchanges.procurementCreated') }}
                            </span>
                            <SecondaryButton v-else class="text-xs" @click="createProcurementFromExchange(req)">
                              <font-awesome-icon :icon="['fas', 'folder-plus']" class="mr-1" />
                              {{ t('exchanges.createProcurement') }}
                            </SecondaryButton>
                          </template>
                        </div>
                      </template>
                      <template v-else>
                        <TextInput v-model="newItemName" :placeholder="t('exchanges.newItemName')" />
                        <SecondaryButton class="text-xs mt-1" @click="createNewItemForExchange = false">
                          {{ t('exchanges.selectExisting') }}
                        </SecondaryButton>
                      </template>
                    </div>
                    <div class="space-y-1">
                      <label class="block text-xs font-medium text-(--text-muted)">{{ t('exchanges.note') }}</label>
                      <TextInput v-model="updateNote" :placeholder="t('exchanges.notePlaceholder')" />
                    </div>
                    </div>
                    <div class="flex gap-2 justify-end">
                      <PrimaryButton :disabled="updateSaving || !updateTargetStatus" @click="submitStatusUpdate(req.id)">
                        {{ updateSaving ? t('common.loading') : t('exchanges.updateStatus') }}
                      </PrimaryButton>
                      <SecondaryButton @click="cancelStatusUpdate">{{ t('common.cancel') }}</SecondaryButton>
                    </div>
                  </div>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </NeutralContainer>

      <!-- Create modal (step wizard) -->
      <Modal v-model="showCreateModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('exchanges.createTitle') }}</SectionHeader>
          <p class="text-xs text-(--text-muted)">{{ t('exchanges.step') }} {{ createStep }} / {{ totalCreateSteps }}</p>

          <!-- Step 1: Select member -->
          <template v-if="createStep === 1">
            <div class="space-y-1">
              <label class="block text-sm font-medium">{{ t('exchanges.member') }}</label>
              <SelectInput v-if="canManageInventory()" v-model="createMemberId">
                <option value="" disabled>{{ t('exchanges.selectMember') }}</option>
                <option v-for="m in membersWithItemsList" :key="m.id" :value="String(m.id)">
                  {{ m.name || m.email || `#${m.id}` }}
                </option>
              </SelectInput>
              <SelectInput v-else-if="isMemberManager() && managed.length > 0" v-model="createMemberId">
                <option v-if="membersWithItems.has(sessionInfo?.member?.id ?? 0)" :value="String(sessionInfo?.member?.id ?? '')">{{ t('profile.myInventorySelf') }}</option>
                <option v-for="m in managedWithItemsList" :key="m.id" :value="String(m.id)">
                  {{ m.name || m.email }}
                </option>
              </SelectInput>
            </div>
            <div class="flex justify-end gap-3">
              <SecondaryButton @click="showCreateModal = false">{{ t('common.cancel') }}</SecondaryButton>
              <PrimaryButton :disabled="!createMemberId" @click="createStepNext">
                {{ t('exchanges.stepNext') }}
              </PrimaryButton>
            </div>
          </template>

          <!-- Step 2: Select item -->
          <template v-if="createStep === 2">
            <Spinner v-if="createLoadingItems" size="md" />
            <template v-else>
              <div v-if="createMemberItems.length === 0" class="text-sm text-(--text-muted) py-2">
                {{ t('exchanges.noItemsForMember') }}
              </div>
              <div v-else class="space-y-1">
                <label class="block text-sm font-medium">{{ t('exchanges.selectItem') }}</label>
                <SelectInput v-model="createItemId">
                  <option value="" disabled>{{ t('exchanges.selectItem') }}</option>
                  <option v-for="item in createMemberItems" :key="item.id" :value="String(item.id)">
                    {{ item.inventoryName }} — {{ item.name }}
                    {{ item.sizeName ?? '' }}
                    {{ item.internalId ? `(${item.internalId})` : '' }}
                  </option>
                </SelectInput>
              </div>
            </template>
            <div class="flex justify-between">
              <SecondaryButton @click="createStepBack">{{ t('common.back') }}</SecondaryButton>
              <PrimaryButton :disabled="!createItemId" @click="createStepNextFromItem">
                {{ t('exchanges.stepNext') }}
              </PrimaryButton>
            </div>
          </template>

          <!-- Step 3: Select new size (conditional) -->
          <template v-if="createStep === 3 && needsSizeStep">
            <p class="text-sm" v-if="selectedCreateItem">
              {{ selectedCreateItem.inventoryName }} — {{ selectedCreateItem.name }}
              <span class="text-(--text-muted)">{{ selectedCreateItem.sizeName ?? t('common.unisize') }}</span>
            </p>
            <div class="space-y-1">
              <label class="block text-sm font-medium">{{ t('exchanges.newSize') }}</label>
              <SelectInput v-model="createNewSizeId">
                <option value="" disabled>{{ t('exchanges.noSize') }}</option>
                <option v-for="size in createItemSizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
              </SelectInput>
            </div>
            <div class="flex justify-between">
              <SecondaryButton @click="createStepBack">{{ t('common.back') }}</SecondaryButton>
              <PrimaryButton :disabled="!createNewSizeId" @click="createStepNext">
                {{ t('exchanges.stepNext') }}
              </PrimaryButton>
            </div>
          </template>

          <!-- Final step: Reason -->
          <template v-if="isReasonStep">
            <p class="text-sm" v-if="selectedCreateItem">
              {{ selectedCreateItem.inventoryName }} — {{ selectedCreateItem.name }}
              <span class="text-(--text-muted)">{{ selectedCreateItem.sizeName ?? t('common.unisize') }}</span>
              <template v-if="createNewSizeId">
                → <span class="font-medium">{{ createItemSizes.find(s => s.id === Number(createNewSizeId))?.label }}</span>
              </template>
            </p>
            <div class="space-y-1">
              <label class="block text-sm font-medium">{{ t('exchanges.reason') }}</label>
              <TextAreaInput v-model="createReason" :placeholder="t('exchanges.reasonPlaceholder')" />
            </div>
            <div class="flex justify-between">
              <SecondaryButton @click="createStepBack">{{ t('common.back') }}</SecondaryButton>
              <PrimaryButton :disabled="createSaving || !canCreate" @click="submitCreate">
                {{ createSaving ? t('common.loading') : t('exchanges.submit') }}
              </PrimaryButton>
            </div>
          </template>
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
                <span class="font-medium">{{ statusLabel(entry.oldStatus as ExchangeStatusName) }}</span>
                <font-awesome-icon :icon="['fas', 'arrow-right']" class="text-(--text-muted)" />
                <span class="font-medium">{{ statusLabel(entry.newStatus as ExchangeStatusName) }}</span>
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
