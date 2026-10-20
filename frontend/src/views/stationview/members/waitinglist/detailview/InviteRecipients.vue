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
 * Where the invitation goes, named before it is sent: the addresses, or plainly that nobody is
 * written to and the entry is only marked as invited.
 */
const props = defineProps<{recipients: InvitationRecipients | null}>()

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
      {{ t('waitingList.inviteListSendsNoMail') }}
    </p>
    <p v-else class="text-sm text-(--text-muted)">{{ t('waitingList.inviteNoAddress') }}</p>
  </div>
</template>
