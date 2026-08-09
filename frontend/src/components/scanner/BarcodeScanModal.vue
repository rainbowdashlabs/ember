/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {nextTick, onBeforeUnmount, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {useBarcodeScanner, type BarcodeFormat, type ScannerSession} from './useBarcodeScanner'

const props = withDefaults(defineProps<{
  mode?: 'one-shot' | 'continuous'
  formats?: BarcodeFormat[]
}>(), {
  mode: 'one-shot',
})

const emit = defineEmits<{
  decoded: [value: string]
  close: []
}>()

const open = defineModel<boolean>({default: false})
const {t} = useI18n()
const {startScan} = useBarcodeScanner()

const videoEl = ref<HTMLVideoElement | null>(null)
const status = ref<'idle' | 'requesting' | 'scanning' | 'error'>('idle')
const errorKey = ref<string | null>(null)
const confirmedAt = ref<number | null>(null)
const lastPayload = ref<string | null>(null)
const lastPayloadAt = ref<number>(0)

let session: ScannerSession | null = null
let attempt = 0

async function begin() {
  if (!videoEl.value) return
  const token = ++attempt
  status.value = 'requesting'
  errorKey.value = null
  try {
    const started = await startScan({
      videoEl: videoEl.value,
      formats: props.formats,
      onDecode: handleDecode,
    })
    if (token !== attempt) {
      started.stop()
      return
    }
    session = started
    status.value = 'scanning'
  } catch (e) {
    if (token === attempt) handleError(e as Error)
  }
}

function handleDecode(value: string) {
  if (!open.value) return
  const now = Date.now()
  if (props.mode === 'continuous' && lastPayload.value === value && now - lastPayloadAt.value < 1500) {
    return
  }
  lastPayload.value = value
  lastPayloadAt.value = now
  emit('decoded', value)
  if (typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function') {
    navigator.vibrate(50)
  }
  if (props.mode === 'one-shot') {
    close()
    return
  }
  confirmedAt.value = now
  setTimeout(() => {
    if (confirmedAt.value === now) confirmedAt.value = null
  }, 1000)
}

function handleError(e: Error) {
  status.value = 'error'
  if (e.name === 'NotAllowedError' || e.name === 'SecurityError') {
    errorKey.value = 'inventory.scan.error.denied'
  } else if (e.name === 'NotFoundError' || e.name === 'OverconstrainedError') {
    errorKey.value = 'inventory.scan.error.notFound'
  } else if (e.message === 'barcode-scanner-insecure-context') {
    errorKey.value = 'inventory.scan.error.insecureContext'
  } else if (e.message === 'barcode-scanner-unsupported') {
    errorKey.value = 'inventory.scan.error.unsupported'
  } else {
    errorKey.value = 'inventory.scan.error.unsupported'
  }
  if (session) {
    session.stop()
    session = null
  }
}

function resetState() {
  attempt++
  if (session) {
    session.stop()
    session = null
  }
  status.value = 'idle'
  confirmedAt.value = null
  lastPayload.value = null
  lastPayloadAt.value = 0
  errorKey.value = null
}

function close() {
  resetState()
  open.value = false
  emit('close')
}

watch(open, async (isOpen) => {
  if (isOpen) {
    await nextTick()
    await begin()
  } else {
    resetState()
  }
})

function onBeforeUnload() {
  if (session) {
    session.stop()
    session = null
  }
}

if (typeof window !== 'undefined') {
  window.addEventListener('beforeunload', onBeforeUnload)
}

onBeforeUnmount(() => {
  if (session) {
    session.stop()
    session = null
  }
  if (typeof window !== 'undefined') {
    window.removeEventListener('beforeunload', onBeforeUnload)
  }
})
</script>

<template>
  <Modal v-model="open" size="lg">
    <div class="space-y-3">
      <SubHeader>{{ t('inventory.scan.modal.title') }}</SubHeader>
      <MutedText tag="p" size="sm">{{ t('inventory.scan.modal.hint') }}</MutedText>

      <div class="relative aspect-video w-full overflow-hidden rounded-theme bg-black">
        <video
            ref="videoEl"
            class="absolute inset-0 h-full w-full object-cover"
            :aria-label="t('inventory.scan.modal.title')"
            muted
            autoplay
            playsinline
        />
        <div class="pointer-events-none absolute inset-1/4 rounded-theme border-2 border-primary/80"/>
        <Transition name="fade">
          <div
              v-if="confirmedAt"
              class="absolute inset-x-0 bottom-4 mx-auto w-fit rounded-full bg-success/90 px-3 py-1 text-sm font-medium text-white"
          >
            {{ t('inventory.scan.continuous.confirmed') }}
          </div>
        </Transition>
      </div>

      <div aria-live="polite" class="min-h-[1.5rem] text-sm text-(--text-muted)">
        <Alert v-if="errorKey" variant="error">{{ t(errorKey) }}</Alert>
        <span v-else-if="status === 'requesting'">{{ t('inventory.scan.modal.requesting') }}</span>
        <span v-else-if="status === 'scanning'">{{ t('inventory.scan.modal.scanning') }}</span>
      </div>

      <div class="flex justify-end">
        <SecondaryButton @click="close">{{ t('inventory.scan.modal.cancel') }}</SecondaryButton>
      </div>
    </div>
  </Modal>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
