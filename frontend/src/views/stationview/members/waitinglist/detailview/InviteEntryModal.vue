/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import type {InvitationRecipients, WaitingListEntryWithScore} from '@/api/generated/schema'
import {entryFullName} from './entryFullName'
import InviteRecipients from './InviteRecipients.vue'
import InviteDateList from './InviteDateList.vue'
import type {InvitationDateDraft} from './invitationDates'

/**
 * The invitation to come and look, which is the one transition that has something to fill in.
 *
 * <p>It opens on the first date rather than on the button, and the button stays off until every row
 * has its date, so neither a stray enter nor a quick click sends a mail. Offering more than one date
 * lets the person pick whichever suits. The dialog names the addresses the mail goes to before
 * anything is sent. What it never does is sign anybody up for any of the dates.
 */
const dates = defineModel<InvitationDateDraft[]>('dates', {required: true})
const message = defineModel<string>('message', {required: true})

const props = defineProps<{
  target: WaitingListEntryWithScore | null
  recipients: InvitationRecipients | null
  canSend: boolean
  duplicate: boolean
  running: boolean
}>()

const emit = defineEmits<{
  cancel: []
  confirm: []
}>()

const {t} = useI18n()

const MESSAGE_MAX_LENGTH = 2000

function onUpdate(value: boolean) {
  if (!value) emit('cancel')
}
</script>

<template>
  <Modal :model-value="props.target !== null" @update:model-value="onUpdate">
    <div v-if="props.target" class="space-y-4" data-testid="waitlist-invite-modal">
      <SubHeader>{{ t('waitingList.inviteTitle') }}</SubHeader>
      <p class="text-sm">{{ t('waitingList.inviteText', {name: entryFullName(props.target)}) }}</p>

      <InviteDateList v-model="dates" :duplicate="props.duplicate" />

      <div class="space-y-1">
        <FieldLabel>{{ t('waitingList.inviteMessage') }}</FieldLabel>
        <TextAreaInput
            v-model="message"
            :rows="3"
            :maxlength="MESSAGE_MAX_LENGTH"
            :placeholder="t('waitingList.inviteMessagePlaceholder')"
            data-testid="waitlist-invite-message"
        />
      </div>

      <InviteRecipients :recipients="props.recipients" />

      <ButtonRow pair align="end">
        <SecondaryButton data-cancel @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton
            :disabled="!props.canSend"
            data-confirm
            data-testid="waitlist-invite-send"
            @click="emit('confirm')"
        >
          <template v-if="props.running">{{ t('common.loading') }}</template>
          <template v-else-if="props.recipients?.addresses.length">{{ t('waitingList.inviteSend') }}</template>
          <template v-else>{{ t('waitingList.inviteMarkOnly') }}</template>
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
