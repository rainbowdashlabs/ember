/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ItemSearchPicker from '@/components/input/search/ItemSearchPicker.vue'
import HandOutChoice from '@/components/inventory/HandOutChoice.vue'
import type {HandOutMode} from '@/components/inventory/HandOutChoice.vue'
import type {InventoryItem, RequiredInventoryItem} from '@/api/generated/schema'

/**
 * Hands a piece to the member: one out of stock, or a new one written down on the spot.
 *
 * <p>Opened for a gap in the member's equipment it is narrowed to that inventory: the size asked for
 * puts the pieces that fit first, and a new piece can be created in that size when the shelf has none.
 * Opened without one it offers any free piece, since there is no inventory to create a new one in.
 */
const open = defineModel<boolean>({required: true})

const props = defineProps<{
  memberId: number
  /** The gap this hand-out fills, or nothing for a piece of any inventory. */
  requirement: RequiredInventoryItem | null
}>()

const emit = defineEmits<{
  handOut: [itemId: number]
  /** The piece is promised to the member rather than handed over now. */
  planHandOut: [itemId: number, inventoryId: number]
  handOutNew: [inventoryId: number, sizeId: number | null]
}>()

const {t} = useI18n()

const pickedItemId = ref<number | null>(null)
const pickedSize = ref('')
const handOutMode = ref<HandOutMode>('NOW')
const refusal = ref('')

const needsSize = computed(() => !!props.requirement?.hasSizes && props.requirement.sizes.length > 0)
const wantedSizeId = computed(() => pickedSize.value ? Number(pickedSize.value) : null)
const title = computed(() => props.requirement
    ? t('memberDetail.handOutFor', {inventory: props.requirement.inventoryName})
    : t('memberDetail.assignItem'))

watch(open, isOpen => {
  if (!isOpen) return
  pickedItemId.value = null
  pickedSize.value = ''
  refusal.value = ''
})

function nameOf(item: InventoryItem): string {
  return item.name ?? item.internalId ?? `#${item.id}`
}

function onPick(item: InventoryItem) {
  if (item.assignedTo != null) {
    refusal.value = item.assignedTo === props.memberId
        ? t('memberDetail.scanAlreadyHere', {name: nameOf(item)})
        : t('memberDetail.scanAlreadyAssigned', {name: nameOf(item)})
    pickedItemId.value = null
    return
  }
  if (handOutMode.value === 'PLANNED') emit('planHandOut', item.id, item.inventoryId)
  else emit('handOut', item.id)
  open.value = false
}

function createNew() {
  if (!props.requirement) return
  emit('handOutNew', props.requirement.inventoryId, wantedSizeId.value)
  open.value = false
}
</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4" data-testid="hand-out-modal">
      <SubHeader>{{ title }}</SubHeader>
      <Alert v-if="refusal" variant="error">{{ refusal }}</Alert>

      <div v-if="needsSize" class="space-y-1">
        <FieldLabel>{{ t('memberDetail.pickSize') }}</FieldLabel>
        <SelectInput v-model="pickedSize">
          <option value="" disabled>{{ t('memberDetail.pickSize') }}</option>
          <option v-for="size in requirement?.sizes ?? []" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
        </SelectInput>
      </div>

      <HandOutChoice v-model="handOutMode"/>
      <ItemSearchPicker
          v-model="pickedItemId"
          :inventory-id="requirement?.inventoryId ?? null"
          :wanted-size-id="wantedSizeId"
          exclude-assigned
          exclude-lost
          exclude-spoken-for
          :placeholder="t('memberDetail.pickStockItem')"
          @pick="onPick"
      />

      <div v-if="requirement" class="space-y-2 border-t border-bg-light-accent pt-4 dark:border-bg-dark-accent">
        <MutedText tag="p" size="sm">{{ t('memberDetail.createInsteadHint') }}</MutedText>
        <SecondaryButton :icon="['fas', 'plus']" :disabled="needsSize && !pickedSize" @click="createNew">
          {{ t('memberDetail.createAndHandOut') }}
        </SecondaryButton>
      </div>

      <div class="flex justify-end">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
      </div>
    </div>
  </Modal>
</template>
