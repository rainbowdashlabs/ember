/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'
import Alert from '@/components/feedback/Alert.vue'

const scanValue = defineModel<string>('scanValue', {required: true})

defineProps<{
  scanBusy: boolean
  scanError: string
  scanSuccess: string
}>()

const emit = defineEmits<{
  (e: 'submit'): void
  (e: 'decoded', value: string): void
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer>
    <SubHeader class="mb-2">{{ t('inventory.memberInventory.scanTitle') }}</SubHeader>
    <p class="text-xs text-(--text-muted) mb-2">{{ t('inventory.memberInventory.scanHint') }}</p>
    <div class="flex gap-2">
      <TextInput
          v-model="scanValue"
          :placeholder="t('inventory.memberInventory.scanPlaceholder')"
          @keydown.enter="emit('submit')"
          class="flex-1"
          :disabled="scanBusy"
      />
      <ScanButton mode="continuous" :disabled="scanBusy" @decoded="emit('decoded', $event)" />
    </div>
    <Alert v-if="scanError" variant="error" class="mt-2">{{ scanError }}</Alert>
    <Alert v-if="scanSuccess" variant="success" class="mt-2">{{ scanSuccess }}</Alert>
  </NeutralContainer>
</template>
