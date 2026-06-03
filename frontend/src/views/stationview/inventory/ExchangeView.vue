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
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import ExchangeStatusBadge from './exchangeview/ExchangeStatusBadge.vue'
import ExchangeStatusUpdatePanel from './exchangeview/ExchangeStatusUpdatePanel.vue'
import ExchangeCreateModal from './exchangeview/ExchangeCreateModal.vue'
import ExchangeLogModal from './exchangeview/ExchangeLogModal.vue'
import ExchangeExportFieldPicker from './exchangeview/ExchangeExportFieldPicker.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'
import type {
  ExchangeRequestEntry,
  ExchangeStatusName,
  Inventory,
  InventoryItem,
  ProfileField,
  StationMember,
} from '@/api/types'
import type { ManagedMember } from '@/api/managedMembers'
import { InventoryTypes, ExchangeStatus } from '@/api/types'
import { exchanges, inventory, profileFields, stationMembers, managedMembers } from '@/api'
import { useSession } from '@/composables/useSession'
import { useStations } from '@/composables/useStations'
import { useBreakpoint } from '@/composables/useBreakpoint'

const { t } = useI18n()
const router = useRouter()
const { canManageInventory, isGuardian, sessionInfo } = useSession()
const { activeStation } = useStations()
const { isMobile } = useBreakpoint()

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

const showMemberColumn = computed(() => canManageInventory() || isGuardian())
const showCreateModal = ref(false)

const membersWithItemsList = computed(() =>
  members.value.filter(m => membersWithItems.value.has(m.id))
)
const managedWithItemsList = computed(() =>
  managed.value.filter(m => membersWithItems.value.has(m.id))
)

// Status flow
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

// Status update
const updatingId = ref<number | null>(null)
const availableItems = ref<InventoryItem[]>([])

// Log modal
const showLogModal = ref(false)
const logExchangeId = ref<number | null>(null)

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const stationId = activeStation.value?.stationId
    const [r, inv, m, mgd] = await Promise.all([
      exchanges.listExchanges(),
      canManageInventory() ? inventory.listInventories() : Promise.resolve([]),
      stationId && canManageInventory() ? stationMembers.listMembers() : Promise.resolve([]),
      isGuardian() ? managedMembers.listManaged() : Promise.resolve([]),
    ])
    requests.value = r
    inventories.value = inv
    members.value = m
    managed.value = mgd
    const withItems = new Set<number>()
    if (inv.length > 0) {
      const itemArrays = await Promise.all(inv.map(i => inventory.listItems(i.id)))
      for (const items of itemArrays) {
        for (const item of items) {
          if (item.assignedTo && !item.lostAt) withItems.add(item.assignedTo)
        }
      }
    } else if (mgd.length > 0) {
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

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

async function startStatusUpdate(request: ExchangeRequestEntry) {
  updatingId.value = request.id
  try {
    const allItems = await inventory.listItems(request.inventoryId)
    availableItems.value = allItems.filter(i => !i.assignedTo && !i.lostAt)
  } catch { availableItems.value = [] }
}

async function onStatusUpdated() {
  updatingId.value = null
  requests.value = await exchanges.listExchanges()
}

async function deleteRequest(id: number) {
  error.value = ''
  try {
    await exchanges.deleteExchange(id)
    requests.value = await exchanges.listExchanges()
  } catch { error.value = t('common.error') }
}

function openLog(id: number) {
  logExchangeId.value = id
  showLogModal.value = true
}

async function enterExportMode() {
  exportMode.value = true
  selectedForExport.value = new Set(requests.value.map(r => r.id))
  selectedExportFields.value = new Set()
  try { allFields.value = await profileFields.listFields() } catch { allFields.value = [] }
}

function cancelExport() {
  exportMode.value = false
  selectedForExport.value = new Set()
  selectedExportFields.value = new Set()
}

function toggleExportField(fieldId: number) {
  const s = new Set(selectedExportFields.value)
  if (s.has(fieldId)) s.delete(fieldId); else s.add(fieldId)
  selectedExportFields.value = s
}

function toggleExportSelection(id: number) {
  const newSet = new Set(selectedForExport.value)
  if (newSet.has(id)) newSet.delete(id); else newSet.add(id)
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
  } catch { error.value = t('common.error') }
  finally { exporting.value = false }
}

async function onCreated() {
  requests.value = await exchanges.listExchanges()
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
            <SecondaryButton :icon="['fas', 'download']" :disabled="exporting || selectedForExport.size === 0" @click="exportSelected">
              {{ exporting ? t('common.loading') : t('exchanges.download') }} ({{ selectedForExport.size }})
            </SecondaryButton>
            <SecondaryButton @click="cancelExport">{{ t('common.cancel') }}</SecondaryButton>
          </template>
          <template v-else>
            <SecondaryButton :icon="['fas', 'file-export']" v-if="canManageInventory() && requests.length > 0" @click="enterExportMode">
              {{ t('exchanges.export') }}
            </SecondaryButton>
            <PrimaryButton :icon="['fas', 'plus']" @click="showCreateModal = true">
              {{ t('exchanges.create') }}
            </PrimaryButton>
          </template>
        </div>
      </div>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <ExchangeExportFieldPicker
        v-if="exportMode"
        :fields="allFields"
        :selected-field-ids="selectedExportFields"
        @toggle-field="toggleExportField"
      />

      <EmptyState v-if="!loading && requests.length === 0">{{ t('exchanges.empty') }}</EmptyState>

      <!-- Mobile cards -->
      <div v-if="!loading && requests.length > 0 && isMobile" class="space-y-3">
        <template v-for="req in requests" :key="req.id">
          <NeutralContainer class="space-y-2">
            <div class="flex items-center justify-between">
              <span class="text-sm font-medium">{{ req.inventoryName }}</span>
              <ExchangeStatusBadge :status="req.status" />
            </div>
            <div v-if="showMemberColumn" class="text-xs text-(--text-muted)"><MemberName :identity="req.memberIdentity ?? null"/></div>
            <div class="text-xs">{{ req.oldSizeLabel ?? t('common.unisize') }} &rarr; {{ req.newSizeLabel ?? t('common.unisize') }}</div>
            <div v-if="req.reason" class="text-xs text-(--text-muted) truncate">{{ req.reason }}</div>
            <div class="text-xs text-(--text-muted)">{{ formatDate(req.createdAt) }}</div>
            <div class="flex items-center gap-1">
              <CheckboxInput v-if="exportMode" :model-value="selectedForExport.has(req.id)" @update:model-value="toggleExportSelection(req.id)" />
              <SecondaryButton @click="openLog(req.id)">
                <font-awesome-icon :icon="['fas', 'clock-rotate-left']" />
              </SecondaryButton>
              <SecondaryButton v-if="canManageInventory() && req.status !== ExchangeStatus.EXCHANGED" @click="startStatusUpdate(req)">
                <font-awesome-icon :icon="['fas', 'arrow-right']" />
              </SecondaryButton>
              <DeleteButton v-if="canManageInventory()" @click="deleteRequest(req.id)" />
            </div>
            <div v-if="updatingId === req.id" class="pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
              <ExchangeStatusUpdatePanel
                :request="req"
                :next-statuses="nextStatuses(req.status, req.inventoryType)"
                :available-items="availableItems"
                @done="onStatusUpdated"
                @cancel="updatingId = null"
                @error="(msg) => error = msg"
              />
            </div>
          </NeutralContainer>
        </template>
      </div>

      <!-- Desktop table -->
      <NeutralContainer v-else-if="!loading && requests.length > 0" class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <THead>
              <th v-if="exportMode" class="px-1 py-2 w-8">
                <CheckboxInput :model-value="selectedForExport.size === requests.length && requests.length > 0" @update:model-value="toggleSelectAll" />
              </th>
              <Th v-if="showMemberColumn">{{ t('exchanges.colMember') }}</Th>
              <Th>{{ t('exchanges.colInventory') }}</Th>
              <Th>{{ t('exchanges.colOldSize') }}</Th>
              <Th>{{ t('exchanges.colNewSize') }}</Th>
              <Th>{{ t('exchanges.colStatus') }}</Th>
              <Th>{{ t('exchanges.colReason') }}</Th>
              <Th>{{ t('exchanges.colDate') }}</Th>
              <th class="px-3 py-2"></th>
            </THead>
          </thead>
          <tbody>
            <template v-for="req in requests" :key="req.id">
              <TRow>
                <td v-if="exportMode" class="px-1 py-2.5 w-8">
                  <CheckboxInput :model-value="selectedForExport.has(req.id)" @update:model-value="toggleExportSelection(req.id)" />
                </td>
                <Td v-if="showMemberColumn">
                  <button
                    v-if="canManageInventory()"
                    class="text-primary hover:underline cursor-pointer"
                    @click="router.push({ name: 'inventory-member', params: { memberId: req.memberId } })"
                  ><MemberName :identity="req.memberIdentity ?? null"/></button>
                  <MemberName v-else :identity="req.memberIdentity ?? null"/>
                </Td>
                <Td class="font-medium">{{ req.inventoryName }}</Td>
                <Td>{{ req.oldSizeLabel ?? t('common.unisize') }}</Td>
                <Td>{{ req.newSizeLabel ?? t('common.unisize') }}</Td>
                <Td><ExchangeStatusBadge :status="req.status" /></Td>
                <Td class="text-(--text-muted) max-w-48 truncate">{{ req.reason }}</Td>
                <Td muted>
                  {{ formatDate(req.createdAt) }}
                  <span v-if="req.createdByName" class="block text-xs italic">{{ t('common.createdBy', { name: req.createdByName }) }}</span>
                </Td>
                <Td align="right">
                  <div class="flex items-center justify-end gap-1">
                    <SecondaryButton @click="openLog(req.id)">
                      <font-awesome-icon :icon="['fas', 'clock-rotate-left']" />
                    </SecondaryButton>
                    <SecondaryButton v-if="canManageInventory() && req.status !== ExchangeStatus.EXCHANGED" @click="startStatusUpdate(req)">
                      <font-awesome-icon :icon="['fas', 'arrow-right']" />
                    </SecondaryButton>
                    <DeleteButton v-if="canManageInventory()" @click="deleteRequest(req.id)" />
                  </div>
                </Td>
              </TRow>
              <tr v-if="updatingId === req.id" class="bg-(--bg-accent)/30">
                <td :colspan="(showMemberColumn ? 8 : 7) + (exportMode ? 1 : 0)" class="px-3 py-3">
                  <ExchangeStatusUpdatePanel
                    :request="req"
                    :next-statuses="nextStatuses(req.status, req.inventoryType)"
                    :available-items="availableItems"
                    @done="onStatusUpdated"
                    @cancel="updatingId = null"
                    @error="(msg) => error = msg"
                  />
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </NeutralContainer>

      <ExchangeCreateModal
        v-model="showCreateModal"
        :requests="requests"
        :members-with-items="membersWithItems"
        :members-with-items-list="membersWithItemsList"
        :managed-with-items-list="managedWithItemsList"
        :managed="managed"
        @created="onCreated"
        @error="(msg) => error = msg"
      />

      <ExchangeLogModal
        v-model="showLogModal"
        :exchange-id="logExchangeId"
        @error="(msg) => error = msg"
      />
    </div>
  </ViewContent>
</template>
