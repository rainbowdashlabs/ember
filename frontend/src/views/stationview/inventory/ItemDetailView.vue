/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import NoteEditor from '@/components/comment/NoteEditor.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import {inventory, stationMembers} from '@/api'
import type {InventoryItem, InventoryItemHistory, InventorySize, StationMember} from '@/api/types'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {hasPermission} = useSession()
const canEdit = computed(() => hasPermission(StationPermission.INVENTORY_EDIT))

const itemId = computed(() => Number(route.params.id))
const item = ref<InventoryItem | null>(null)
const historyEntries = ref<InventoryItemHistory[]>([])
const sizes = ref<InventorySize[]>([])
const members = ref<StationMember[]>([])
const loading = ref(true)
const error = ref('')
const success = ref('')

const isManager = computed(() => hasPermission('INVENTORY_MANAGER') || hasPermission('STATION_ADMINISTRATOR'))
const canEditItem = computed(() => canEdit.value || isManager.value)
const assignedMember = computed(() => {
  if (!item.value?.assignedTo) return null
  return members.value.find(m => m.id === item.value!.assignedTo) ?? null
})
const assignedMemberIdentity = computed(() => assignedMember.value?.identity ?? null)

// -- Edit state --
const editing = ref(false)
const editName = ref('')
const editInternalId = ref('')
const editSizeId = ref<string>('')
const saving = ref(false)

function startEdit() {
  if (!item.value) return
  editName.value = item.value.name ?? ''
  editInternalId.value = item.value.internalId ?? ''
  editSizeId.value = item.value.sizeId != null ? String(item.value.sizeId) : ''
  editing.value = true
}

async function saveEdit() {
  if (!item.value) return
  saving.value = true
  error.value = ''
  try {
    item.value = await inventory.updateItem(itemId.value, {
      name: editName.value,
      internalId: editInternalId.value,
      sizeId: editSizeId.value ? Number(editSizeId.value) : undefined,
    })
    editing.value = false
    success.value = t('itemDetail.saved')
    setTimeout(() => success.value = '', 3000)
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}

// -- Assign --
const showAssignModal = ref(false)
const assignMemberId = ref('')

async function doAssign() {
  if (!assignMemberId.value) return
  error.value = ''
  try {
    const member = members.value.find(m => m.id === Number(assignMemberId.value))
    item.value = await inventory.assignItem(itemId.value, {
      memberId: Number(assignMemberId.value),
      memberName: member?.name ?? '',
    })
    showAssignModal.value = false
    assignMemberId.value = ''
    historyEntries.value = await inventory.getItemHistory(itemId.value)
    success.value = t('itemDetail.assigned')
    setTimeout(() => success.value = '', 3000)
  } catch {
    error.value = t('common.error')
  }
}

async function doUnassign() {
  error.value = ''
  try {
    item.value = await inventory.assignItem(itemId.value, {memberId: null})
    historyEntries.value = await inventory.getItemHistory(itemId.value)
    success.value = t('itemDetail.unassigned')
    setTimeout(() => success.value = '', 3000)
  } catch {
    error.value = t('common.error')
  }
}

// -- Lost/Found --
async function doMarkLost() {
  error.value = ''
  try {
    item.value = await inventory.markLost(itemId.value)
    success.value = t('itemDetail.markedLost')
    setTimeout(() => success.value = '', 3000)
  } catch {
    error.value = t('common.error')
  }
}

async function doMarkFound() {
  error.value = ''
  try {
    item.value = await inventory.markFound(itemId.value)
    success.value = t('itemDetail.markedFound')
    setTimeout(() => success.value = '', 3000)
  } catch {
    error.value = t('common.error')
  }
}

function formatDate(iso?: string | null): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('de-DE', {day: '2-digit', month: '2-digit', year: 'numeric'})
}

function sizeLabel(sizeId?: number | null): string {
  if (sizeId == null) return '—'
  return sizes.value.find(s => s.id === sizeId)?.label ?? String(sizeId)
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [i, h] = await Promise.all([
      inventory.getItem(itemId.value),
      inventory.getItemHistory(itemId.value),
    ])
    item.value = i
    historyEntries.value = h
    const [s, m] = await Promise.all([
      inventory.listSizes(i.inventoryId),
      stationMembers.listMembers(),
    ])
    sizes.value = s
    members.value = m
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex items-center gap-2">
        <SecondaryButton :icon="['fas', 'arrow-left']" @click="router.back()"/>
        <SectionHeader>{{ t('itemDetail.title') }}</SectionHeader>
      </div>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <template v-if="!loading && item">
        <!-- Item metadata -->
        <NeutralContainer class="space-y-3">
          <div class="flex items-center justify-between">
            <SubHeader>{{ item.name }}</SubHeader>
            <SecondaryButton v-if="canEditItem && !editing" :icon="['fas', 'pen']" @click="startEdit">
              {{ t('itemDetail.edit') }}
            </SecondaryButton>
          </div>

          <!-- Edit mode -->
          <template v-if="editing">
            <div class="grid gap-4 sm:grid-cols-3">
              <div class="space-y-1">
                <FieldLabel>{{ t('itemDetail.name') }}</FieldLabel>
                <TextInput v-model="editName"/>
              </div>
              <div class="space-y-1">
                <FieldLabel>{{ t('itemDetail.internalId') }}</FieldLabel>
                <TextInput v-model="editInternalId"/>
              </div>
              <div v-if="sizes.length > 0" class="space-y-1">
                <FieldLabel>{{ t('itemDetail.size') }}</FieldLabel>
                <SelectInput v-model="editSizeId">
                  <option value="">—</option>
                  <option v-for="s in sizes" :key="s.id" :value="String(s.id)">{{ s.label }}</option>
                </SelectInput>
              </div>
            </div>
            <div class="flex gap-2">
              <PrimaryButton :disabled="saving" @click="saveEdit">{{ t('memberEdit.save') }}</PrimaryButton>
              <SecondaryButton @click="editing = false">{{ t('common.cancel') }}</SecondaryButton>
            </div>
          </template>

          <!-- Display mode -->
          <div v-else class="grid grid-cols-2 sm:grid-cols-4 gap-4 text-sm">
            <div v-if="item.internalId">
              <FieldLabel class="text-xs">{{ t('itemDetail.internalId') }}</FieldLabel>
              <span class="font-mono">{{ item.internalId }}</span>
            </div>
            <div v-if="sizes.length > 0">
              <FieldLabel class="text-xs">{{ t('itemDetail.size') }}</FieldLabel>
              <span>{{ sizeLabel(item.sizeId) }}</span>
            </div>
            <div>
              <FieldLabel class="text-xs">{{ t('itemDetail.status') }}</FieldLabel>
              <ErrorBadge v-if="item.lostAt">{{ t('profile.lostSince') }} {{ formatDate(item.lostAt) }}</ErrorBadge>
              <SuccessBadge v-else-if="item.assignedTo">{{ t('itemDetail.statusAssigned') }}</SuccessBadge>
              <InfoBadge v-else>{{ t('itemDetail.available') }}</InfoBadge>
            </div>
            <div v-if="assignedMember">
              <FieldLabel class="text-xs">{{ t('itemDetail.assignedTo') }}</FieldLabel>
              <MemberName :identity="assignedMemberIdentity"/>
            </div>
          </div>
        </NeutralContainer>

        <!-- Actions -->
        <NeutralContainer v-if="canEditItem" class="space-y-3">
          <SubHeader>{{ t('itemDetail.actions') }}</SubHeader>
          <div class="flex flex-wrap gap-2">
            <PrimaryButton :icon="['fas', 'user-plus']" @click="showAssignModal = true">
              {{ t('itemDetail.assign') }}
            </PrimaryButton>
            <SecondaryButton v-if="item.assignedTo" :icon="['fas', 'user-minus']" @click="doUnassign">
              {{ t('itemDetail.unassign') }}
            </SecondaryButton>
            <ErrorButton v-if="!item.lostAt" :icon="['fas', 'triangle-exclamation']" @click="doMarkLost">
              {{ t('itemDetail.markLost') }}
            </ErrorButton>
            <SuccessButton v-if="item.lostAt" :icon="['fas', 'check']" @click="doMarkFound">
              {{ t('itemDetail.markFound') }}
            </SuccessButton>
          </div>
        </NeutralContainer>

        <!-- Assignment history -->
        <NeutralContainer class="space-y-3">
          <SubHeader>{{ t('itemDetail.history') }}</SubHeader>
          <div v-if="historyEntries.length === 0" class="text-sm text-(--text-muted)">{{ t('itemDetail.noHistory') }}</div>
          <table v-else class="w-full text-sm">
            <thead>
              <tr class="text-xs text-(--text-muted) border-b border-(--border)">
                <th class="p-2 text-left">{{ t('itemDetail.member') }}</th>
                <th class="p-2 text-left">{{ t('itemDetail.givenOut') }}</th>
                <th class="p-2 text-left">{{ t('itemDetail.returned') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="h in historyEntries" :key="h.id" class="border-b border-(--border) last:border-0">
                <td class="p-2">{{ h.memberName || '—' }}</td>
                <td class="p-2 text-(--text-muted)">{{ formatDate(h.givenOut) }}</td>
                <td class="p-2 text-(--text-muted)">{{ h.returned ? formatDate(h.returned) : t('itemDetail.current') }}</td>
              </tr>
            </tbody>
          </table>
        </NeutralContainer>

        <!-- Manager notes -->
        <NeutralContainer v-if="isManager">
          <NoteEditor :entity-type="'ITEM'" :entity-id="itemId"/>
        </NeutralContainer>
      </template>

      <!-- Assign modal -->
      <Modal v-model="showAssignModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('itemDetail.assignTitle') }}</SectionHeader>
          <SelectInput v-model="assignMemberId">
            <option value="">{{ t('itemDetail.selectMember') }}</option>
            <option v-for="m in members" :key="m.id" :value="String(m.id)">
              {{ m.name || m.email }}
            </option>
          </SelectInput>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showAssignModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="!assignMemberId" @click="doAssign">{{ t('itemDetail.assign') }}</PrimaryButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
