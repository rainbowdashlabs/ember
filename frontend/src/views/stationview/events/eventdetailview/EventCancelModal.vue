/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import {events} from '@/api'
import {useAsyncAction} from '@/composables/useAsyncAction'

const props = defineProps<{
  show: boolean
  eventId: number
}>()

const emit = defineEmits<{
  close: []
  cancelled: []
}>()

const {t} = useI18n()
const visible = computed({
  get: () => props.show,
  set: (v: boolean) => { if (!v) emit('close') },
})
const cancelReason = ref('')

const {running: cancelling, error: cancelError, run: cancelEvent} = useAsyncAction(async () => {
  await events.cancelEvent(props.eventId, cancelReason.value || undefined)
  cancelReason.value = ''
  emit('cancelled')
})
</script>

<template>
  <Modal v-model="visible">
    <template #header>{{ t('events.cancelEvent') }}</template>
    <div class="space-y-4">
      <p>{{ t('events.cancelConfirm') }}</p>
      <TextAreaInput v-model="cancelReason" :placeholder="t('events.cancelReason')" :rows="3"/>
      <Alert v-if="cancelError" variant="error">{{ cancelError }}</Alert>
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
