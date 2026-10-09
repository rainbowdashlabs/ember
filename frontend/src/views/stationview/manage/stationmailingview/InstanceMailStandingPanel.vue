/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import MailReplyToField from './MailReplyToField.vue'
import {getOwnInstanceMail} from '@/api/instanceMail'
import type {InstanceMailStation} from '@/api/generated/schema'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {formatDate} from '@/util/format'

/**
 * Whether the instance carries this station's mail after its own providers, and how much of it went
 * out that way today. The station reads it here; an instance administrator decides it. The address
 * replies go to is the station's own choice and sits beside it, since it matters most for mail the
 * instance sends under its address.
 */
const {t} = useI18n()

const standing = ref<InstanceMailStation | null>(null)

const {failure} = useAsyncLoader(async (isCurrent) => {
  const loaded = await getOwnInstanceMail()
  if (isCurrent()) standing.value = loaded
})
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center gap-2 flex-wrap">
      <SectionHeader>{{ t('instanceMail.station.title') }}</SectionHeader>
      <SuccessBadge v-if="standing?.granted">{{ t('instanceMail.station.grantedBadge') }}</SuccessBadge>
    </div>
    <FailureAlert :failure="failure"/>
    <template v-if="standing">
      <MutedText v-if="standing.granted" tag="p" size="sm" data-testid="instance-mail-granted">
        {{ t('instanceMail.station.granted', {date: formatDate(standing.grantedAt)}) }}
      </MutedText>
      <MutedText v-else tag="p" size="sm" data-testid="instance-mail-not-granted">
        {{ t('instanceMail.station.notGranted') }}
      </MutedText>
      <MutedText v-if="standing.granted" tag="p" size="sm" data-testid="instance-mail-use">
        {{ standing.dailyLimit === null
          ? t('instanceMail.station.sentNoLimit', {sent: standing.sentToday})
          : t('instanceMail.station.sentOfLimit', {sent: standing.sentToday, limit: standing.dailyLimit}) }}
      </MutedText>
    </template>
    <MailReplyToField/>
  </NeutralContainer>
</template>
