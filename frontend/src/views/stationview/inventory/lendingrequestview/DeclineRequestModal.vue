/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import type {Failure} from '@/util/failure'

/** Declines a lending request with a reason the asking station reads. */
const open = defineModel<boolean>({required: true})

defineProps<{
  /** Whether the borrowing station withdraws its own request rather than the lender declining it. */
  withdraw?: boolean
  /** Whether a decline is on its way, which holds the button back. */
  busy?: boolean
  /** Why the last decline failed, shown in the dialog where it is seen. */
  failure?: Failure | null
}>()

const emit = defineEmits<{
  decline: [reason: string]
}>()

const {t} = useI18n()
const reason = ref('')
/** Whether this dialog sent a decline since it opened, so only its own failure shows. */
const pressed = ref(false)
watch(open, value => {
  if (!value) return
  reason.value = ''
  pressed.value = false
})

function decline() {
  pressed.value = true
  emit('decline', reason.value)
}
</script>

<template>
  <Modal v-model="open" data-confirm-always>
    <SectionHeader class="mb-4">{{ withdraw ? t('lending.withdrawTitle') : t('lending.declineTitle') }}</SectionHeader>
    <TextAreaInput v-model="reason" :placeholder="withdraw ? t('lending.withdrawReasonPlaceholder') : t('lending.declineReasonPlaceholder')" :rows="3"/>
    <FailureAlert :failure="pressed ? failure ?? null : null" class="mt-3"/>
    <ButtonRow pair align="end" class="mt-4">
      <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
      <ErrorButton :disabled="busy" @click="decline">{{ withdraw ? t('lending.withdraw') : t('lending.decline') }}</ErrorButton>
    </ButtonRow>
  </Modal>
</template>
