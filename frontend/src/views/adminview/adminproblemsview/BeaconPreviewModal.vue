/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import {beacon} from '@/api'
import type {ProblemPayload} from '@/api/beacon'

/**
 * Exactly what would leave this instance, shown before it does.
 *
 * <p>An exception message quotes what failed, and what failed is sometimes an address or a row
 * somebody can be recognised by. Asking an operator to agree to forwarding without showing them the
 * bytes would be asking them to agree to something nobody has read.
 */
const open = defineModel<boolean>('open', {default: false})

const props = defineProps<{problemId: number | null}>()

const emit = defineEmits<{sent: []}>()

const {t} = useI18n()
const payload = ref<ProblemPayload | null>(null)
const loading = ref(false)
const error = ref('')
const sending = ref(false)

watch(open, async value => {
  if (!value || props.problemId == null) return
  loading.value = true
  error.value = ''
  payload.value = null
  try {
    payload.value = await beacon.previewProblem(props.problemId)
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
})

async function send() {
  if (props.problemId == null) return
  sending.value = true
  try {
    await beacon.sendProblem(props.problemId)
    emit('sent')
    open.value = false
  } catch {
    error.value = t('common.error')
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <Modal v-model="open" size="lg">
    <div class="space-y-4">
      <SubHeader>{{ t('beacon.previewTitle') }}</SubHeader>
      <MutedText tag="p" size="sm">{{ t('beacon.previewHint') }}</MutedText>

      <Spinner v-if="loading" size="md"/>
      <FailureAlert :message="error"/>

      <pre v-if="payload" class="max-h-96 overflow-auto rounded-lg bg-bg-light-accent/40 dark:bg-bg-dark-accent/40 p-4 text-xs whitespace-pre-wrap break-words">{{ JSON.stringify(payload, null, 2) }}</pre>

      <div class="flex justify-end gap-2">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!payload || sending" :icon="['fas', 'tower-broadcast']" @click="send">
          {{ sending ? t('common.loading') : t('beacon.send') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
