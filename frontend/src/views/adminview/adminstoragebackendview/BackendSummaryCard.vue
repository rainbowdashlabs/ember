/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {ProbeResult} from '@/api/storageBackend'

defineProps<{
  summaryLabel: string
  probingLive: boolean
  probeOutcome: ProbeResult | null
}>()

defineEmits<{
  probe: []
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('adminStorageBackend.summary.title') }}</SubHeader>
    <MutedText tag="p" size="sm">{{ summaryLabel }}</MutedText>
    <div>
      <SecondaryButton :disabled="probingLive" @click="$emit('probe')">
        {{ probingLive ? t('adminStorageBackend.actions.probing') : t('adminStorageBackend.actions.probeLive') }}
      </SecondaryButton>
    </div>
    <div v-if="probeOutcome" class="text-sm">
      <Alert v-if="probeOutcome.healthy" variant="success">
        {{ t('adminStorageBackend.probe.ok') }}
      </Alert>
      <Alert v-else variant="error">
        {{ t('adminStorageBackend.probe.failed', {reason: probeOutcome.error ?? ''}) }}
      </Alert>
    </div>
  </NeutralContainer>
</template>
