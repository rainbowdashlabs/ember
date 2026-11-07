/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import {fromMember, userTypesOf} from '@/components/input/select/memberOption'
import MovementWizard from '@/views/stationview/inventory/movementwizard/MovementWizard.vue'
import type {WizardPrefill} from '@/views/stationview/inventory/movementwizard/useMovementWizard'
import {
  MovementPurpose,
  type MemberWithName,
  type MyInventoryItem,
} from '@/api/generated/schema'

const { t } = useI18n()

const props = defineProps<{
  memberDisplayName: string
  canMarkFormer: boolean
  formerBlockReasons: string[]
  markingFormer: boolean
  deletingMember: boolean
  allMembers: MemberWithName[]
  memberId: number
  memberDisplayNameFn: (m: MemberWithName) => string
}>()

const emit = defineEmits<{
  markFormer: []
  deleteMember: []
  reassignItem: [itemId: number, targetMemberId: number]
  /** A movement was started for this member, so whatever is listed about them is out of date. */
  exchangeStarted: []
}>()

const showFormerModal = ref(false)

const showDeleteModal = ref(false)
const showDeleteConfirm = ref(false)

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
  openReassignModal,
  openExchangeModal,
})
</script>

<template>
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

  <ConfirmDeleteModal
      v-model="showDeleteModal"
      :title="t('memberDetail.deleteTitle')"
      :message="t('memberDetail.deleteText', { name: memberDisplayName })"
      :confirm-label="t('memberDetail.deleteConfirmAction')"
      @confirm="showDeleteModal = false; showDeleteConfirm = true"
  >
    <MutedText tag="p">{{ t('memberDetail.deleteHint') }}</MutedText>
  </ConfirmDeleteModal>

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
