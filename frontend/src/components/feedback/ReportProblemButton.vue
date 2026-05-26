/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from './Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import Alert from './Alert.vue'
import {submitReport} from '@/api/problemReports'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const {sessionInfo} = useSession()

const showModal = ref(false)
const message = ref('')
const sending = ref(false)
const sent = ref(false)
const error = ref('')

async function send() {
  if (!message.value.trim()) return
  sending.value = true
  error.value = ''
  try {
    await submitReport(message.value.trim(), sessionInfo.value)
    sent.value = true
    message.value = ''
    setTimeout(() => {
      showModal.value = false
      sent.value = false
    }, 2000)
  } catch {
    error.value = t('common.error')
  } finally {
    sending.value = false
  }
}

function open() {
  sent.value = false
  error.value = ''
  showModal.value = true
}
</script>

<template>
  <button
      type="button"
      class="fixed bottom-4 right-4 z-40 h-10 w-10 rounded-full bg-error text-white shadow-lg hover:bg-error/80 transition-colors flex items-center justify-center"
      :title="t('problemReport.button')"
      @click="open"
  >
    <font-awesome-icon :icon="['fas', 'bug']" class="h-4 w-4"/>
  </button>

  <Modal v-model="showModal">
    <template #title>{{ t('problemReport.title') }}</template>
    <div class="space-y-4">
      <p class="text-sm text-(--text-muted)">{{ t('problemReport.hint') }}</p>
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="sent" variant="success">{{ t('problemReport.sent') }}</Alert>
      <template v-if="!sent">
        <TextAreaInput v-model="message" :rows="4" :placeholder="t('problemReport.placeholder')"/>
        <p class="text-xs text-(--text-muted)">{{ t('problemReport.autoCapture') }}</p>
        <div class="flex justify-end gap-2">
          <SecondaryButton @click="showModal = false">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton :disabled="sending || !message.trim()" @click="send">
            {{ sending ? t('common.loading') : t('problemReport.send') }}
          </PrimaryButton>
        </div>
      </template>
    </div>
  </Modal>
</template>
