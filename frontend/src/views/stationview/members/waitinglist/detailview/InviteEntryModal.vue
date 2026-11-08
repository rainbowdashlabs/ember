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
import InviteAnswerSoFar from './InviteAnswerSoFar.vue'
import type {InvitationDateDraft, InvitationMode} from './invitationDates'

/**
 * The invitation to come and look, which is the one transition that has something to fill in.
 *
 * <p>It opens on the first date rather than on the button, and the button stays off until every row
 * has its date, so neither a stray enter nor a quick click sends a mail. Offering more than one date
 * lets the person pick whichever suits. The dialog names the addresses the mail goes to before
 * anything is sent. What it never does is sign anybody up for any of the dates.
 *
 * <p>The same dialog offers somebody already invited other dates. It then shows what they answered
 * first, so the station replies to what was said.
 */
const dates = defineModel<InvitationDateDraft[]>('dates', {required: true})
const message = defineModel<string>('message', {required: true})

const props = defineProps<{
  target: WaitingListEntryWithScore | null
  mode: InvitationMode
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
      <template v-if="props.mode === 'otherDates'">
        <SubHeader>{{ t('waitingList.otherDatesTitle') }}</SubHeader>
        <p class="text-sm">{{ t('waitingList.otherDatesText', {name: entryFullName(props.target)}) }}</p>
        <InviteAnswerSoFar :entry="props.target" />
      </template>
      <template v-else>
        <SubHeader>{{ t('waitingList.inviteTitle') }}</SubHeader>
        <p class="text-sm">{{ t('waitingList.inviteText', {name: entryFullName(props.target)}) }}</p>
      </template>

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

      <InviteRecipients
          :recipients="props.recipients"
          :silent-text="props.mode === 'otherDates' ? t('waitingList.otherDatesListSendsNoMail') : undefined"
          :no-address-text="props.mode === 'otherDates' ? t('waitingList.otherDatesNoAddress') : undefined"
      />

      <ButtonRow pair align="end">
        <SecondaryButton data-cancel @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton
            :disabled="!props.canSend"
            data-confirm
            data-testid="waitlist-invite-send"
            @click="emit('confirm')"
        >
          <template v-if="props.running">{{ t('common.loading') }}</template>
          <template v-else-if="props.mode === 'otherDates' && props.recipients?.addresses.length">{{ t('waitingList.otherDatesSend') }}</template>
          <template v-else-if="props.mode === 'otherDates'">{{ t('waitingList.otherDatesSave') }}</template>
          <template v-else-if="props.recipients?.addresses.length">{{ t('waitingList.inviteSend') }}</template>
          <template v-else>{{ t('waitingList.inviteMarkOnly') }}</template>
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
