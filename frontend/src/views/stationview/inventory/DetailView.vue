/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import Modal from '@/components/feedback/Modal.vue'
import ItemsTable from './ItemsTable.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import type { InventoryDetail, InventoryItem, InventorySize, StationMember, ProcurementEntry } from '@/api/types'
import { InventoryTypes, StationPermission } from '@/api/types'
import { inventory, stationMembers, procurement } from '@/api'
import { useSession } from '@/composables/useSession'
import { getLentOutByInventory, type LentOutItem } from '@/api/lending'
import { useStations } from '@/composables/useStations'
import InventoryStatsPanel from './detailview/InventoryStatsPanel.vue'
import LentOutTable from './detailview/LentOutTable.vue'
import EditItemModal from './detailview/EditItemModal.vue'
import HistoryModal from './detailview/HistoryModal.vue'
import ProcurementTable from './detailview/ProcurementTable.vue'
import LostItemsTable from './detailview/LostItemsTable.vue'
import ItemModals from './editview/ItemModals.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { currentStationId } = useStations()
const { hasPermission } = useSession()
const canEdit = computed(() => hasPermission(StationPermission.INVENTORY_EDIT))
const canProcure = computed(() => hasPermission(StationPermission.INVENTORY_PROCUREMENT))
const canCreateInternal = computed(() => hasPermission(StationPermission.INVENTORY_CREATE_INTERNAL))
const canCreateExternal = computed(() => hasPermission(StationPermission.INVENTORY_CREATE_EXTERNAL))

const inventoryId = computed(() => Number(route.params.id))
const detail = ref<InventoryDetail | null>(null)

const canCreateItem = computed(() => {
  const type = detail.value?.inventoryType ?? InventoryTypes.INTERNAL
  if (type === InventoryTypes.INTERNAL) return canCreateInternal.value
  if (type === InventoryTypes.EXTERNAL) return canCreateExternal.value
  // MIXED
  return canCreateInternal.value || canCreateExternal.value
})
const canQuickAssign = computed(() => {
  const type = detail.value?.inventoryType ?? InventoryTypes.INTERNAL
  return (type === InventoryTypes.EXTERNAL || type === InventoryTypes.MIXED) && canCreateExternal.value
})
const canAddInternal = computed(() => {
  const type = detail.value?.inventoryType ?? InventoryTypes.INTERNAL
  return type !== InventoryTypes.EXTERNAL && canCreateInternal.value
})
const items = ref<InventoryItem[]>([])
const memberMap = ref<Map<number, StationMember>>(new Map())
const openProcurement = ref<ProcurementEntry[]>([])
const lentOutItems = ref<LentOutItem[]>([])
const loading = ref(true)
const error = ref('')

// Assign modal
const showAssignModal = ref(false)
const assignItemId = ref<number | null>(null)
const assignMemberId = ref('')

// Procurement modal
const showProcurementModal = ref(false)
const procMemberId = ref('')
const procSizeId = ref('')
const procNotes = ref('')
const procCreated = ref(false)

// Edit item modal
const showEditItemModal = ref(false)
const editingItem = ref<InventoryItem | null>(null)

// Item creation modal (reused from edit view)
const itemModals = ref<InstanceType<typeof ItemModals> | null>(null)

// History modal
const showHistoryModal = ref(false)
const historyTarget = ref<InventoryItem | null>(null)

// Delete
const showDeleteModal = ref(false)
const deleteTarget = ref<InventoryItem | null>(null)

// Lending: map item ID → station name for actively lent items
const activeLendingStatuses = new Set(['APPROVED', 'LENT'])

const lentItemStationMap = computed(() => {
  const map = new Map<number, string>()
  for (const l of lentOutItems.value) {
    if (l.assignedItemId && activeLendingStatuses.has(l.status)) {
      map.set(l.assignedItemId, l.requestingStationName)
    }
  }
  return map
})

function isLentOut(itemId: number): boolean {
  return lentItemStationMap.value.has(itemId)
}

const totalCount = computed(() => items.value.length)
const lostCount = computed(() => items.value.filter(i => i.lostAt).length)
const lentOutCount = computed(() => lentItemStationMap.value.size)
const assignedCount = computed(() => items.value.filter(i => i.assignedTo && !i.lostAt && !isLentOut(i.id)).length)
const freeCount = computed(() => items.value.filter(i => !i.assignedTo && !i.lostAt && !isLentOut(i.id)).length)
const lostItems = computed(() => items.value.filter(i => i.lostAt))

const sizeDistribution = computed(() => {
  if (!detail.value?.hasSizes || !detail.value.sizes) return []
  return detail.value.sizes.map(size => {
    const sizeItems = items.value.filter(i => i.sizeId === size.id)
    return {
      size,
      total: sizeItems.length,
      assigned: sizeItems.filter(i => i.assignedTo && !i.lostAt && !isLentOut(i.id)).length,
      free: sizeItems.filter(i => !i.assignedTo && !i.lostAt && !isLentOut(i.id)).length,
      lost: sizeItems.filter(i => i.lostAt).length,
      lent: sizeItems.filter(i => isLentOut(i.id)).length,
    }
  })
})

const noSizeItems = computed(() => {
  if (!detail.value?.hasSizes) return []
  const nosizeItems = items.value.filter(i => !i.sizeId)
  if (nosizeItems.length === 0) return []
  return [{
    size: null as InventorySize | null,
    total: nosizeItems.length,
    assigned: nosizeItems.filter(i => i.assignedTo && !i.lostAt && !isLentOut(i.id)).length,
    free: nosizeItems.filter(i => !i.assignedTo && !i.lostAt && !isLentOut(i.id)).length,
    lost: nosizeItems.filter(i => i.lostAt).length,
    lent: nosizeItems.filter(i => isLentOut(i.id)).length,
  }]
})

const allSizeStats = computed(() => [...sizeDistribution.value, ...noSizeItems.value])

function sizeName(sizeId: number | null | undefined): string {
  if (!sizeId || !detail.value?.sizes) return ''
  return detail.value.sizes.find(s => s.id === sizeId)?.label ?? ''
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [inv, allItems, members] = await Promise.all([
      inventory.getInventory(inventoryId.value),
      inventory.listItems(inventoryId.value),
      currentStationId.value ? stationMembers.listMembers() : Promise.resolve([]),
    ])
    detail.value = inv
    items.value = allItems
    const map = new Map<number, StationMember>()
    for (const m of members) map.set(m.id, m)
    memberMap.value = map
    try {
      const allProc = await procurement.listOpen()
      openProcurement.value = allProc.filter(p => p.inventoryId === inventoryId.value)
    } catch { openProcurement.value = [] }
    try {
      lentOutItems.value = await getLentOutByInventory(inventoryId.value)
    } catch { lentOutItems.value = [] }
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

const freeItems = computed(() => items.value.filter(i => !i.assignedTo && !i.lostAt && !isLentOut(i.id)))
const unassignedMembers = computed(() => [...memberMap.value.values()])

function openAssign(itemId: number) {
  assignItemId.value = itemId
  assignMemberId.value = ''
  showAssignModal.value = true
}

async function submitAssign() {
  if (!assignItemId.value || !assignMemberId.value) return
  const mid = Number(assignMemberId.value)
  const m = memberMap.value.get(mid)
  try {
    await inventory.assignItem(assignItemId.value, { memberId: mid, memberName: m?.name ?? '' })
    showAssignModal.value = false
    await loadData()
  } catch { /* ignore */ }
}

function openProcurementModal() {
  procMemberId.value = ''
  procSizeId.value = ''
  procNotes.value = ''
  procCreated.value = false
  showProcurementModal.value = true
}

async function submitProcurement() {
  if (!procMemberId.value) return
  try {
    await procurement.createProcurement({
      inventoryId: inventoryId.value,
      memberId: Number(procMemberId.value),
      sizeId: procSizeId.value ? Number(procSizeId.value) : undefined,
      notes: procNotes.value || undefined,
    })
    procCreated.value = true
    await loadData()
  } catch { /* ignore */ }
}

function onAssign(item: InventoryItem) { openAssign(item.id) }

async function onUnassign(item: InventoryItem) {
  error.value = ''
  try {
    if (detail.value?.inventoryType === InventoryTypes.EXTERNAL) {
      await inventory.deleteItem(item.id)
    } else {
      await inventory.assignItem(item.id, { memberId: null, memberName: '' })
    }
    await loadData()
  } catch { error.value = t('common.error') }
}

function onEditItem(item: InventoryItem) {
  editingItem.value = item
  showEditItemModal.value = true
}

async function onMarkLost(item: InventoryItem) {
  error.value = ''
  try { await inventory.markLost(item.id); await loadData() }
  catch { error.value = t('common.error') }
}

async function onMarkFound(item: InventoryItem) {
  error.value = ''
  try { await inventory.markFound(item.id); await loadData() }
  catch { error.value = t('common.error') }
}

function onHistory(item: InventoryItem) {
  historyTarget.value = item
  showHistoryModal.value = true
}

function onDeleteItem(item: InventoryItem) {
  deleteTarget.value = item
  showDeleteModal.value = true
}

async function confirmDeleteItem() {
  if (!deleteTarget.value) return
  try {
    await inventory.deleteItem(deleteTarget.value.id)
    showDeleteModal.value = false
    deleteTarget.value = null
    await loadData()
  } catch { error.value = t('common.error') }
}

async function fulfillProcurement(id: number) {
  error.value = ''
  try { await procurement.fulfill(id); await loadData() }
  catch { error.value = t('common.error') }
}

function goBack() { router.push({ name: 'inventory-manage' }) }

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
        <div>
          <SectionHeader>{{ detail?.name ?? '' }}</SectionHeader>
          <p class="text-sm text-(--text-muted)">
            {{ t('inventory.manage.type.' + (detail?.inventoryType ?? InventoryTypes.INTERNAL)) }}
            <span v-if="detail?.hasSizes"> &middot; {{ t('inventory.manage.withSizes') }}</span>
          </p>
        </div>
        <SecondaryButton :icon="['fas', 'chevron-left']" @click="goBack">
          {{ t('inventory.manage.back') }}
        </SecondaryButton>
      </div>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && detail">
        <InventoryStatsPanel
          :total-count="totalCount"
          :free-count="freeCount"
          :assigned-count="assignedCount"
          :lost-count="lostCount"
          :lent-out-count="lentOutCount"
          :has-sizes="detail.hasSizes"
          :size-stats="allSizeStats"
        />

        <ProcurementTable
          :entries="openProcurement"
          :readonly="!canProcure"
          :can-create="canProcure"
          @fulfill="fulfillProcurement"
          @create="openProcurementModal"
        />

        <LentOutTable :lent-out-items="lentOutItems" :lent-out-count="lentOutCount" />

        <!-- All items -->
        <div v-if="items.length > 0 || canCreateItem" class="flex flex-wrap items-center justify-between gap-2">
          <SubHeader>{{ t('inventory.edit.itemsTitle') }} ({{ items.length }})</SubHeader>
          <div v-if="canCreateItem" class="flex items-center gap-2">
            <PrimaryButton v-if="canQuickAssign" :icon="['fas', 'user-plus']" @click="itemModals?.openQuickAssign()">
              {{ t('inventory.edit.quickAssign') }}
            </PrimaryButton>
            <PrimaryButton v-if="canAddInternal" :icon="['fas', 'plus']" @click="itemModals?.openAdd()">
              {{ t('inventory.edit.addItem') }}
            </PrimaryButton>
          </div>
        </div>
        <template v-if="items.length > 0">
          <ItemsTable
            :items="items"
            :has-sizes="detail.hasSizes"
            :sizes="detail.sizes"
            :members="memberMap"
            :show-actions="canEdit"
            :show-history="true"
            :inventory-type="detail.inventoryType ?? InventoryTypes.INTERNAL"
            :lent-out-items="lentOutItems"
            :lent-item-map="lentItemStationMap"
            @assign="onAssign"
            @unassign="onUnassign"
            @edit="onEditItem"
            @mark-lost="onMarkLost"
            @mark-found="onMarkFound"
            @history="onHistory"
            @delete="onDeleteItem"
          />
        </template>

        <!-- Free items for assignment -->
        <template v-if="canEdit && freeItems.length > 0">
          <SubHeader>{{ t('inventory.detail.freeItems') }}</SubHeader>
          <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
            <NeutralContainer v-for="item in freeItems" :key="item.id" class="flex items-center justify-between gap-2">
              <div>
                <div class="text-sm font-medium">
                  {{ item.name }}
                  <SizeBadge v-if="sizeName(item.sizeId)">{{ sizeName(item.sizeId) }}</SizeBadge>
                </div>
                <div v-if="item.internalId" class="text-xs text-(--text-muted)">{{ item.internalId }}</div>
              </div>
              <PrimaryButton @click="openAssign(item.id)">
                {{ t('inventory.detail.assign') }}
              </PrimaryButton>
            </NeutralContainer>
          </div>
        </template>

        <!-- Actions -->
        <div v-if="canProcure" class="flex gap-2">
          <PrimaryButton :icon="['fas', 'folder-plus']" @click="openProcurementModal">
            {{ t('inventory.detail.createProcurement') }}
          </PrimaryButton>
        </div>

        <LostItemsTable :items="lostItems" :sizes="detail.sizes ?? []" :member-map="memberMap" />
      </template>

      <!-- Assign modal -->
      <Modal v-model="showAssignModal">
        <div class="space-y-3">
          <SectionHeader>{{ t('inventory.detail.assign') }}</SectionHeader>
          <SelectInput v-model="assignMemberId">
            <option value="" disabled>{{ t('inventory.detail.selectMember') }}</option>
            <option v-for="m in unassignedMembers" :key="m.id" :value="String(m.id)">
              {{ m.name || m.email || `#${m.id}` }}
            </option>
          </SelectInput>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showAssignModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="!assignMemberId" @click="submitAssign">{{ t('inventory.detail.assign') }}</PrimaryButton>
          </div>
        </div>
      </Modal>

      <!-- Procurement modal -->
      <Modal v-model="showProcurementModal">
        <div class="space-y-3">
          <SectionHeader>{{ t('inventory.detail.createProcurement') }}</SectionHeader>
          <Alert v-if="procCreated" variant="success">{{ t('inventory.check.procurementCreated') }}</Alert>
          <template v-if="!procCreated">
            <SelectInput v-model="procMemberId">
              <option value="" disabled>{{ t('inventory.detail.selectMember') }}</option>
              <option v-for="m in unassignedMembers" :key="m.id" :value="String(m.id)">
                {{ m.name || m.email || `#${m.id}` }}
              </option>
            </SelectInput>
            <SelectInput v-if="detail?.hasSizes" v-model="procSizeId">
              <option value="">{{ t('inventory.detail.anySize') }}</option>
              <option v-for="s in detail.sizes" :key="s.id" :value="String(s.id)">{{ s.label }}</option>
            </SelectInput>
            <TextAreaInput v-model="procNotes" :placeholder="t('inventory.detail.procurementNotes')" :rows="2" />
          </template>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showProcurementModal = false">{{ t('common.close') }}</SecondaryButton>
            <PrimaryButton v-if="!procCreated" :disabled="!procMemberId" @click="submitProcurement">{{ t('common.save') }}</PrimaryButton>
          </div>
        </div>
      </Modal>

      <EditItemModal
        v-model="showEditItemModal"
        :item="editingItem"
        :has-sizes="detail?.hasSizes ?? false"
        :sizes="detail?.sizes ?? []"
        @saved="loadData"
      />

      <ItemModals
        v-if="detail"
        ref="itemModals"
        :detail="detail"
        :members="[...memberMap.values()]"
        @items-changed="loadData"
        @error="error = $event"
      />

      <HistoryModal
        v-model="showHistoryModal"
        :item="historyTarget"
        :member-map="memberMap"
      />

      <ConfirmDeleteModal
        v-model="showDeleteModal"
        :message="t('inventory.edit.deleteConfirm', { name: deleteTarget?.name })"
        @confirm="confirmDeleteItem"
      />
    </div>
  </ViewContent>
</template>
