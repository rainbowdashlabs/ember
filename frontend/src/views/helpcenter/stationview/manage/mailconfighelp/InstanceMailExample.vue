/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import MailProviderStanding from '@/components/mail/MailProviderStanding.vue'
import type {ProviderStanding} from '@/api/generated/schema'

/**
 * What a granted station sees of the instance carrying its mail: the box on its mailing page, and one
 * of the instance's providers at the end of its list in the delivery overview.
 */
const {t} = useI18n()

const lent: ProviderStanding = {
  position: 1,
  provider: 'BREVO',
  senderAddress: 'post@ember.example',
  attempts: 2,
  dailySendLimit: 300,
  sentToday: 12,
  waiting: 0,
  exhausted: false,
  viaInstance: true,
  pool: {limit: 150, sentToday: 64, stations: []},
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center gap-2 flex-wrap">
      <SectionHeader>{{ t('instanceMail.station.title') }}</SectionHeader>
      <SuccessBadge>{{ t('instanceMail.station.grantedBadge') }}</SuccessBadge>
    </div>
    <MutedText tag="p" size="sm">{{ t('instanceMail.station.granted', {date: '01.10.2026'}) }}</MutedText>
    <MutedText tag="p" size="sm">{{ t('instanceMail.station.sentOfLimit', {sent: 12, limit: 50}) }}</MutedText>
    <LabelledField :label="t('instanceMail.station.replyTo')" :help="t('instanceMail.station.replyToHint')" hint>
      <TextInput model-value="kontakt@jugendfeuerwehr.example" disabled/>
    </LabelledField>
    <MailProviderStanding :standing="lent"/>
  </NeutralContainer>
</template>
