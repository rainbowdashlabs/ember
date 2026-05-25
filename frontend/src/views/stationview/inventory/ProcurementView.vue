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
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import type {
  ProcurementEntry,
  Inventory,
  InventorySize,
  StationMember,
} from '@/api/types'
import { procurement, inventory, stationMembers } from '@/api'
import MemberName from '@/components/avatar/MemberName.vue'

const { t } = useI18n()

const entries = ref<ProcurementEntry[]>([])
const inventories = ref<Inventory[]>([])
const members = ref<StationMember[]>([])
const loading = ref(true)
const error = ref('')

// Create modal
const showCreateModal = ref(false)
const createInventoryId = ref<string>('')
const createMemberId = ref<string>('')
const createSizeId = ref<string>('')
const createNotes = ref('')
const createSaving = ref(false)
const createSuccess = ref(false)
const availableSizes = ref<InventorySize[]>([])

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [e, inv, m] = await Promise.all([
      procurement.listProcurement(),
      inventory.listInventories(),
      stationMembers.listMembers(),
    ])
    entries.value = e
    inventories.value = inv
    members.value = m
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function onInventorySelected() {
  availableSizes.value = []
  createSizeId.value = ''
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
  createMemberId.value = ''
  createSizeId.value = ''
  createNotes.value = ''
  createSuccess.value = false
  availableSizes.value = []
  showCreateModal.value = true
}

async function submitCreate() {
  createSaving.value = true
  error.value = ''
  try {
    await procurement.createProcurement({
      inventoryId: Number(createInventoryId.value),
      memberId: Number(createMemberId.value),
      sizeId: createSizeId.value ? Number(createSizeId.value) : undefined,
      notes: createNotes.value || undefined,
    })
    createSuccess.value = true
    entries.value = await procurement.listProcurement()
  } catch {
    error.value = t('common.error')
  } finally {
    createSaving.value = false
  }
}

async function fulfillEntry(id: number) {
  error.value = ''
  try {
    await procurement.fulfill(id)
    entries.value = await procurement.listProcurement()
  } catch {
    error.value = t('common.error')
  }
}

async function deleteEntry(id: number) {
  error.value = ''
  try {
    await procurement.deleteProcurement(id)
    entries.value = await procurement.listProcurement()
  } catch {
    error.value = t('common.error')
  }
}

function memberDisplayName(member: StationMember): string {
  if (member.name && member.name.trim()) return member.name
  return member.email ?? `#${member.id}`
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('de-DE', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  })
}

const canCreate = computed(() => !!createInventoryId.value && !!createMemberId.value)

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <SectionHeader>{{ t('procurement.title') }}</SectionHeader>
        <PrimaryButton :icon="['fas', 'plus']" @click="openCreateModal">
          {{ t('procurement.create') }}
        </PrimaryButton>
      </div>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <EmptyState v-if="!loading && entries.length === 0">{{ t('procurement.empty') }}</EmptyState>

      <div v-if="!loading" class="space-y-3">
        <NeutralContainer v-for="entry in entries" :key="entry.id">
          <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
            <div class="space-y-1">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-medium">{{ entry.inventoryName }}</span>
                <SizeBadge>{{ entry.sizeLabel || t('common.unisize') }}</SizeBadge>
                <SuccessBadge v-if="entry.fulfilledAt">{{ t('procurement.fulfilled') }}</SuccessBadge>
                <ErrorBadge v-else>{{ t('procurement.open') }}</ErrorBadge>
              </div>
              <div class="text-sm text-(--text-muted)">
                <MemberName :name="entry.memberName" :member-id="entry.memberId"/> &mdash; {{ formatDate(entry.requestedAt) }}
              </div>
              <div v-if="entry.fulfilledAt" class="text-xs text-(--text-muted)">
                {{ t('procurement.fulfilledAt') }}: {{ formatDate(entry.fulfilledAt) }}
              </div>
              <div v-if="entry.notes" class="text-sm">{{ entry.notes }}</div>
            </div>

            <div class="flex items-center gap-2 shrink-0">
              <PrimaryButton :icon="['fas', 'check']" v-if="!entry.fulfilledAt" @click="fulfillEntry(entry.id)">
                {{ t('procurement.markFulfilled') }}
              </PrimaryButton>
              <DeleteButton @click="deleteEntry(entry.id)" />
            </div>
          </div>
        </NeutralContainer>
      </div>

      <!-- Create modal -->
      <Modal v-model="showCreateModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('procurement.createTitle') }}</SectionHeader>

          <template v-if="createSuccess">
            <Alert variant="success">{{ t('inventory.check.procurementCreated') }}</Alert>
            <div class="flex justify-end">
              <SecondaryButton @click="showCreateModal = false">{{ t('common.close') }}</SecondaryButton>
            </div>
          </template>
          <template v-else>
            <div class="space-y-1">
              <FieldLabel>{{ t('procurement.member') }}</FieldLabel>
              <SelectInput v-model="createMemberId">
                <option value="" disabled>{{ t('procurement.selectMember') }}</option>
                <option v-for="m in members" :key="m.id" :value="String(m.id)">{{ memberDisplayName(m) }}</option>
              </SelectInput>
            </div>

            <div class="space-y-1">
              <FieldLabel>{{ t('procurement.inventory') }}</FieldLabel>
              <SelectInput v-model="createInventoryId" @change="onInventorySelected">
                <option value="" disabled>{{ t('procurement.selectInventory') }}</option>
                <option v-for="inv in inventories" :key="inv.id" :value="String(inv.id)">{{ inv.name }}</option>
              </SelectInput>
            </div>

            <div v-if="availableSizes.length > 0" class="space-y-1">
              <FieldLabel>{{ t('procurement.size') }}</FieldLabel>
              <SelectInput v-model="createSizeId">
                <option value="">{{ t('procurement.noSize') }}</option>
                <option v-for="size in availableSizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
              </SelectInput>
            </div>

            <div class="space-y-1">
              <FieldLabel>{{ t('procurement.notes') }}</FieldLabel>
              <TextAreaInput v-model="createNotes" :placeholder="t('procurement.notesPlaceholder')" />
            </div>

            <div class="flex justify-end gap-3">
              <SecondaryButton @click="showCreateModal = false">{{ t('common.cancel') }}</SecondaryButton>
              <PrimaryButton :disabled="createSaving || !canCreate" @click="submitCreate">
                {{ createSaving ? t('common.loading') : t('procurement.submit') }}
              </PrimaryButton>
            </div>
          </template>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
