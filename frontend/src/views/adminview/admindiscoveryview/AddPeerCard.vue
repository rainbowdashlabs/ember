/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {DiscoveryInfoProbe} from '@/api/discovery'

defineProps<{
  probing: boolean
  probeError: string
  probeResult: DiscoveryInfoProbe | null
}>()

const baseUrl = defineModel<string>('baseUrl', {required: true})
const expectedKey = defineModel<string>('expectedKey', {required: true})

const emit = defineEmits<{
  probe: []
  add: []
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('adminDiscovery.addPeer') }}</SubHeader>
    <p class="text-sm text-(--text-muted)">{{ t('adminDiscovery.addPeerHelp') }}</p>
    <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
      <TextInput v-model="baseUrl" :placeholder="t('adminDiscovery.baseUrlPlaceholder')"/>
      <TextInput v-model="expectedKey" :placeholder="t('adminDiscovery.expectedKey')"/>
    </div>
    <div class="flex flex-wrap gap-2">
      <SecondaryButton :disabled="!baseUrl.trim() || probing" @click="emit('probe')">
        {{ t('adminDiscovery.probe') }}
      </SecondaryButton>
      <SuccessButton :disabled="!baseUrl.trim()" @click="emit('add')">
        {{ t('adminDiscovery.add') }}
      </SuccessButton>
    </div>
    <Alert v-if="probeError" variant="error">{{ probeError }}</Alert>
    <Alert v-if="probeResult" variant="success">
      {{ t('adminDiscovery.probeOk') }}
      <code class="ml-2 text-xs break-all">{{ probeResult.instanceId }}</code>
    </Alert>
  </NeutralContainer>
</template>
