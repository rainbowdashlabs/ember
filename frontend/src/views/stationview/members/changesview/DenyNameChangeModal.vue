/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import type {NameChangeView} from '@/api/generated/schema'

/**
 * Turns a requested name down, with a reason the member reads in their notification, or none.
 */
const open = defineModel<boolean>({required: true})
const reason = defineModel<string>('reason', {required: true})

defineProps<{
  request: NameChangeView | null
  processing: boolean
}>()

const emit = defineEmits<{
  submit: []
}>()

const {t} = useI18n()
</script>

<template>
  <Modal v-model="open">
    <form class="space-y-4" @submit.prevent="emit('submit')">
      <SectionHeader>{{ t('memberChanges.denyNameTitle') }}</SectionHeader>
      <MutedText v-if="request" tag="p" size="sm">
        {{ t('memberChanges.nameRequestFromTo', {current: request.currentName, requested: request.requestedName}) }}
      </MutedText>
      <div class="space-y-1">
        <FieldLabel>{{ t('memberChanges.denyNameReason') }}</FieldLabel>
        <TextAreaInput v-model="reason" :maxlength="500"/>
        <MutedText tag="p" size="sm">{{ t('memberChanges.denyNameReasonHint') }}</MutedText>
      </div>
      <ButtonRow pair align="end">
        <SecondaryButton type="button" @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <ErrorButton :disabled="processing" type="submit">{{ t('memberChanges.denyName') }}</ErrorButton>
      </ButtonRow>
    </form>
  </Modal>
</template>
