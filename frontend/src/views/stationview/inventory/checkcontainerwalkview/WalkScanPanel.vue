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
import ScanButton from '@/components/scanner/ScanButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import type {WalkCounts} from './types'

defineProps<{
  flash: string
  counts: WalkCounts
}>()

const scan = defineModel<string>('scan', {required: true})

const emit = defineEmits<{
  submit: []
  decoded: [value: string]
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="mb-4">
    <SubHeader class="mb-2">{{ t('inventory.checkContainer.scanTitle') }}</SubHeader>
    <div class="flex gap-2">
      <TextInput
          v-model="scan"
          :placeholder="t('inventory.checkContainer.scanPlaceholder')"
          @keydown.enter="emit('submit')"
          class="flex-1"
      />
      <ScanButton mode="continuous" @decoded="emit('decoded', $event)" />
    </div>
    <Alert v-if="flash" variant="info" class="mt-3">{{ flash }}</Alert>
    <div class="flex flex-wrap gap-2 text-sm mt-3">
      <SuccessBadge>{{ t('inventory.checkContainer.statusConfirmed') }}: {{ counts.confirmed }}</SuccessBadge>
      <InfoBadge>{{ t('inventory.checkContainer.statusPending') }}: {{ counts.pending }}</InfoBadge>
      <ErrorBadge>{{ t('inventory.checkContainer.statusMissing') }}: {{ counts.missing }}</ErrorBadge>
      <InfoBadge v-if="counts.extra > 0">{{ t('inventory.checkContainer.statusExtra') }}: {{ counts.extra }}</InfoBadge>
    </div>
  </NeutralContainer>
</template>
