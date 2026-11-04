/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import type {InvitationRecipients} from '@/api/generated/schema'
import InviteRecipients from './InviteRecipients.vue'
import type {TransitionKind} from './entryTransitions'

/**
 * What the station writes to the family with the start of the trial or a withdrawal, beside where it
 * goes.
 *
 * <p>The start of the trial keeps its message on the entry's page as well, so the box stays even when
 * nobody is written to. A withdrawal deletes the entry and its page, so a message nobody would be
 * written to would reach nobody: the box only appears once the addresses are known and there are
 * some, and the dialog says so otherwise.
 */
const message = defineModel<string>({required: true})

const props = defineProps<{
  kind: TransitionKind
  recipients: InvitationRecipients | null
}>()

const {t} = useI18n()

const MESSAGE_MAX_LENGTH = 2000

const somebodyWrittenTo = computed(() => props.recipients !== null && props.recipients.addresses.length > 0)

const offersMessage = computed(() => props.kind === 'testing' || somebodyWrittenTo.value)

watch(offersMessage, offered => {
  if (!offered) message.value = ''
})

const silentText = computed(() => props.kind === 'withdraw'
  ? t('waitingList.withdrawListSendsNoMail')
  : t('waitingList.testingListSendsNoMail'))

const noAddressText = computed(() => props.kind === 'withdraw'
  ? t('waitingList.withdrawNoAddress')
  : t('waitingList.testingNoAddress'))
</script>

<template>
  <div class="space-y-3" data-testid="waitlist-transition-message">
    <div v-if="offersMessage" class="space-y-1">
      <FieldLabel>{{ t('waitingList.transitionMessage') }}</FieldLabel>
      <TextAreaInput
          v-model="message"
          :rows="3"
          :maxlength="MESSAGE_MAX_LENGTH"
          :placeholder="props.kind === 'withdraw'
            ? t('waitingList.withdrawMessagePlaceholder')
            : t('waitingList.testingMessagePlaceholder')"
          data-testid="waitlist-transition-message-input"
      />
    </div>
    <InviteRecipients :recipients="props.recipients" :silent-text="silentText" :no-address-text="noAddressText" />
  </div>
</template>
