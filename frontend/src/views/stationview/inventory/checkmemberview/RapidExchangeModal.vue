/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {InventorySize} from '@/api/inventory'

/** Which of the two reasons the check offered, which is what the form arrives filled in for. */
export type RapidExchangeKind = 'size' | 'damaged'

/**
 * The exchange a check raises on the spot, filled in from what the check already knows.
 *
 * <p>Somebody walking a check has the member in front of them and the piece in their hands. Sending
 * them to the exchange screen to type in what they are looking at is how a check that found something
 * ends with nothing recorded. The two reasons it offers are the two that come up: it does not fit, and
 * it is damaged.
 *
 * <p>Everything stays editable. The size one size up is a guess from the order the inventory keeps its
 * sizes in, and a guess that cannot be corrected is worse than no guess.
 */
const props = defineProps<{
  itemName?: string
  /** The sizes the inventory keeps, smallest first. */
  sizes: InventorySize[]
  currentSizeId?: number | null
  kind: RapidExchangeKind
  busy?: boolean
  error?: string
}>()

const show = defineModel<boolean>({required: true})

const emit = defineEmits<{
  confirm: [payload: {newSizeId: number | null; reason: string}]
}>()

const {t} = useI18n()

const newSizeId = ref('')
const reason = ref('')

/**
 * One size up, read off the order the inventory keeps.
 *
 * <p>The sizes are ordered smallest first, so the next one along is the next larger. Where the piece is
 * already the largest, or the inventory keeps no sizes at all, it stays as it is and whoever raised the
 * exchange says in words what they need.
 */
const oneSizeUp = computed(() => {
  if (!props.currentSizeId) return null
  const at = props.sizes.findIndex(size => size.id === props.currentSizeId)
  if (at < 0 || at + 1 >= props.sizes.length) return null
  return props.sizes[at + 1] ?? null
})

watch(show, visible => {
  if (!visible) return
  const wanted = props.kind === 'size' ? oneSizeUp.value?.id ?? props.currentSizeId : props.currentSizeId
  newSizeId.value = wanted ? String(wanted) : ''
  reason.value = props.kind === 'size'
      ? t('inventory.check.exchangeSizeReason')
      : t('inventory.check.exchangeDamagedReason')
})

function confirm() {
  if (!reason.value.trim()) return
  emit('confirm', {
    newSizeId: newSizeId.value ? Number(newSizeId.value) : null,
    reason: reason.value.trim(),
  })
}
</script>

<template>
  <Modal v-model="show">
    <SubHeader class="mb-1">
      {{ props.kind === 'size' ? t('inventory.check.exchangeSize') : t('inventory.check.exchangeDamaged') }}
    </SubHeader>
    <p class="mb-3 text-sm text-(--text-muted)">{{ props.itemName }}</p>

    <Alert v-if="props.error" variant="error" class="mb-3">{{ props.error }}</Alert>

    <div class="space-y-3">
      <div v-if="props.sizes.length > 0" class="space-y-1">
        <FieldLabel>{{ t('inventory.check.exchangeNewSize') }}</FieldLabel>
        <SelectInput v-model="newSizeId" class="w-full" data-testid="rapid-exchange-size">
          <option value="">{{ t('common.unisize') }}</option>
          <option v-for="size in props.sizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
        </SelectInput>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('inventory.check.exchangeReason') }}</FieldLabel>
        <TextInput v-model="reason" class="w-full" data-testid="rapid-exchange-reason"/>
      </div>
    </div>

    <div class="mt-4 flex justify-end gap-2">
      <SecondaryButton @click="show = false">{{ t('common.cancel') }}</SecondaryButton>
      <PrimaryButton
          :disabled="props.busy || !reason.trim()"
          data-testid="rapid-exchange-confirm"
          @click="confirm"
      >
        {{ t('inventory.check.exchangeCreate') }}
      </PrimaryButton>
    </div>
  </Modal>
</template>
