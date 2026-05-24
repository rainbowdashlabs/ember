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
import ErrorButton from '@/components/button/ErrorButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldValueDisplay from '@/components/display/FieldValueDisplay.vue'
import ChangeHistory from './detailview/ChangeHistory.vue'
import ManagerSection from './detailview/ManagerSection.vue'
import InventorySection from './detailview/InventorySection.vue'
import DetailModals from './detailview/DetailModals.vue'
import type { ProfileField, ProfileFieldChange, StationMember, Inventory, InventoryItem } from '@/api/types'
import { Roles, hasTeamRole, ExchangeStatus, StationModules } from '@/api/types'
import type { MyInventoryItem } from '@/api/inventory'
import type { ExchangeRequestEntry } from '@/api/types'
import { profileFields, profileFieldChanges, stationMembers, members, inventory, exchanges } from '@/api'
import { useSession } from '@/composables/useSession'
import MutedText from '@/components/typography/MutedText.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

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
const formerSuccess = ref(false)
const deleteSuccess = ref(false)
const markingFormer = ref(false)
const deletingMember = ref(false)

// Modal data
const inventories = ref<Inventory[]>([])
const assignItems = ref<InventoryItem[]>([])
const exchangeSizes = ref<import('@/api/types').InventorySize[]>([])

const modalsRef = ref<InstanceType<typeof DetailModals> | null>(null)

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

const showManagerSection = computed(() =>
  memberRoles.value.includes(Roles.MEMBER) && !memberRoles.value.includes(Roles.GUARDIAN) && !hasTeamRole(memberRoles.value)
)

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

function getManagerFields(mgrId: number): ProfileField[] {
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

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [allFields, allMems, roles, profileValues, mgrs] = await Promise.all([
      profileFields.listFields(),
      stationMembers.listMembers(),
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
    for (const v of profileValues) { map.set(v.fieldId, v.value ?? '') }
    values.value = map
    await loadManagerDetails(mgrs)
    if (showChangeHistory.value) {
      try { changes.value = await profileFieldChanges.getChanges(memberId.value) } catch { /* ignore */ }
    }
    try { memberInventory.value = await inventory.memberItems(memberId.value) } catch { memberInventory.value = [] }
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

async function handleLinkManager(managerId: number) {
  error.value = ''
  try {
    const currentIds = managers.value.map(m => m.id)
    await stationMembers.setManagers(memberId.value, { managerIds: [...currentIds, managerId] })
    managers.value = await stationMembers.getManagers(memberId.value)
    await loadManagerDetails(managers.value)
  } catch { error.value = t('common.error') }
}

async function handleRemoveManager(mgrId: number) {
  error.value = ''
  try {
    const newIds = managers.value.filter(m => m.id !== mgrId).map(m => m.id)
    await stationMembers.setManagers(memberId.value, { managerIds: newIds })
    managers.value = await stationMembers.getManagers(memberId.value)
  } catch { error.value = t('common.error') }
}

async function handleCreateManager(data: { firstName: string; lastName: string; email: string }) {
  error.value = ''
  try {
    const invited = await members.invite({ email: data.email, firstName: data.firstName, lastName: data.lastName })
    const updatedMembers = await stationMembers.listMembers()
    const newMember = updatedMembers.find(m => m.accountId === invited.id)
    if (newMember) {
      const currentIds = managers.value.map(m => m.id)
      await stationMembers.setManagers(memberId.value, { managerIds: [...currentIds, newMember.id] })
      const allRoles = await stationMembers.listAllRoles()
      const mgrRoleNames: readonly string[] = [Roles.LOGIN, Roles.GUARDIAN]
      const mgrRoleIds = allRoles.filter(r => mgrRoleNames.includes(r.role)).map(r => r.id)
      await stationMembers.setRoles(newMember.id, { roleIds: mgrRoleIds })
      managers.value = await stationMembers.getManagers(memberId.value)
      await loadManagerDetails(managers.value)
      allMembers.value = updatedMembers
    }
  } catch { error.value = t('common.error') }
}

async function handleMarkFormer() {
  markingFormer.value = true
  error.value = ''
  try {
    await stationMembers.markFormer(memberId.value)
    formerSuccess.value = true
  } catch { error.value = t('common.error') }
  finally { markingFormer.value = false }
}

async function handleDeleteMember() {
  deletingMember.value = true
  error.value = ''
  try {
    await stationMembers.deleteMember(memberId.value)
    deleteSuccess.value = true
  } catch { error.value = t('common.error') }
  finally { deletingMember.value = false }
}

async function handleAssignItem(itemId: number) {
  error.value = ''
  try {
    await inventory.assignItem(itemId, { memberId: memberId.value })
    memberInventory.value = await inventory.memberItems(memberId.value)
  } catch { error.value = t('common.error') }
}

async function handleUnassignItem(item: MyInventoryItem) {
  error.value = ''
  try {
    await inventory.assignItem(item.id, { memberId: null })
    memberInventory.value = await inventory.memberItems(memberId.value)
  } catch { error.value = t('common.error') }
}

async function handleReassignItem(itemId: number, targetMemberId: number) {
  error.value = ''
  try {
    await inventory.assignItem(itemId, { memberId: targetMemberId })
    memberInventory.value = await inventory.memberItems(memberId.value)
  } catch { error.value = t('common.error') }
}

async function handleSubmitExchange(data: { item: MyInventoryItem; newSizeId?: number; reason: string }) {
  error.value = ''
  try {
    await exchanges.createExchange({
      memberId: memberId.value,
      itemId: data.item.id,
      inventoryId: data.item.inventoryId,
      oldSizeId: data.item.sizeId ?? undefined,
      newSizeId: data.newSizeId,
      reason: data.reason,
    })
  } catch { error.value = t('common.error') }
}

async function handleLoadExchangeSizes(inventoryId: number) {
  try { exchangeSizes.value = await inventory.listSizes(inventoryId) } catch { exchangeSizes.value = [] }
}

async function handleLoadInventories() {
  try { inventories.value = await inventory.listInventories() } catch { /* ignore */ }
}

async function handleAssignInventoryChange(inventoryId: string) {
  try {
    const items = await inventory.listItems(Number(inventoryId))
    assignItems.value = items.filter(i => !i.assignedTo)
  } catch { assignItems.value = [] }
}

async function loadChanges() {
  try { changes.value = await profileFieldChanges.getChanges(memberId.value) } catch { /* ignore */ }
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <SecondaryButton :icon="['fas', 'chevron-left']" @click="router.push({ name: 'members-list' })">
          {{ t('memberDetail.back') }}
        </SecondaryButton>
        <div class="flex items-center gap-2">
          <ErrorButton :icon="['fas', 'user-slash']" v-if="canManageMembers() && !formerSuccess && !deleteSuccess" @click="modalsRef?.openFormerModal()">
            {{ t('memberDetail.markFormer') }}
          </ErrorButton>
          <ErrorButton :icon="['fas', 'trash']" v-if="canManageMembers() && !deleteSuccess && !formerSuccess" @click="modalsRef?.openDeleteModal()">
            {{ t('memberDetail.deleteData') }}
          </ErrorButton>
          <PrimaryButton :icon="['fas', 'pen']" @click="router.push({ name: 'members-edit', params: { id: memberId } })">
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
          <SubHeader class="text-sm">{{ t('memberDetail.fields') }}</SubHeader>
          <MutedText tag="div" size="sm" class="py-2" v-if="applicableFields.length === 0">
            {{ t('memberDetail.noFields') }}
          </MutedText>
          <div class="grid gap-2 sm:grid-cols-2">
            <div v-for="field in applicableFields" :key="field.id" class="text-sm">
              <span class="text-(--text-muted)">{{ field.name }}:</span>
              <span class="ml-1 font-medium"><FieldValueDisplay :value="getFieldValue(field.id)" :field-type="field.fieldType"/></span>
            </div>
          </div>
        </NeutralContainer>

        <!-- Managers -->
        <ManagerSection
          v-if="showManagerSection"
          :managers="managers"
          :available-managers="availableManagers"
          :manager-values="managerValues"
          :manager-roles="managerRoles"
          :fields="fields"
          :member-display-name-fn="memberDisplayName"
          :get-manager-fields-fn="getManagerFields"
          :get-manager-field-value-fn="getManagerFieldValue"
          @link-manager="handleLinkManager"
          @remove-manager="handleRemoveManager"
          @create-manager="handleCreateManager"
          @edit-manager="(id) => router.push({ name: 'members-edit', params: { id } })"
        />

        <!-- Inventory -->
        <InventorySection
          v-if="inventoryEnabled && (memberInventory.length > 0 || showInventoryManagement)"
          :member-inventory="memberInventory"
          :member-exchanges="memberExchanges"
          :show-inventory-management="showInventoryManagement"
          :can-manage-inventory="canManageInventory()"
          @assign-item="modalsRef?.openAssignModal()"
          @request-exchange="modalsRef?.openExchangeModal($event)"
          @unassign="handleUnassignItem"
          @reassign="modalsRef?.openReassignModal($event)"
        />

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

      <DetailModals
        ref="modalsRef"
        :member-display-name="member ? memberDisplayName(member) : ''"
        :can-mark-former="canMarkFormer"
        :former-block-reasons="formerBlockReasons"
        :marking-former="markingFormer"
        :deleting-member="deletingMember"
        :all-members="allMembers"
        :member-id="memberId"
        :member-display-name-fn="memberDisplayName"
        :inventories="inventories"
        :assign-items="assignItems"
        :exchange-sizes="exchangeSizes"
        @mark-former="handleMarkFormer"
        @delete-member="handleDeleteMember"
        @assign-item="handleAssignItem"
        @assign-inventory-change="handleAssignInventoryChange"
        @reassign-item="handleReassignItem"
        @submit-exchange="handleSubmitExchange"
        @load-exchange-sizes="handleLoadExchangeSizes"
        @load-inventories="handleLoadInventories"
      />
    </div>
  </ViewContent>
</template>
