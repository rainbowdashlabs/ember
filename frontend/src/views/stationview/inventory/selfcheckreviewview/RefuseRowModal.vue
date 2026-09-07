/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * Sending one answer back to the member with a reason.
 *
 * <p>This is for an answer that cannot be settled at all, never for one the reviewer merely doubts.
 * Where the record is only stale, putting it right and taking the row is the answer instead.
 */
const show = defineModel<boolean>({required: true})
const reason = defineModel<string>('reason', {required: true})

const props = defineProps<{
  itemName: string
  busy: boolean
  error: string
}>()

const emit = defineEmits<{confirm: []}>()

const {t} = useI18n()

const ready = computed(() => !props.busy && reason.value.trim().length > 0)
</script>

<template>
  <Modal v-model="show">
    <div class="space-y-3">
      <SubHeader>{{ t('selfCheck.review.refuseTitle') }}</SubHeader>
      <p class="text-sm">{{ itemName }}</p>
      <MutedText size="sm" tag="p">{{ t('selfCheck.review.refuseHint') }}</MutedText>
      <TextAreaInput
          v-model="reason"
          :placeholder="t('selfCheck.review.refuseReasonPlaceholder')"
          :rows="3"
          data-testid="review-refuse-reason"
      />
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="show = false">{{ t('common.cancel') }}</SecondaryButton>
        <ErrorButton :disabled="!ready" data-testid="review-refuse-confirm" @click="emit('confirm')">
          {{ t('selfCheck.review.refuse') }}
        </ErrorButton>
      </div>
    </div>
  </Modal>
</template>
