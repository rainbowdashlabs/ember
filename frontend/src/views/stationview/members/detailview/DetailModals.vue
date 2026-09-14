/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import {fromMember, userTypesOf} from '@/components/input/select/memberOption'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import ItemSearchPicker from '@/components/input/search/ItemSearchPicker.vue'
import HandOutChoice from '@/components/inventory/HandOutChoice.vue'
import type {HandOutMode} from '@/components/inventory/HandOutChoice.vue'
import MovementWizard from '@/views/stationview/inventory/movementwizard/MovementWizard.vue'
import type {WizardPrefill} from '@/views/stationview/inventory/movementwizard/useMovementWizard'
import {MovementPurpose} from '@/api/movements'
import type {InventoryItem, MyInventoryItem} from '@/api/inventory'
import type { StationMember } from '@/api/types'

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
}>()

const emit = defineEmits<{
  markFormer: []
  deleteMember: []
  assignItem: [itemId: number]
  /** The piece is promised to the member rather than handed over now. */
  planHandOut: [itemId: number, inventoryId: number]
  reassignItem: [itemId: number, targetMemberId: number]
  /** A movement was started for this member, so whatever is listed about them is out of date. */
  exchangeStarted: []
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
const handOutMode = ref<HandOutMode>('NOW')

// Reassign modal
const showReassignModal = ref(false)
const reassignItemRef = ref<MyInventoryItem | null>(null)
const reassignTargetId = ref('')

const showWizard = ref(false)
const wizardPrefill = ref<WizardPrefill>({})

const reassignTargets = computed(() =>
  props.allMembers.filter(m => m.id !== props.memberId).map(fromMember))

const reassignUserTypes = computed(() => userTypesOf(reassignTargets.value))

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
  if (handOutMode.value === 'PLANNED') emit('planHandOut', item.id, item.inventoryId)
  else emit('assignItem', item.id)
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

/**
 * A swap of this member's piece, raised the one way every swap is raised.
 *
 * <p>The piece answers the purpose, the member and the subject, so the wizard asks the reason and shows
 * the chain it would walk.
 */
function openExchangeModal(item: MyInventoryItem) {
  wizardPrefill.value = {
    purpose: MovementPurpose.EXCHANGE,
    memberId: props.memberId,
    itemId: item.id,
    inventoryId: item.inventoryId,
    oldSizeId: item.sizeId ?? null,
    skip: ['purpose', 'party', 'subject'],
  }
  showWizard.value = true
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
      <SubHeader>{{ t('memberDetail.markFormerTitle') }}</SubHeader>
      <template v-if="canMarkFormer">
        <p class="text-sm">{{ t('memberDetail.markFormerConfirm', { name: memberDisplayName }) }}</p>
        <p class="text-xs text-(--text-muted)">{{ t('memberDetail.markFormerHint') }}</p>
        <ButtonRow pair align="end">
          <SecondaryButton @click="showFormerModal = false">{{ t('common.cancel') }}</SecondaryButton>
          <ErrorButton :disabled="markingFormer" @click="emit('markFormer'); showFormerModal = false">
            {{ markingFormer ? t('common.loading') : t('memberDetail.markFormer') }}
          </ErrorButton>
        </ButtonRow>
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
      <SubHeader>{{ t('memberDetail.deleteTitle') }}</SubHeader>
      <p class="text-sm">{{ t('memberDetail.deleteText', { name: memberDisplayName }) }}</p>
      <p class="text-xs text-(--text-muted)">{{ t('memberDetail.deleteHint') }}</p>
      <ButtonRow pair align="end">
        <SecondaryButton @click="showDeleteModal = false">{{ t('common.cancel') }}</SecondaryButton>
        <ErrorButton @click="showDeleteModal = false; showDeleteConfirm = true">
          {{ t('memberDetail.deleteConfirmAction') }}
        </ErrorButton>
      </ButtonRow>
    </div>
  </Modal>

  <!-- Delete member modal (second confirmation) -->
  <Modal v-model="showDeleteConfirm">
    <div class="space-y-4">
      <SubHeader>{{ t('memberDetail.deleteConfirmTitle') }}</SubHeader>
      <p class="text-sm font-semibold text-error">{{ t('memberDetail.deleteConfirmText') }}</p>
      <ButtonRow pair align="end">
        <SecondaryButton @click="showDeleteConfirm = false">{{ t('common.cancel') }}</SecondaryButton>
        <ErrorButton :disabled="deletingMember" @click="emit('deleteMember'); showDeleteConfirm = false">
          {{ deletingMember ? t('common.loading') : t('memberDetail.deleteConfirmFinal') }}
        </ErrorButton>
      </ButtonRow>
    </div>
  </Modal>

  <!-- Assign item modal -->
  <Modal v-model="showAssignModal">
    <div class="space-y-4">
      <SubHeader>{{ t('memberDetail.assignItem') }}</SubHeader>
      <Alert v-if="assignScanError" variant="error">{{ assignScanError }}</Alert>
      <HandOutChoice v-model="handOutMode"/>
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
      <SubHeader>{{ t('memberDetail.reassignItem') }}</SubHeader>
      <p v-if="reassignItemRef" class="text-sm">
        {{ reassignItemRef.inventoryName }}, {{ reassignItemRef.name }}
        <SizeBadge>{{ reassignItemRef.sizeName ?? t('common.unisize') }}</SizeBadge>
      </p>
      <div class="space-y-1">
        <FieldLabel>{{ t('memberDetail.selectTargetMember') }}</FieldLabel>
        <MemberSelectInput
            v-model="reassignTargetId"
            :members="reassignTargets"
            :user-types="reassignUserTypes"
            :placeholder="t('memberDetail.selectTargetPlaceholder')"
        />
      </div>
      <ButtonRow pair align="end">
        <SecondaryButton @click="showReassignModal = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!reassignTargetId" @click="confirmReassign">
          {{ t('memberDetail.reassignItem') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>

  <MovementWizard v-model="showWizard" :prefill="wizardPrefill" @started="emit('exchangeStarted')"/>
</template>
