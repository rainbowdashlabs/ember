/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { InvitationRecipients } from '@/api/generated/schema'
import { entryFullName } from './entryFullName'
import { TELLS_THE_FAMILY, type PendingTransition, type TransitionKind } from './entryTransitions'
import TransitionMessage from './TransitionMessage.vue'

/**
 * Confirms a move before it runs. A move the family is told about also takes what the station
 * writes to them, beside the addresses it goes to.
 */
const message = defineModel<string>('message', { required: true })

const props = defineProps<{
  pending: PendingTransition | null
  recipients: InvitationRecipients | null
  canConfirm: boolean
  running: boolean
}>()

const emit = defineEmits<{
  cancel: []
  confirm: []
}>()

const { t } = useI18n()

const transitionTextKey: Record<TransitionKind, string> = {
  backToWaiting: 'waitingList.transitionBackToWaitingText',
  testing: 'waitingList.transitionTestingText',
  join: 'waitingList.transitionJoinText',
  approve: 'waitingList.transitionApproveText',
  reject: 'waitingList.transitionRejectText',
  withdraw: 'waitingList.transitionWithdrawText',
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
      <TransitionMessage
        v-if="TELLS_THE_FAMILY.has(pending.kind)"
        v-model="message"
        :kind="pending.kind"
        :recipients="props.recipients"
      />
      <ButtonRow pair align="end">
        <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!props.canConfirm" data-testid="waitlist-transition-confirm" @click="emit('confirm')">
          {{ running ? t('common.loading') : t('waitingList.transitionConfirmAction') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
