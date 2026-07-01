/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { WaitingListEntryWithScore } from '@/api/types'

type TransitionKind = 'invite' | 'testing' | 'join' | 'approve' | 'reject' | 'withdraw'

interface PendingTransition {
  entry: WaitingListEntryWithScore
  kind: TransitionKind
}

const props = defineProps<{
  pending: PendingTransition | null
  running: boolean
}>()

const emit = defineEmits<{
  (e: 'cancel'): void
  (e: 'confirm'): void
}>()

const { t } = useI18n()

const transitionTextKey: Record<TransitionKind, string> = {
  invite: 'waitingList.transitionInviteText',
  testing: 'waitingList.transitionTestingText',
  join: 'waitingList.transitionJoinText',
  approve: 'waitingList.transitionApproveText',
  reject: 'waitingList.transitionRejectText',
  withdraw: 'waitingList.transitionWithdrawText',
}

function entryFullName(item: WaitingListEntryWithScore): string {
  const e = item.entry
  return e.lastname ? `${e.firstname} ${e.lastname}` : e.firstname
}

function onUpdate(v: boolean) {
  if (!v) emit('cancel')
}

const open = () => props.pending !== null
</script>

<template>
  <Modal :model-value="open()" @update:model-value="onUpdate">
    <div v-if="pending" class="space-y-4">
      <SubHeader>{{ t('waitingList.transitionConfirmTitle') }}</SubHeader>
      <p class="text-sm">{{ t(transitionTextKey[pending.kind], { name: entryFullName(pending.entry) }) }}</p>
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="running" @click="emit('confirm')">
          {{ running ? t('common.loading') : t('waitingList.transitionConfirmAction') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
