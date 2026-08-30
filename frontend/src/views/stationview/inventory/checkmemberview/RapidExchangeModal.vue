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

/** Why a piece is being exchanged. Anything that is neither of the two common ones is said in words. */
type ExchangeReason = 'tooSmall' | 'damaged' | 'other'

/**
 * The exchange a check raises on the spot, filled in from what the check already knows.
 *
 * <p>Somebody walking a check has the member in front of them and the piece in their hands. Sending
 * them to the exchange screen to type in what they are looking at is how a check that found something
 * ends with nothing recorded.
 *
 * <p>The reason is asked here rather than by having a button per reason outside. Two buttons covered
 * the two common cases and left no room for the third, and every reason added would have been another
 * button on a screen meant to hold one decision.
 *
 * <p>Everything stays editable. The size one size up is a guess from the order the inventory keeps its
 * sizes in, and a guess that cannot be corrected is worse than no guess.
 */
const props = defineProps<{
  itemName?: string
  /** The sizes the inventory keeps, smallest first. */
  sizes: InventorySize[]
  currentSizeId?: number | null
  busy?: boolean
  error?: string
}>()

const show = defineModel<boolean>({required: true})

const emit = defineEmits<{
  confirm: [payload: {newSizeId: number | null; reason: string}]
}>()

const {t} = useI18n()

const reasonKind = ref<ExchangeReason>('tooSmall')
const ownReason = ref('')
const newSizeId = ref('')

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

/** A piece that is too small wants the next size; anything else wants the same one back. */
function sizeForReason(kind: ExchangeReason): string {
  const wanted = kind === 'tooSmall' ? oneSizeUp.value?.id ?? props.currentSizeId : props.currentSizeId
  return wanted ? String(wanted) : ''
}

watch(show, visible => {
  if (!visible) return
  reasonKind.value = 'tooSmall'
  ownReason.value = ''
  newSizeId.value = sizeForReason('tooSmall')
})

function chooseReason(kind: ExchangeReason) {
  reasonKind.value = kind
  newSizeId.value = sizeForReason(kind)
}

/** What is written down as the reason: the chosen one in words, or what was typed instead. */
const reasonText = computed(() => {
  if (reasonKind.value === 'tooSmall') return t('inventory.check.exchangeReasonTooSmallText')
  if (reasonKind.value === 'damaged') return t('inventory.check.exchangeReasonDamagedText')
  return ownReason.value.trim()
})

function confirm() {
  if (!reasonText.value) return
  emit('confirm', {
    newSizeId: newSizeId.value ? Number(newSizeId.value) : null,
    reason: reasonText.value,
  })
}
</script>

<template>
  <Modal v-model="show">
    <SubHeader class="mb-1">{{ t('inventory.check.exchange') }}</SubHeader>
    <p class="mb-3 text-sm text-(--text-muted)">{{ props.itemName }}</p>

    <Alert v-if="props.error" variant="error" class="mb-3">{{ props.error }}</Alert>

    <div class="space-y-3">
      <div class="space-y-1">
        <FieldLabel>{{ t('inventory.check.exchangeReason') }}</FieldLabel>
        <div class="flex flex-wrap gap-2">
          <SecondaryButton
              :class="{'ring-2 ring-(--accent)': reasonKind === 'tooSmall'}"
              data-testid="rapid-exchange-reason-too-small"
              @click="chooseReason('tooSmall')"
          >
            {{ t('inventory.check.exchangeReasonTooSmall') }}
          </SecondaryButton>
          <SecondaryButton
              :class="{'ring-2 ring-(--accent)': reasonKind === 'damaged'}"
              data-testid="rapid-exchange-reason-damaged"
              @click="chooseReason('damaged')"
          >
            {{ t('inventory.check.exchangeReasonDamaged') }}
          </SecondaryButton>
          <SecondaryButton
              :class="{'ring-2 ring-(--accent)': reasonKind === 'other'}"
              data-testid="rapid-exchange-reason-other"
              @click="chooseReason('other')"
          >
            {{ t('inventory.check.exchangeReasonOther') }}
          </SecondaryButton>
        </div>
      </div>

      <div v-if="reasonKind === 'other'" class="space-y-1">
        <FieldLabel>{{ t('inventory.check.exchangeOwnReason') }}</FieldLabel>
        <TextInput v-model="ownReason" class="w-full" data-testid="rapid-exchange-reason"/>
      </div>

      <div v-if="props.sizes.length > 0" class="space-y-1">
        <FieldLabel>{{ t('inventory.check.exchangeNewSize') }}</FieldLabel>
        <SelectInput v-model="newSizeId" class="w-full" data-testid="rapid-exchange-size">
          <option value="">{{ t('common.unisize') }}</option>
          <option v-for="size in props.sizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
        </SelectInput>
      </div>
    </div>

    <div class="mt-4 flex justify-end gap-2">
      <SecondaryButton @click="show = false">{{ t('common.cancel') }}</SecondaryButton>
      <PrimaryButton
          :disabled="props.busy || !reasonText"
          data-testid="rapid-exchange-confirm"
          @click="confirm"
      >
        {{ t('inventory.check.exchangeCreate') }}
      </PrimaryButton>
    </div>
  </Modal>
</template>
