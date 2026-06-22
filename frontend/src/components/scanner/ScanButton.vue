/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import BarcodeScanModal from './BarcodeScanModal.vue'
import {useBarcodeScanner} from './useBarcodeScanner'
import type {BarcodeFormat} from './useBarcodeScanner'

withDefaults(defineProps<{
  mode?: 'one-shot' | 'continuous'
  formats?: BarcodeFormat[]
  disabled?: boolean
}>(), {
  mode: 'one-shot',
})

const emit = defineEmits<{
  decoded: [value: string]
}>()

const {t} = useI18n()
const {noCameraAvailable} = useBarcodeScanner()
const open = ref(false)

function onDecoded(value: string) {
  emit('decoded', value)
}
</script>

<template>
  <template v-if="!noCameraAvailable">
    <IconButton
        :icon="['fas', 'barcode']"
        :label="t('inventory.scan.button.label')"
        :disabled="disabled"
        class="text-secondary-accent hover:bg-secondary/15 dark:text-secondary"
        @click="open = true"
    />
    <BarcodeScanModal v-model="open" :mode="mode" :formats="formats" @decoded="onDecoded"/>
  </template>
</template>
