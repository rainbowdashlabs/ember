/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const open = defineModel<boolean>({required: true})
const maxUses = defineModel<number | undefined>('maxUses', {required: true})
const expiresAt = defineModel<string>('expiresAt', {required: true})

defineProps<{
  creating: boolean
}>()

const emit = defineEmits<{
  (e: 'submit'): void
}>()

const { t } = useI18n()
</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4">
      <SectionHeader>{{ t('waitingList.createInvite') }}</SectionHeader>
      <div class="space-y-1">
        <FieldLabel>{{ t('waitingList.maxUses') }}</FieldLabel>
        <NumberInput v-model="maxUses" :placeholder="t('waitingList.maxUsesPlaceholder')" />
        <p class="text-xs text-(--text-muted)">{{ t('waitingList.maxUsesHint') }}</p>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('waitingList.expiresAt') }}</FieldLabel>
        <DateInput v-model="expiresAt" />
        <p class="text-xs text-(--text-muted)">{{ t('waitingList.expiresAtHint') }}</p>
      </div>
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="creating" @click="emit('submit')">
          {{ creating ? t('common.loading') : t('waitingList.createInvite') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
