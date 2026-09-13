/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import type {InventorySize} from '@/api/inventory'

/**
 * Why, in the words of whoever is starting it, and the size where a swap asks for a different one.
 *
 * <p>Every chain carries the reason, and it is the only part of a movement nobody else can write later.
 */
const props = defineProps<{
  /** Whether a different size can be asked for, which only a swap does. */
  asksForASize: boolean
  sizes: InventorySize[]
}>()

const reason = defineModel<string>('reason', {required: true})
const newSizeId = defineModel<number | null>('newSizeId', {required: true})

const {t} = useI18n()

const sizeChoice = computed({
  get: () => (newSizeId.value != null ? String(newSizeId.value) : ''),
  set: value => {
    newSizeId.value = value ? Number(value) : null
  },
})
</script>

<template>
  <div class="space-y-3">
    <SubHeader>{{ t('movements.wizard.reason.title') }}</SubHeader>

    <div class="space-y-1">
      <FieldLabel>{{ t('movements.wizard.reason.label') }}</FieldLabel>
      <TextAreaInput v-model="reason" data-testid="wizard-reason"
                     :placeholder="t('movements.wizard.reason.placeholder')"/>
    </div>

    <div v-if="props.asksForASize && props.sizes.length > 0" class="space-y-1">
      <FieldLabel>{{ t('movements.wizard.reason.newSize') }}</FieldLabel>
      <SelectInput v-model="sizeChoice" data-testid="wizard-new-size">
        <option value="">{{ t('movements.wizard.subject.anySize') }}</option>
        <option v-for="size in props.sizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
      </SelectInput>
    </div>
  </div>
</template>
