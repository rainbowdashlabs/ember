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
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {InventoryItem} from '@/api/inventory'
import type {MovementStep} from '@/api/movements'

const {t} = useI18n()

const props = defineProps<{
  step: MovementStep
  /** Free stock to choose from, offered only by the step that names the arriving item. */
  candidates: InventoryItem[]
  /** Whether the viewer may override a party that has not answered. */
  canForce: boolean
  busy: boolean
}>()

const emit = defineEmits<{
  acknowledge: [payload: {note: string; pickedItemId: number | null}]
  force: [payload: {note: string; pickedItemId: number | null}]
  decline: [reason: string]
  cancel: [reason: string]
}>()

const note = ref('')
const pickedItemId = ref('')

const picked = computed(() => (pickedItemId.value ? Number(pickedItemId.value) : null))

/** The step that names the replacement cannot be walked past without one. */
const missingItem = computed(() => props.step.picksItem && picked.value === null)

function payload() {
  return {note: note.value, pickedItemId: picked.value}
}
</script>

<template>
  <div class="mt-2 space-y-2">
    <div v-if="props.step.picksItem" class="space-y-1">
      <FieldLabel>{{ t('movements.pickItem') }}</FieldLabel>
      <SelectInput v-model="pickedItemId">
        <option value="">{{ t('movements.pickItemPlaceholder') }}</option>
        <option v-for="candidate in props.candidates" :key="candidate.id" :value="String(candidate.id)">
          {{ candidate.name }}<template v-if="candidate.internalId"> ({{ candidate.internalId }})</template>
        </option>
      </SelectInput>
    </div>

    <div class="space-y-1">
      <FieldLabel hint>{{ t('movements.note') }}</FieldLabel>
      <TextInput v-model="note" :placeholder="t('movements.notePlaceholder')"/>
    </div>

    <div class="flex flex-wrap gap-2">
      <PrimaryButton
          v-if="props.step.actionable"
          :disabled="props.busy || missingItem"
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
