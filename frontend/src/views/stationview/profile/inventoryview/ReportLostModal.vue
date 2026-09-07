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
import SizeBadge from '@/components/badge/SizeBadge.vue'
import type {NamedPiece} from '@/api/inventory'

/**
 * Saying that a piece of gear cannot be found.
 *
 * <p>It is not a request and nobody answers it: the item counts as missing from the moment it is
 * submitted, and it can be marked found again. Asking for a replacement is a separate act the station
 * takes afterwards, which is why nothing here mentions one.
 */
const modelValue = defineModel<boolean>({required: true})
const note = defineModel<string>('note', {required: true})

const props = defineProps<{
  item: NamedPiece | null
  /** Whether this station asks for a note before it accepts the report. */
  noteRequired: boolean
  submitting: boolean
  error: string
}>()

const emit = defineEmits<{
  cancel: []
  submit: []
}>()

const {t} = useI18n()

const canSubmit = computed(() => !props.submitting && (!props.noteRequired || note.value.trim().length > 0))
</script>

<template>
  <Modal v-model="modelValue">
    <div class="space-y-3" data-testid="report-lost-modal">
      <SubHeader>{{ t('profile.reportLost') }}</SubHeader>
      <p v-if="item" class="text-sm">
        {{ item.inventoryName }} - {{ item.name }}
        <SizeBadge v-if="item.sizeName">{{ item.sizeName }}</SizeBadge>
      </p>
      <MutedText size="sm">{{ t('profile.reportLostHint') }}</MutedText>
      <TextAreaInput
          v-model="note"
          :placeholder="noteRequired ? t('profile.lostNoteRequired') : t('profile.lostNotePlaceholder')"
          :rows="3"
          data-testid="report-lost-note"
      />
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
        <ErrorButton :disabled="!canSubmit" data-testid="report-lost-submit" @click="emit('submit')">
          {{ t('profile.submitLost') }}
        </ErrorButton>
      </div>
    </div>
  </Modal>
</template>
