/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import { maxPointsOf } from '@/views/stationview/protocol/protocolPoints'
import ProtocolEditorDemo from './ProtocolEditorDemo.vue'
import { demoProtocolItems, demoProtocols } from '../fixtures'

const { t } = useI18n()
const [sample] = demoProtocols(t)
const totalPoints = maxPointsOf(demoProtocolItems(t))
</script>

<template>
  <HelpSection :title="t('helpCenter.protocolDetail.exampleTitle')">
    <div class="flex items-center gap-2 mb-4">
      <SecondaryButton disabled>
        <font-awesome-icon :icon="['fas', 'chevron-left']" />
      </SecondaryButton>
      <SectionHeader>{{ sample?.name }}</SectionHeader>
      <EditButton disabled :label="t('common.edit')" />
      <span class="text-sm text-[var(--text-muted)] ml-auto">
        {{ t('protocol.threshold') }}: {{ t('helpCenter.sample.protocol.points', {points: sample?.passThreshold}) }}
        / {{ t('helpCenter.sample.protocol.points', {points: totalPoints}) }} {{ t('protocol.total') }}
      </span>
    </div>

    <ProtocolEditorDemo />

    <PrimaryButton class="mt-4" disabled>
      <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" /> {{ t('protocol.addSection') }}
    </PrimaryButton>
  </HelpSection>
</template>
