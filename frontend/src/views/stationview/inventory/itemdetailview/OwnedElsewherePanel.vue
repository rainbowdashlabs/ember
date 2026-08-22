/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {InventoryItem} from '@/api/inventory'
import {MovementPurpose} from '@/api/movements'
import {movements} from '@/api'
import {useAsyncAction} from '@/composables/useAsyncAction'

/**
 * What a station may do with a piece of gear that belongs to the association above it.
 *
 * <p>Not describing it, which is the association's, and not passing it on to anybody else, which is
 * not the station's to give. What is left is handing it back, and asking for a different one. Both
 * start a movement that the association answers at its end.
 */
const props = defineProps<{
  item: InventoryItem
}>()

const emit = defineEmits<{
  started: []
}>()

const {t} = useI18n()

const asking = ref<'RETURN' | 'EXCHANGE' | null>(null)
const reason = ref('')

const {running, error, run: start} = useAsyncAction(async () => {
  if (!asking.value) return
  await movements.createMovement({
    purpose: asking.value === 'RETURN' ? MovementPurpose.RETURN : MovementPurpose.EXCHANGE,
    outgoingItemId: props.item.id,
    reason: reason.value.trim() || undefined,
  })
  asking.value = null
  reason.value = ''
  emit('started')
})
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SectionHeader>{{ t('itemDetail.ownedElsewhereTitle') }}</SectionHeader>
    <p class="text-sm text-(--text-muted)">{{ t('itemDetail.ownedElsewhereHint') }}</p>

    <div class="flex flex-wrap gap-2">
      <SecondaryButton :icon="['fas', 'rotate-left']" @click="asking = 'RETURN'">
        {{ t('itemDetail.handBack') }}
      </SecondaryButton>
      <SecondaryButton :icon="['fas', 'right-left']" @click="asking = 'EXCHANGE'">
        {{ t('itemDetail.askExchange') }}
      </SecondaryButton>
    </div>

    <Modal v-if="asking" model-value @update:model-value="(v) => { if (!v) asking = null }">
      <div class="space-y-4">
        <SectionHeader>
          {{ asking === 'RETURN' ? t('itemDetail.handBack') : t('itemDetail.askExchange') }}
        </SectionHeader>
        <Alert v-if="error" variant="error">{{ error }}</Alert>

        <div class="space-y-1">
          <FieldLabel>{{ t('itemDetail.movementReason') }}</FieldLabel>
          <TextAreaInput v-model="reason" :placeholder="t('itemDetail.movementReasonPlaceholder')"/>
        </div>

        <div class="flex justify-end gap-3">
          <SecondaryButton :disabled="running" @click="asking = null">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton :disabled="running" @click="start">
            {{ running ? t('common.loading') : t('common.send') }}
          </PrimaryButton>
        </div>
      </div>
    </Modal>
  </NeutralContainer>
</template>
