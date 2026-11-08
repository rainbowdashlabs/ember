/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ExchangeReasonPicker from '@/components/movement/ExchangeReasonPicker.vue'
import type {NamedPiece} from '@/api/inventory'
import type {InventorySize} from '@/api/generated/schema'
import type {Failure} from '@/util/failure'

const modelValue = defineModel<boolean>({required: true})
const reason = defineModel<string>('reason', {required: true})
const newSizeId = defineModel<number | null>('newSizeId', {required: true})

/**
 * Asking for a piece to be swapped while answering a self-check.
 *
 * <p>Its own dialogue rather than the wizard, because the check already knows the answers the wizard
 * asks for. Why and which size are asked the way every swap asks them, from the size the member says
 * the piece is.
 */
const props = defineProps<{
  item: NamedPiece | null
  sizes: InventorySize[]
  submitting: boolean
  failure: Failure | null
}>()

const emit = defineEmits<{
  cancel: []
  submit: []
}>()

const {t} = useI18n()

const opened = ref(0)

watch(modelValue, open => {
  if (open) opened.value++
})

const ready = computed(() => !props.submitting && reason.value.trim() !== '')
</script>

<template>
  <Modal v-model="modelValue">
    <div class="space-y-3">
      <SubHeader>{{ t('profile.requestExchange') }}</SubHeader>
      <p v-if="item" class="text-sm">{{ item.inventoryName }} - {{ item.name }}</p>
      <ExchangeReasonPicker
          :key="opened"
          v-model:new-size-id="newSizeId"
          v-model:text="reason"
          :sizes="sizes"
          :current-size-id="item?.sizeId ?? null"
      />
      <FailureAlert :failure="failure"/>
      <ButtonRow pair align="end">
        <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!ready" data-testid="exchange-submit" @click="emit('submit')">
          {{ t('profile.submitExchange') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
