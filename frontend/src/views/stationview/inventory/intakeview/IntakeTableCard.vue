/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Alert from '@/components/feedback/Alert.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import MemberPicker, {type PickableMember} from '@/views/stationview/members/MemberPicker.vue'
import IntakeTable from './IntakeTable.vue'
import type {IntakeLine} from './intakeLines'
import {ItemOwner, type InventorySize, type ItemOwnerName} from '@/api/inventory'
import type {InventoryFieldDefinition} from '@/api/inventoryFields'

/**
 * The filled-in half of a stock-taking: the table, who else can be added to it, and saving it.
 *
 * <p>Apart from the view because the view is about loading the people and sending the result, and
 * this is about what the reader types.
 */
const lines = defineModel<IntakeLine[]>('lines', {required: true})
const bulkSize = defineModel<string>('bulkSize', {required: true})
const ownerKind = defineModel<ItemOwnerName>('ownerKind', {required: true})

defineProps<{
  sizes: InventorySize[]
  fields: InventoryFieldDefinition[]
  hasSizes: boolean
  /** Whether this inventory holds the station's gear as well as the association's, so it is asked. */
  holdsBoth: boolean
  pickable: PickableMember[]
  userTypes: string[]
  filled: number
  saving: boolean
  saveError?: string
}>()

const emit = defineEmits<{
  (e: 'applyToEmpty'): void
  (e: 'add', memberId: number): void
  (e: 'save'): void
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div v-if="holdsBoth" class="space-y-1">
      <FieldLabel>{{ t('inventory.intake.owner') }}</FieldLabel>
      <SelectInput v-model="ownerKind" class="w-56" data-testid="intake-owner">
        <option :value="ItemOwner.STATION">{{ t('inventory.intake.ownerStation') }}</option>
        <option :value="ItemOwner.CLUSTER">{{ t('inventory.intake.ownerCluster') }}</option>
      </SelectInput>
    </div>

    <IntakeTable
        v-model:lines="lines"
        v-model:bulk-size="bulkSize"
        :sizes="sizes"
        :fields="fields"
        :has-sizes="hasSizes"
        @apply-to-empty="emit('applyToEmpty')"
    />

    <div class="space-y-2">
      <MutedText size="sm" tag="p">{{ t('inventory.intake.addByHand') }}</MutedText>
      <MemberPicker
          :members="pickable"
          :user-types="userTypes"
          :placeholder="t('inventory.intake.addByHandPlaceholder')"
          @select="memberId => emit('add', memberId)"
      />
    </div>

    <div class="space-y-2">
      <Alert v-if="saveError" variant="error" data-testid="intake-error">{{ saveError }}</Alert>
      <div class="flex flex-wrap items-center justify-between gap-2">
        <MutedText size="sm">{{ t('inventory.intake.filled', {count: filled}) }}</MutedText>
        <PrimaryButton :disabled="saving || filled === 0" data-testid="intake-save" @click="emit('save')">
          {{ saving ? t('common.loading') : t('inventory.intake.save') }}
        </PrimaryButton>
      </div>
    </div>
  </NeutralContainer>
</template>
