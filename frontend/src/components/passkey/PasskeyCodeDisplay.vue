/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onBeforeUnmount} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * The QR code held up in the room, with the typed code beneath it for a device with no camera.
 * The parent revokes the code when this leaves the screen: an abandoned attempt must not leave
 * a photographed code alive for the rest of its window.
 */
const {t} = useI18n()

const props = defineProps<{
  code: string
  qrPng: string
}>()

const emit = defineEmits<{
  (e: 'gone'): void
}>()

const groupedCode = computed(() =>
  props.code.length === 8 ? `${props.code.slice(0, 4)}-${props.code.slice(4)}` : props.code)

onBeforeUnmount(() => emit('gone'))
</script>

<template>
  <div class="space-y-2 text-center">
    <img :src="`data:image/png;base64,${qrPng}`" :alt="t('passkeys.code.qrAlt')"
         class="mx-auto h-48 w-48 rounded bg-white p-2"/>
    <div class="text-2xl font-mono tracking-widest">{{ groupedCode }}</div>
    <MutedText tag="p" size="sm">{{ t('passkeys.code.hint') }}</MutedText>
  </div>
</template>
