/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import ItemSearchPicker from '@/components/input/search/ItemSearchPicker.vue'
import type { StationMember, InventoryItem } from '@/api/types'
import type { InventorySize } from '@/api/types'
import type { MyInventoryItem } from '@/api/inventory'

const { t } = useI18n()

const props = defineProps<{
  memberDisplayName: string
  canMarkFormer: boolean
  formerBlockReasons: string[]
  markingFormer: boolean
  deletingMember: boolean
  allMembers: StationMember[]
  memberId: number
  memberDisplayNameFn: (m: StationMember) => string
  exchangeSizes: InventorySize[]
}>()

const emit = defineEmits<{
  markFormer: []
  deleteMember: []
  assignItem: [itemId: number]
  reassignItem: [itemId: number, targetMemberId: number]
  submitExchange: [data: { item: MyInventoryItem; newSizeId?: number; reason: string }]
  loadExchangeSizes: [inventoryId: number]
}>()

// Former modal
const showFormerModal = ref(false)

// Delete modal
const showDeleteModal = ref(false)
const showDeleteConfirm = ref(false)

// Assign modal
const showAssignModal = ref(false)
const pickedItemId = ref<number | null>(null)
const assignScanError = ref('')

// Reassign modal
const showReassignModal = ref(false)
const reassignItemRef = ref<MyInventoryItem | null>(null)
const reassignTargetId = ref('')

// Exchange modal
const showExchangeModal = ref(false)
const exchangeItem = ref<MyInventoryItem | null>(null)
const exchangeNewSizeId = ref<string>('')
const exchangeReason = ref('')
const exchangeSaving = ref(false)
const exchangeSuccess = ref(false)

const reassignTargets = computed(() => {
  return props.allMembers
    .filter(m => m.id !== props.memberId)
    .sort((a, b) => props.memberDisplayNameFn(a).localeCompare(props.memberDisplayNameFn(b)))
})

function openFormerModal() { showFormerModal.value = true }

function openDeleteModal() { showDeleteModal.value = true }

function openAssignModal() {
  showAssignModal.value = true
  pickedItemId.value = null
  assignScanError.value = ''
}

function onItemPicked(item: InventoryItem) {
  if (item.assignedTo === props.memberId) {
    assignScanError.value = t('memberDetail.scanAlreadyHere', { name: item.name ?? item.internalId ?? `#${item.id}` })
    pickedItemId.value = null
    return
  }
  if (item.assignedTo != null) {
    assignScanError.value = t('memberDetail.scanAlreadyAssigned', { name: item.name ?? item.internalId ?? `#${item.id}` })
    pickedItemId.value = null
    return
  }
  emit('assignItem', item.id)
  showAssignModal.value = false
}

function openReassignModal(item: MyInventoryItem) {
  reassignItemRef.value = item
  reassignTargetId.value = ''
  showReassignModal.value = true
}

function confirmReassign() {
  if (!reassignItemRef.value || !reassignTargetId.value) return
  emit('reassignItem', reassignItemRef.value.id, Number(reassignTargetId.value))
  showReassignModal.value = false
}

function openExchangeModal(item: MyInventoryItem) {
  exchangeItem.value = item
  exchangeReason.value = ''
  exchangeNewSizeId.value = ''
  exchangeSuccess.value = false
  showExchangeModal.value = true
  emit('loadExchangeSizes', item.inventoryId)
}

function submitExchange() {
  if (!exchangeItem.value || !exchangeReason.value.trim()) return
  exchangeSaving.value = true
  emit('submitExchange', {
    item: exchangeItem.value,
    newSizeId: exchangeNewSizeId.value ? Number(exchangeNewSizeId.value) : undefined,
    reason: exchangeReason.value.trim(),
  })
  exchangeSuccess.value = true
  exchangeSaving.value = false
}

defineExpose({
  openFormerModal,
  openDeleteModal,
  openAssignModal,
  openReassignModal,
  openExchangeModal,
})
</script>

<template>
  <!-- Former confirmation modal -->
  <Modal v-model="showFormerModal">
    <div class="space-y-4">
      <SectionHeader>{{ t('memberDetail.markFormerTitle') }}</SectionHeader>
      <template v-if="canMarkFormer">
        <p class="text-sm">{{ t('memberDetail.markFormerConfirm', { name: memberDisplayName }) }}</p>
        <p class="text-xs text-(--text-muted)">{{ t('memberDetail.markFormerHint') }}</p>
        <div class="flex justify-end gap-2">
          <SecondaryButton @click="showFormerModal = false">{{ t('common.cancel') }}</SecondaryButton>
          <ErrorButton :disabled="markingFormer" @click="emit('markFormer'); showFormerModal = false">
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
      <p class="text-sm">{{ t('memberDetail.deleteText', { name: memberDisplayName }) }}</p>
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
        <ErrorButton :disabled="deletingMember" @click="emit('deleteMember'); showDeleteConfirm = false">
          {{ deletingMember ? t('common.loading') : t('memberDetail.deleteConfirmFinal') }}
        </ErrorButton>
      </div>
    </div>
  </Modal>

  <!-- Assign item modal -->
  <Modal v-model="showAssignModal">
    <div class="space-y-4">
      <SectionHeader>{{ t('memberDetail.assignItem') }}</SectionHeader>
      <Alert v-if="assignScanError" variant="error">{{ assignScanError }}</Alert>
      <ItemSearchPicker
          v-model="pickedItemId"
          :exclude-assigned="true"
          :exclude-lost="true"
          @pick="onItemPicked"
      />
      <div class="flex justify-end">
        <SecondaryButton @click="showAssignModal = false">{{ t('common.cancel') }}</SecondaryButton>
      </div>
    </div>
  </Modal>

  <!-- Reassign item modal -->
  <Modal v-model="showReassignModal">
    <div class="space-y-4">
      <SectionHeader>{{ t('memberDetail.reassignItem') }}</SectionHeader>
      <p v-if="reassignItemRef" class="text-sm">
        {{ reassignItemRef.inventoryName }} &mdash; {{ reassignItemRef.name }}
        <SizeBadge>{{ reassignItemRef.sizeName ?? t('common.unisize') }}</SizeBadge>
      </p>
      <div class="space-y-1">
        <FieldLabel>{{ t('memberDetail.selectTargetMember') }}</FieldLabel>
        <SelectInput v-model="reassignTargetId">
          <option value="" disabled>{{ t('memberDetail.selectTargetPlaceholder') }}</option>
          <option v-for="m in reassignTargets" :key="m.id" :value="String(m.id)">{{ memberDisplayNameFn(m) }}</option>
        </SelectInput>
      </div>
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="showReassignModal = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!reassignTargetId" @click="confirmReassign">
          {{ t('memberDetail.reassignItem') }}
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
          {{ exchangeItem.inventoryName }} &mdash; {{ exchangeItem.name }}
          <SizeBadge>{{ exchangeItem.sizeName ?? t('common.unisize') }}</SizeBadge>
        </p>
        <div v-if="exchangeSizes.length > 0" class="space-y-1">
          <FieldLabel>{{ t('exchanges.newSize') }}</FieldLabel>
          <SelectInput v-model="exchangeNewSizeId">
            <option value="" disabled>{{ t('exchanges.selectNewSize') }}</option>
            <option v-for="size in exchangeSizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
          </SelectInput>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('exchanges.reason') }}</FieldLabel>
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
</template>
