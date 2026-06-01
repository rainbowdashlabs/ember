/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import {events} from '@/api'

const props = defineProps<{
  show: boolean
  eventId: number
}>()

const emit = defineEmits<{
  close: []
  cancelled: []
}>()

const {t} = useI18n()
const cancelReason = ref('')
const cancelling = ref(false)

async function cancelEvent() {
  cancelling.value = true
  try {
    await events.cancelEvent(props.eventId, cancelReason.value || undefined)
    cancelReason.value = ''
    emit('cancelled')
  } finally {
    cancelling.value = false
  }
}
</script>

<template>
  <Modal :show="show" @close="emit('close')">
    <template #header>{{ t('events.cancelEvent') }}</template>
    <div class="space-y-4">
      <p>{{ t('events.cancelConfirm') }}</p>
      <TextAreaInput v-model="cancelReason" :placeholder="t('events.cancelReason')" :rows="3"/>
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="emit('close')">{{ t('common.cancel') }}</SecondaryButton>
        <ErrorButton :disabled="cancelling" @click="cancelEvent">
          <font-awesome-icon :icon="['fas', 'ban']" class="mr-1"/>
          {{ cancelling ? t('common.loading') : t('events.cancelEvent') }}
        </ErrorButton>
      </div>
    </div>
  </Modal>
</template>
