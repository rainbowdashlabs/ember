/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import type {InventorySize, NamedPiece} from '@/api/inventory'

const modelValue = defineModel<boolean>({required: true})
const reason = defineModel<string>('reason', {required: true})
const newSizeId = defineModel<string>('newSizeId', {required: true})

/**
 * Asking for a piece to be swapped while answering a self-check.
 *
 * <p>Its own dialogue rather than the wizard, because the check already knows the answers the wizard
 * asks for and knows one more it does not: why. A piece that no longer fits and a piece that is broken
 * are two different requests, and the answer decides the size as well as the words: another size is the
 * point of the first, and the same size back is the ordinary outcome of the second.
 */
const props = withDefaults(
    defineProps<{
      item: NamedPiece | null
      sizes: InventorySize[]
      submitting: boolean
      error: string
      /** Why the piece is being swapped, where the screen knows. Empty where the member says it themselves. */
      cause?: string
      /** Whether a different size is the point of it, which is what makes naming one unavoidable. */
      sizeRequired?: boolean
    }>(),
    {cause: '', sizeRequired: true},
)

const emit = defineEmits<{
  cancel: []
  submit: []
}>()

const {t} = useI18n()

const ready = computed(() => {
  if (props.submitting) return false
  if (!props.cause && !reason.value.trim()) return false
  return !props.sizeRequired || props.sizes.length === 0 || newSizeId.value !== ''
})
</script>

<template>
  <Modal v-model="modelValue">
    <div class="space-y-3">
      <SubHeader>{{ t('profile.requestExchange') }}</SubHeader>
      <p class="text-sm" v-if="item">
        {{ item.inventoryName }} - {{ item.name }}
        <SizeBadge>{{ item.sizeName ?? t('common.unisize') }}</SizeBadge>
      </p>
      <p v-if="cause" class="text-sm" data-testid="exchange-cause">{{ cause }}</p>
      <div v-if="sizes.length > 0" class="space-y-1">
        <FieldLabel>{{ t('movements.newSize') }}</FieldLabel>
        <SelectInput v-model="newSizeId" class="w-full" data-testid="exchange-new-size">
          <option value="" :disabled="sizeRequired">
            {{ sizeRequired ? t('movements.selectNewSize') : t('movements.noSize') }}
          </option>
          <option v-for="size in sizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
        </SelectInput>
      </div>
      <TextAreaInput
          v-model="reason"
          :placeholder="cause ? t('profile.exchangeExtraPlaceholder') : t('profile.exchangeReasonPlaceholder')"
          :rows="3"
          data-testid="exchange-reason"
      />
      <FailureAlert :message="error"/>
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!ready" data-testid="exchange-submit" @click="emit('submit')">
          {{ t('profile.submitExchange') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
