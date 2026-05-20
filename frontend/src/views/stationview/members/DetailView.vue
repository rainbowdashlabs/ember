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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import Modal from '@/components/feedback/Modal.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import ChangeHistory from './detailview/ChangeHistory.vue'
import type { ProfileField, ProfileFieldChange, StationMember } from '@/api/types'
import { Roles, hasTeamRole, ExchangeStatus, StationModules } from '@/api/types'
import type { Inventory, InventoryItem } from '@/api/types'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldValueDisplay from '@/components/display/FieldValueDisplay.vue'
import InventoryItemCard from '@/views/stationview/inventory/InventoryItemCard.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import { profileFields, profileFieldChanges, stationMembers, members, inventory, exchanges } from '@/api'
import type { MyInventoryItem } from '@/api/inventory'
import type { ExchangeRequestEntry } from '@/api/types'
import { useStations } from '@/composables/useStations'
import { useSession } from '@/composables/useSession'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { currentStationId } = useStations()
const { sessionInfo, canManageMembers, isGuardian, canManageInventory, isModuleEnabled } = useSession()
const inventoryEnabled = computed(() => isModuleEnabled(StationModules.INVENTORY))
const showInventoryManagement = computed(() => inventoryEnabled.value && canManageInventory())

const memberId = computed(() => Number(route.params.id))
const currentMemberId = computed(() => sessionInfo.value?.member?.id ?? 0)
const showChangeHistory = computed(() => canManageMembers() || isGuardian())

const member = ref<StationMember | null>(null)
const fields = ref<ProfileField[]>([])
const values = ref<Map<number, string>>(new Map())
const memberRoles = ref<string[]>([])
const managers = ref<StationMember[]>([])
const managerValues = ref<Map<number, Map<number, string>>>(new Map())
const managerRoles = ref<Map<number, string[]>>(new Map())
const allMembers = ref<StationMember[]>([])
const changes = ref<ProfileFieldChange[]>([])
const memberInventory = ref<MyInventoryItem[]>([])
const memberExchanges = ref<ExchangeRequestEntry[]>([])
const loading = ref(true)
const error = ref('')

// Link manager
const showLinkManager = ref(false)
const selectedManagerId = ref('')

// Create manager
const showCreateManager = ref(false)
const newMgrFirstName = ref('')
const newMgrLastName = ref('')
const newMgrEmail = ref('')
const creatingManager = ref(false)

const applicableFields = computed(() => {
  const scopes: string[] = []
  if (memberRoles.value.includes(Roles.MEMBER)) scopes.push(Roles.MEMBER)
  if (hasTeamRole(memberRoles.value)) scopes.push(Roles.TEAM)
  if (memberRoles.value.includes(Roles.GUARDIAN)) scopes.push(Roles.GUARDIAN)
  return fields.value.filter(f => {
    if (f.scope === 'GROUP') return false
    return scopes.includes(f.scope ?? Roles.MEMBER)
  })
})

const availableManagers = computed(() => {
  const managerIds = new Set(managers.value.map(m => m.id))
  managerIds.add(memberId.value)
  return allMembers.value
      .filter(m => !managerIds.has(m.id))
      .sort((a, b) => memberDisplayName(a).localeCompare(memberDisplayName(b)))
})

function memberDisplayName(m: StationMember): string {
  return m.name && m.name.trim() ? m.name : m.email ?? `#${m.id}`
}

function getFieldValue(fieldId: number): unknown {
  const raw = values.value.get(fieldId) ?? ''
  try { return JSON.parse(raw) } catch { return raw }
}

function getManagerFieldValue(mgrId: number, fieldId: number): unknown {
  const vals = managerValues.value.get(mgrId)
  if (!vals) return ''
  const raw = vals.get(fieldId) ?? ''
  try { return JSON.parse(raw) } catch { return raw }
}

function getManagerFields(mgrId: number): typeof fields.value {
  const roles = managerRoles.value.get(mgrId) ?? []
  const scopes: string[] = []
  if (roles.includes(Roles.MEMBER)) scopes.push(Roles.MEMBER)
  if (hasTeamRole(roles)) scopes.push(Roles.TEAM)
  if (roles.includes(Roles.GUARDIAN)) scopes.push(Roles.GUARDIAN)
  return fields.value.filter(f => {
    if (f.scope === 'GROUP') return false
    return scopes.includes(f.scope ?? Roles.MEMBER)
  })
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [allFields, allMems, roles, profileValues, mgrs] = await Promise.all([
      profileFields.listFields(),
      stationMembers.listMembers(currentStationId.value!),
      stationMembers.getRoles(memberId.value),
      profileFields.getValues(memberId.value),
      stationMembers.getManagers(memberId.value),
    ])
    fields.value = allFields
    allMembers.value = allMems
    member.value = allMems.find(m => m.id === memberId.value) ?? null
    memberRoles.value = roles.map(r => r.role)
    managers.value = mgrs

    const map = new Map<number, string>()
    for (const v of profileValues) {
      map.set(v.fieldId, v.value ?? '')
    }
    values.value = map

    // Load manager details and change history
    await loadManagerDetails(mgrs)
    if (showChangeHistory.value) {
      try {
        changes.value = await profileFieldChanges.getChanges(memberId.value)
      } catch { /* ignore if unauthorized */ }
    }
    // Load inventory and exchanges
    try {
      memberInventory.value = await inventory.memberItems(memberId.value)
    } catch { memberInventory.value = [] }
    try {
      const allExch = await exchanges.listExchanges()
      memberExchanges.value = allExch.filter(e => e.memberId === memberId.value && e.status !== ExchangeStatus.EXCHANGED)
    } catch { memberExchanges.value = [] }
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function loadManagerDetails(mgrs: StationMember[]) {
  const mgrVals = new Map<number, Map<number, string>>()
  const mgrRoles = new Map<number, string[]>()
  for (const mgr of mgrs) {
    try {
      const [vals, roles] = await Promise.all([
        profileFields.getValues(mgr.id),
        stationMembers.getRoles(mgr.id),
      ])
      const fieldMap = new Map<number, string>()
      for (const v of vals) { fieldMap.set(v.fieldId, v.value ?? '') }
      mgrVals.set(mgr.id, fieldMap)
      mgrRoles.set(mgr.id, roles.map(r => r.role))
    } catch { /* skip */ }
  }
  managerValues.value = mgrVals
  managerRoles.value = mgrRoles
}

async function linkManager() {
  if (!selectedManagerId.value) return
  error.value = ''
  try {
    const currentIds = managers.value.map(m => m.id)
    await stationMembers.setManagers(memberId.value, { managerIds: [...currentIds, Number(selectedManagerId.value)] })
    managers.value = await stationMembers.getManagers(memberId.value)
    await loadManagerDetails(managers.value)
    showLinkManager.value = false
    selectedManagerId.value = ''
  } catch {
    error.value = t('common.error')
  }
}

async function removeManager(mgrId: number) {
  error.value = ''
  try {
    const newIds = managers.value.filter(m => m.id !== mgrId).map(m => m.id)
    await stationMembers.setManagers(memberId.value, { managerIds: newIds })
    managers.value = await stationMembers.getManagers(memberId.value)
  } catch {
    error.value = t('common.error')
  }
}

async function createNewManager() {
  creatingManager.value = true
  error.value = ''
  try {
    const invited = await members.invite({
      email: newMgrEmail.value,
      firstName: newMgrFirstName.value,
      lastName: newMgrLastName.value,
    })
    // Find new member and assign as manager
    const updatedMembers = await stationMembers.listMembers(currentStationId.value!)
    const newMember = updatedMembers.find(m => m.accountId === invited.id)
    if (newMember) {
      const currentIds = managers.value.map(m => m.id)
      await stationMembers.setManagers(memberId.value, { managerIds: [...currentIds, newMember.id] })
      // Assign member_manager role to new account
      const allRoles = await stationMembers.listAllRoles()
      const mgrRoleNames: readonly string[] = [Roles.LOGIN, Roles.GUARDIAN]
      const mgrRoleIds = allRoles.filter(r => mgrRoleNames.includes(r.role)).map(r => r.id)
      await stationMembers.setRoles(newMember.id, { roleIds: mgrRoleIds })

      managers.value = await stationMembers.getManagers(memberId.value)
      await loadManagerDetails(managers.value)
      allMembers.value = updatedMembers
    }
    showCreateManager.value = false
    newMgrFirstName.value = ''
    newMgrLastName.value = ''
    newMgrEmail.value = ''
  } catch {
    error.value = t('common.error')
  } finally {
    creatingManager.value = false
  }
}

async function loadChanges() {
  try {
    changes.value = await profileFieldChanges.getChanges(memberId.value)
  } catch { /* ignore */ }
}

function itemExchange(itemId: number): ExchangeRequestEntry | undefined {
  return memberExchanges.value.find(e => e.itemId === itemId)
}

// Exchange modal
const showExchangeModal = ref(false)
const exchangeItem = ref<MyInventoryItem | null>(null)
const exchangeNewSizeId = ref<string>('')
const exchangeReason = ref('')
const exchangeSizes = ref<import('@/api/types').InventorySize[]>([])
const exchangeSaving = ref(false)
const exchangeSuccess = ref(false)

async function openExchangeModal(item: MyInventoryItem) {
  exchangeItem.value = item
  exchangeReason.value = ''
  exchangeNewSizeId.value = ''
  exchangeSizes.value = []
  exchangeSuccess.value = false
  showExchangeModal.value = true
  try {
    exchangeSizes.value = await inventory.listSizes(item.inventoryId)
  } catch { /* ignore */ }
}

async function submitExchange() {
  if (!exchangeItem.value || !exchangeReason.value.trim()) return
  exchangeSaving.value = true
  try {
    await exchanges.createExchange({
      memberId: memberId.value,
      itemId: exchangeItem.value.id,
      inventoryId: exchangeItem.value.inventoryId,
      oldSizeId: exchangeItem.value.sizeId ?? undefined,
      newSizeId: exchangeNewSizeId.value ? Number(exchangeNewSizeId.value) : undefined,
      reason: exchangeReason.value.trim(),
    })
    exchangeSuccess.value = true
  } catch {
    error.value = t('common.error')
  } finally {
    exchangeSaving.value = false
  }
}

// Assign inventory item
const showAssignModal = ref(false)
const inventories = ref<Inventory[]>([])
const assignInventoryId = ref('')
const assignItems = ref<InventoryItem[]>([])
const assignItemId = ref('')
const assignLoading = ref(false)

async function openAssignModal() {
  showAssignModal.value = true
  assignInventoryId.value = ''
  assignItems.value = []
  assignItemId.value = ''
  try {
    inventories.value = await inventory.listInventories()
  } catch { /* ignore */ }
}

async function onAssignInventoryChange(invId: string | undefined) {
  assignInventoryId.value = invId ?? ''
  assignItemId.value = ''
  if (!invId) { assignItems.value = []; return }
  try {
    const items = await inventory.listItems(Number(invId))
    assignItems.value = items.filter(i => !i.assignedTo)
  } catch { assignItems.value = [] }
}

async function confirmAssign() {
  if (!assignItemId.value) return
  assignLoading.value = true
  error.value = ''
  try {
    await inventory.assignItem(Number(assignItemId.value), { memberId: memberId.value })
    memberInventory.value = await inventory.memberItems(memberId.value)
    showAssignModal.value = false
  } catch {
    error.value = t('common.error')
  } finally {
    assignLoading.value = false
  }
}

// Unassign inventory item
async function unassignItem(item: MyInventoryItem) {
  error.value = ''
  try {
    await inventory.assignItem(item.id, { memberId: null })
    memberInventory.value = await inventory.memberItems(memberId.value)
  } catch {
    error.value = t('common.error')
  }
}

// Reassign inventory item to another member
const showReassignModal = ref(false)
const reassignItem = ref<MyInventoryItem | null>(null)
const reassignTargetId = ref('')
const reassignLoading = ref(false)

function openReassignModal(item: MyInventoryItem) {
  reassignItem.value = item
  reassignTargetId.value = ''
  showReassignModal.value = true
}

async function confirmReassign() {
  if (!reassignItem.value || !reassignTargetId.value) return
  reassignLoading.value = true
  error.value = ''
  try {
    await inventory.assignItem(reassignItem.value.id, { memberId: Number(reassignTargetId.value) })
    memberInventory.value = await inventory.memberItems(memberId.value)
    showReassignModal.value = false
  } catch {
    error.value = t('common.error')
  } finally {
    reassignLoading.value = false
  }
}

const reassignTargets = computed(() => {
  return allMembers.value
      .filter(m => m.id !== memberId.value)
      .sort((a, b) => memberDisplayName(a).localeCompare(memberDisplayName(b)))
})

// Former member
const showFormerModal = ref(false)
const markingFormer = ref(false)
const formerSuccess = ref(false)

const showDeleteModal = ref(false)
const showDeleteConfirm = ref(false)
const deletingMember = ref(false)
const deleteSuccess = ref(false)

async function confirmDeleteMember() {
  deletingMember.value = true
  error.value = ''
  try {
    await stationMembers.deleteMember(memberId.value)
    deleteSuccess.value = true
    showDeleteConfirm.value = false
    showDeleteModal.value = false
  } catch {
    error.value = t('common.error')
  } finally {
    deletingMember.value = false
  }
}

const formerBlockReasons = computed(() => {
  const reasons: string[] = []
  if (memberInventory.value.length > 0) {
    reasons.push(t('memberDetail.formerBlockInventory', { count: memberInventory.value.length }))
  }
  const forbidden = [Roles.GUARDIAN, Roles.MANAGER, Roles.ADMIN]
  if (memberRoles.value.some(r => forbidden.includes(r as any))) {
    reasons.push(t('memberDetail.formerBlockRole'))
  }
  return reasons
})
const canMarkFormer = computed(() => formerBlockReasons.value.length === 0 && !!member.value)

async function confirmMarkFormer() {
  markingFormer.value = true
  error.value = ''
  try {
    await stationMembers.markFormer(memberId.value)
    formerSuccess.value = true
    showFormerModal.value = false
  } catch {
    error.value = t('common.error')
  } finally {
    markingFormer.value = false
  }
}

function goBack() {
  router.push({ name: 'members-list' })
}

function goToEdit() {
  router.push({ name: 'members-edit', params: { id: memberId.value } })
}

function goToEditManager(mgrId: number) {
  router.push({ name: 'members-edit', params: { id: mgrId } })
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <SecondaryButton @click="goBack">
          <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-2" />
          {{ t('memberDetail.back') }}
        </SecondaryButton>
        <div class="flex items-center gap-2">
          <ErrorButton v-if="canManageMembers() && !formerSuccess && !deleteSuccess" @click="showFormerModal = true">
            <font-awesome-icon :icon="['fas', 'user-slash']" class="mr-1" />
            {{ t('memberDetail.markFormer') }}
          </ErrorButton>
          <ErrorButton v-if="canManageMembers() && !deleteSuccess && !formerSuccess" @click="showDeleteModal = true">
            <font-awesome-icon :icon="['fas', 'trash']" class="mr-1" />
            {{ t('memberDetail.deleteData') }}
          </ErrorButton>
          <PrimaryButton @click="goToEdit">
            <font-awesome-icon :icon="['fas', 'pen']" class="mr-2" />
            {{ t('memberDetail.edit') }}
          </PrimaryButton>
        </div>
      </div>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && member">
        <SectionHeader>{{ memberDisplayName(member) }}</SectionHeader>
        <p v-if="member.email" class="text-sm text-(--text-muted)">{{ member.email }}</p>

        <!-- Profile fields -->
        <NeutralContainer class="space-y-3">
          <h3 class="text-sm font-semibold">{{ t('memberDetail.fields') }}</h3>
          <div v-if="applicableFields.length === 0" class="text-(--text-muted) text-sm py-2">
            {{ t('memberDetail.noFields') }}
          </div>
          <div class="grid gap-2 sm:grid-cols-2">
            <div v-for="field in applicableFields" :key="field.id" class="text-sm">
              <span class="text-(--text-muted)">{{ field.name }}:</span>
              <span class="ml-1 font-medium"><FieldValueDisplay :value="getFieldValue(field.id)" :field-type="field.fieldType"/></span>
            </div>
          </div>
        </NeutralContainer>

        <!-- Managers (only shown for MEMBER role, not for GUARDIAN) -->
        <NeutralContainer v-if="memberRoles.includes(Roles.MEMBER) && !memberRoles.includes(Roles.GUARDIAN) && !hasTeamRole(memberRoles)" class="space-y-4">
          <div class="flex items-center justify-between">
            <h3 class="text-sm font-semibold">{{ t('memberDetail.managers') }}</h3>
            <div class="flex items-center gap-2">
              <SecondaryButton class="text-sm" @click="showLinkManager = !showLinkManager">
                <font-awesome-icon :icon="['fas', 'link']" class="mr-1" />
                {{ t('memberDetail.linkManager') }}
              </SecondaryButton>
              <SecondaryButton class="text-sm" @click="showCreateManager = !showCreateManager">
                <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
                {{ t('memberDetail.createManager') }}
              </SecondaryButton>
            </div>
          </div>

          <div v-if="managers.length === 0" class="text-(--text-muted) text-sm py-2">
            {{ t('memberDetail.noManagers') }}
          </div>

          <div class="space-y-3">
            <div v-for="mgr in managers" :key="mgr.id" class="rounded-lg px-4 py-3 bg-bg-light-accent/30 dark:bg-bg-dark-accent/30 space-y-2">
              <div class="flex items-center justify-between">
                <div>
                  <span class="font-semibold">{{ memberDisplayName(mgr) }}</span>
                  <span v-if="mgr.email" class="ml-2 text-xs text-(--text-muted)">{{ mgr.email }}</span>
                </div>
                <div class="flex items-center gap-2">
                  <EditButton @click="goToEditManager(mgr.id)" />
                  <DeleteButton @click="removeManager(mgr.id)" />
                </div>
              </div>
              <div v-if="getManagerFields(mgr.id).length > 0" class="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
                <div v-for="field in getManagerFields(mgr.id)" :key="field.id" class="text-sm">
                  <span class="text-(--text-muted)">{{ field.name }}:</span>
                  <span class="ml-1 font-medium"><FieldValueDisplay :value="getManagerFieldValue(mgr.id, field.id)" :field-type="field.fieldType"/></span>
                </div>
              </div>
            </div>
          </div>

          <!-- Link existing manager -->
          <div v-if="showLinkManager" class="space-y-2 pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
            <label class="block text-sm font-medium">{{ t('memberDetail.selectManager') }}</label>
            <div class="flex gap-2">
              <SelectInput v-model="selectedManagerId" class="flex-1">
                <option value="" disabled>{{ t('memberDetail.selectManagerPlaceholder') }}</option>
                <option v-for="m in availableManagers" :key="m.id" :value="String(m.id)">{{ memberDisplayName(m) }}</option>
              </SelectInput>
              <PrimaryButton :disabled="!selectedManagerId" @click="linkManager">
                {{ t('memberDetail.assign') }}
              </PrimaryButton>
            </div>
          </div>

          <!-- Create new manager -->
          <div v-if="showCreateManager" class="space-y-3 pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
            <label class="block text-sm font-medium">{{ t('memberDetail.createManagerTitle') }}</label>
            <div class="grid gap-3 sm:grid-cols-3">
              <TextInput v-model="newMgrFirstName" :placeholder="t('memberDetail.firstName')" />
              <TextInput v-model="newMgrLastName" :placeholder="t('memberDetail.lastName')" />
              <TextInput v-model="newMgrEmail" :placeholder="t('memberDetail.email')" />
            </div>
            <SecondaryButton :disabled="!newMgrFirstName || !newMgrLastName || !newMgrEmail || creatingManager" @click="createNewManager">
              <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
              {{ creatingManager ? t('common.loading') : t('memberDetail.createManagerSubmit') }}
            </SecondaryButton>
          </div>
        </NeutralContainer>

        <!-- Inventory -->
        <NeutralContainer v-if="inventoryEnabled && (memberInventory.length > 0 || showInventoryManagement)" class="space-y-3">
          <div class="flex items-center justify-between">
            <SubHeader>{{ t('memberDetail.inventory') }}</SubHeader>
            <PrimaryButton v-if="showInventoryManagement" class="text-sm" @click="openAssignModal">
              <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
              {{ t('memberDetail.assignItem') }}
            </PrimaryButton>
          </div>
          <div v-if="memberInventory.length === 0" class="text-sm text-(--text-muted)">
            {{ t('memberDetail.noInventory') }}
          </div>
          <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
            <InventoryItemCard
                v-for="item in memberInventory"
                :key="item.id"
                :item="item"
                :exchange="itemExchange(item.id) ?? null"
                :show-exchange-button="canManageInventory()"
                :show-unassign-button="showInventoryManagement"
                :show-reassign-button="showInventoryManagement"
                :show-inventory-name="true"
                @request-exchange="openExchangeModal"
                @unassign="unassignItem"
                @reassign="openReassignModal"
            />
          </div>
        </NeutralContainer>

        <!-- Change History -->
        <ChangeHistory
          v-if="showChangeHistory"
          :member-id="memberId"
          :changes="changes"
          :current-member-id="currentMemberId"
          @reload="loadChanges"
        />
      </template>

      <Alert v-if="formerSuccess" variant="success">{{ t('memberDetail.formerSuccess') }}</Alert>
      <Alert v-if="deleteSuccess" variant="success">{{ t('memberDetail.deleteSuccess') }}</Alert>

      <!-- Former confirmation modal -->
      <Modal v-model="showFormerModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('memberDetail.markFormerTitle') }}</SectionHeader>
          <template v-if="canMarkFormer">
            <p class="text-sm">{{ t('memberDetail.markFormerConfirm', { name: member ? memberDisplayName(member) : '' }) }}</p>
            <p class="text-xs text-(--text-muted)">{{ t('memberDetail.markFormerHint') }}</p>
            <div class="flex justify-end gap-2">
              <SecondaryButton @click="showFormerModal = false">{{ t('common.cancel') }}</SecondaryButton>
              <ErrorButton :disabled="markingFormer" @click="confirmMarkFormer">
                {{ markingFormer ? t('common.loading') : t('memberDetail.markFormer') }}
              </ErrorButton>
            </div>
          </template>
          <template v-else>
            <p class="text-sm">{{ t('memberDetail.formerBlocked') }}</p>
            <ul class="list-disc list-inside text-sm text-error space-y-1">
              <li v-for="(reason, i) in formerBlockReasons" :key="i">{{ reason }}</li>
            </ul>
            <div class="flex justify-end">
              <SecondaryButton @click="showFormerModal = false">{{ t('common.close') }}</SecondaryButton>
            </div>
          </template>
        </div>
      </Modal>

      <!-- Delete member modal (first step) -->
      <Modal v-model="showDeleteModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('memberDetail.deleteTitle') }}</SectionHeader>
          <p class="text-sm">{{ t('memberDetail.deleteText', { name: member ? memberDisplayName(member) : '' }) }}</p>
          <p class="text-xs text-(--text-muted)">{{ t('memberDetail.deleteHint') }}</p>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showDeleteModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <ErrorButton @click="showDeleteModal = false; showDeleteConfirm = true">
              {{ t('memberDetail.deleteConfirmAction') }}
            </ErrorButton>
          </div>
        </div>
      </Modal>

      <!-- Delete member modal (second confirmation) -->
      <Modal v-model="showDeleteConfirm">
        <div class="space-y-4">
          <SectionHeader>{{ t('memberDetail.deleteConfirmTitle') }}</SectionHeader>
          <p class="text-sm font-semibold text-error">{{ t('memberDetail.deleteConfirmText') }}</p>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showDeleteConfirm = false">{{ t('common.cancel') }}</SecondaryButton>
            <ErrorButton :disabled="deletingMember" @click="confirmDeleteMember">
              {{ deletingMember ? t('common.loading') : t('memberDetail.deleteConfirmFinal') }}
            </ErrorButton>
          </div>
        </div>
      </Modal>

      <!-- Assign item modal -->
      <Modal v-model="showAssignModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('memberDetail.assignItem') }}</SectionHeader>
          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('memberDetail.selectInventory') }}</label>
            <SelectInput :model-value="assignInventoryId" @update:model-value="onAssignInventoryChange">
              <option value="" disabled>{{ t('memberDetail.selectInventoryPlaceholder') }}</option>
              <option v-for="inv in inventories" :key="inv.id" :value="String(inv.id)">{{ inv.name }}</option>
            </SelectInput>
          </div>
          <div v-if="assignInventoryId" class="space-y-1">
            <label class="block text-sm font-medium">{{ t('memberDetail.selectItem') }}</label>
            <SelectInput v-model="assignItemId">
              <option value="" disabled>{{ t('memberDetail.selectItemPlaceholder') }}</option>
              <option v-for="item in assignItems" :key="item.id" :value="String(item.id)">
                {{ item.name ?? item.internalId ?? `#${item.id}` }}
              </option>
            </SelectInput>
            <p v-if="assignItems.length === 0" class="text-xs text-(--text-muted)">{{ t('memberDetail.noAvailableItems') }}</p>
          </div>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showAssignModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="assignLoading || !assignItemId" @click="confirmAssign">
              {{ assignLoading ? t('common.loading') : t('memberDetail.assignItem') }}
            </PrimaryButton>
          </div>
        </div>
      </Modal>

      <!-- Reassign item modal -->
      <Modal v-model="showReassignModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('memberDetail.reassignItem') }}</SectionHeader>
          <p v-if="reassignItem" class="text-sm">
            {{ reassignItem.inventoryName }} — {{ reassignItem.name }}
            <SizeBadge>{{ reassignItem.sizeName ?? t('common.unisize') }}</SizeBadge>
          </p>
          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('memberDetail.selectTargetMember') }}</label>
            <SelectInput v-model="reassignTargetId">
              <option value="" disabled>{{ t('memberDetail.selectTargetPlaceholder') }}</option>
              <option v-for="m in reassignTargets" :key="m.id" :value="String(m.id)">{{ memberDisplayName(m) }}</option>
            </SelectInput>
          </div>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showReassignModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="reassignLoading || !reassignTargetId" @click="confirmReassign">
              {{ reassignLoading ? t('common.loading') : t('memberDetail.reassignItem') }}
            </PrimaryButton>
          </div>
        </div>
      </Modal>

      <!-- Exchange modal -->
      <Modal v-model="showExchangeModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('memberDetail.requestExchange') }}</SectionHeader>
          <template v-if="exchangeSuccess">
            <Alert variant="success">{{ t('profile.exchangeCreated') }}</Alert>
            <div class="flex justify-end">
              <SecondaryButton @click="showExchangeModal = false">{{ t('common.close') }}</SecondaryButton>
            </div>
          </template>
          <template v-else>
            <p v-if="exchangeItem" class="text-sm">
              {{ exchangeItem.inventoryName }} — {{ exchangeItem.name }}
              <SizeBadge>{{ exchangeItem.sizeName ?? t('common.unisize') }}</SizeBadge>
            </p>
            <div v-if="exchangeSizes.length > 0" class="space-y-1">
              <label class="block text-sm font-medium">{{ t('exchanges.newSize') }}</label>
              <SelectInput v-model="exchangeNewSizeId">
                <option value="" disabled>{{ t('exchanges.selectNewSize') }}</option>
                <option v-for="size in exchangeSizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
              </SelectInput>
            </div>
            <div class="space-y-1">
              <label class="block text-sm font-medium">{{ t('exchanges.reason') }}</label>
              <TextAreaInput v-model="exchangeReason" :placeholder="t('exchanges.reasonPlaceholder')" :rows="3" />
            </div>
            <div class="flex justify-end gap-2">
              <SecondaryButton @click="showExchangeModal = false">{{ t('common.cancel') }}</SecondaryButton>
              <PrimaryButton :disabled="exchangeSaving || !exchangeReason.trim() || (exchangeSizes.length > 0 && !exchangeNewSizeId)" @click="submitExchange">
                {{ exchangeSaving ? t('common.loading') : t('exchanges.submit') }}
              </PrimaryButton>
            </div>
          </template>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
