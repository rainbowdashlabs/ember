/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ItemSearchPicker from '@/components/input/search/ItemSearchPicker.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {InventorySize} from '@/api/inventory'
import type {MovementStep, NewItemRequest} from '@/api/movements'

const {t} = useI18n()

const props = defineProps<{
  step: MovementStep
  /** Whether the viewer may override a party that has not answered. */
  canForce: boolean
  /**
   * Whether the piece that arrives may be written down here. True where the owner is a body outside
   * Ember: it names nothing, so there is nothing to pick and the chain would stop on this step.
   */
  mayRecord: boolean
  /** The sizes the movement's inventory keeps, for the piece being written down. */
  sizes: InventorySize[]
  /** The size the exchange asked for, which the piece written down starts out as. */
  wantedSizeId?: number | null
  busy: boolean
}>()

const emit = defineEmits<{
  acknowledge: [payload: AcknowledgePayload]
  force: [payload: AcknowledgePayload]
  decline: [reason: string]
  cancel: [reason: string]
}>()

/** What the step is answered with: a piece already known, or one written down on the spot. */
export interface AcknowledgePayload {
  note: string
  pickedItemId: number | null
  newItem: NewItemRequest | null
}

const note = ref('')
const searched = ref<number | null>(null)
const recording = ref(false)
const newName = ref('')
const newInternalId = ref('')
const newSizeId = ref(String(props.wantedSizeId ?? ''))

const picked = computed(() => (recording.value ? null : searched.value))

/**
 * Recording and picking are the two answers to the same step, so choosing one drops the other.
 *
 * <p>Opening the form fills the size in from what the exchange asked for and leaves it editable: what
 * an association sends is not always what was asked for, and the record has to be able to say so.
 */
function toggleRecording() {
  recording.value = !recording.value
  if (!recording.value) return
  searched.value = null
  newSizeId.value = String(props.wantedSizeId ?? '')
}

/** The step that names the arriving piece cannot be walked past without one. */
const missingItem = computed(() => {
  if (!props.step.picksItem) return false
  if (recording.value) return !newName.value.trim()
  return picked.value == null
})

function payload(): AcknowledgePayload {
  return {
    note: note.value,
    pickedItemId: picked.value,
    newItem: recording.value
        ? {
          name: newName.value.trim(),
          internalId: newInternalId.value.trim() || undefined,
          sizeId: newSizeId.value ? Number(newSizeId.value) : null,
        }
        : null,
  }
}
</script>

<template>
  <div class="mt-2 space-y-2">
    <div v-if="props.step.picksItem" class="space-y-2">
      <FieldLabel>{{ t('movements.pickItem') }}</FieldLabel>

      <ItemSearchPicker
          v-if="!recording"
          v-model="searched"
          :disabled="props.busy"
          exclude-lost
          :placeholder="t('movements.pickItemPlaceholder')"
      />

      <div v-if="props.mayRecord" class="flex items-center gap-2">
        <SecondaryButton :disabled="props.busy" data-testid="movement-record-arrival" @click="toggleRecording">
          {{ recording ? t('common.cancel') : t('movements.recordArrival') }}
        </SecondaryButton>
      </div>
    </div>

    <div v-if="recording" class="space-y-2 rounded-md border border-(--border) p-2">
      <p class="text-xs text-(--text-muted)">{{ t('movements.recordArrivalHint') }}</p>
      <div class="space-y-1">
        <FieldLabel>{{ t('movements.arrivalName') }}</FieldLabel>
        <TextInput v-model="newName" data-testid="movement-new-name"/>
      </div>
      <div class="space-y-1">
        <FieldLabel hint>{{ t('movements.arrivalInternalId') }}</FieldLabel>
        <TextInput v-model="newInternalId" data-testid="movement-new-internal-id"/>
      </div>
      <div v-if="props.sizes.length > 0" class="space-y-1">
        <FieldLabel hint>{{ t('movements.arrivalSize') }}</FieldLabel>
        <SelectInput v-model="newSizeId" data-testid="movement-new-size">
          <option value="">{{ t('movements.arrivalNoSize') }}</option>
          <option v-for="size in props.sizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
        </SelectInput>
      </div>
    </div>

    <div class="space-y-1">
      <FieldLabel hint>{{ t('movements.note') }}</FieldLabel>
      <TextInput v-model="note" :placeholder="t('movements.notePlaceholder')"/>
    </div>

    <div class="flex flex-wrap gap-2">
      <PrimaryButton
          v-if="props.step.actionable"
          :disabled="props.busy || missingItem"
          data-testid="movement-acknowledge"
          @click="emit('acknowledge', payload())"
      >
        {{ t('movements.acknowledge') }}
      </PrimaryButton>
      <SecondaryButton
          v-if="props.canForce && !props.step.actionable"
          :disabled="props.busy || !note || missingItem"
          @click="emit('force', payload())"
      >
        {{ t('movements.force') }}
      </SecondaryButton>
      <SecondaryButton :disabled="props.busy" @click="emit('decline', note)">
        {{ t('movements.decline') }}
      </SecondaryButton>
      <SecondaryButton v-if="props.step.actionable" :disabled="props.busy" @click="emit('cancel', note)">
        {{ t('movements.cancel') }}
      </SecondaryButton>
    </div>

    <p v-if="props.canForce && !props.step.actionable" class="text-xs text-(--text-muted)">
      {{ t('movements.forceHint') }}
    </p>
  </div>
</template>
