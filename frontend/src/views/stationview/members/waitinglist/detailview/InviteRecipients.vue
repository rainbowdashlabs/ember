/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import type {InvitationRecipients} from '@/api/generated/schema'

/**
 * Where a mail about the entry goes, named before it is sent: the addresses, or plainly that nobody
 * is written to and what happens instead. The invitation says the entry is only marked as invited;
 * another step says what it does in its own words.
 */
const props = defineProps<{
  recipients: InvitationRecipients | null
  /** What is said when the list writes no mail, the invitation's wording when not given. */
  silentText?: string
  /** What is said when nobody left an address, the invitation's wording when not given. */
  noAddressText?: string
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-1" data-testid="waitlist-invite-recipients">
    <FieldLabel>{{ t('waitingList.inviteRecipients') }}</FieldLabel>
    <Spinner v-if="!props.recipients" size="sm" />
    <ul v-else-if="props.recipients.addresses.length" class="text-sm list-disc pl-5">
      <li v-for="address in props.recipients.addresses" :key="address">{{ address }}</li>
    </ul>
    <p v-else-if="!props.recipients.sendsMail" class="text-sm text-(--text-muted)">
      {{ props.silentText ?? t('waitingList.inviteListSendsNoMail') }}
    </p>
    <p v-else class="text-sm text-(--text-muted)">{{ props.noAddressText ?? t('waitingList.inviteNoAddress') }}</p>
  </div>
</template>
