/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {nextTick, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'
import {normaliseScannedPayload} from '@/components/scanner/useBarcodeScanner'
import {inventory} from '@/api'

const {t} = useI18n()
const router = useRouter()

interface ScanInputRef {
  $el: HTMLInputElement
}

const scanInputRef = ref<ScanInputRef | null>(null)
const scanInput = ref('')
const scanError = ref('')

async function onScanSubmit() {
  const query = normaliseScannedPayload(scanInput.value)
  if (!query) return
  scanError.value = ''
  const item = await inventory.findByInternalId(query)
  if (item) {
    scanInput.value = ''
    router.push({name: 'inventory-item-detail', params: {id: item.id}})
  } else {
    scanError.value = t('inventory.manage.scanNotFound')
  }
}

function onScanDecoded(value: string) {
  scanInput.value = value
  onScanSubmit()
}

onMounted(() => {
  nextTick(() => {
    const el = scanInputRef.value?.$el
    if (el instanceof HTMLInputElement) el.focus()
  })
})
</script>

<template>
  <NeutralContainer class="space-y-2">
    <FieldLabel>{{ t('inventory.manage.scanLabel') }}</FieldLabel>
    <form class="flex items-center gap-2" @submit.prevent="onScanSubmit">
      <TextInput ref="scanInputRef" v-model="scanInput" :placeholder="t('inventory.manage.scanPlaceholder')" class="flex-1" />
      <ScanButton @decoded="onScanDecoded"/>
      <PrimaryButton :icon="['fas', 'magnifying-glass']" type="submit">
        {{ t('inventory.manage.scanSubmit') }}
      </PrimaryButton>
    </form>
    <Alert v-if="scanError" variant="error" class="text-sm">{{ scanError }}</Alert>
  </NeutralContainer>
</template>
