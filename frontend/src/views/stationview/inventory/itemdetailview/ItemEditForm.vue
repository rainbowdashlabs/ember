/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {InventorySize} from '@/api/inventory'

const name = defineModel<string>('name', {required: true})
const internalId = defineModel<string>('internalId', {required: true})
const sizeId = defineModel<string>('sizeId', {required: true})

const props = defineProps<{
  sizes: InventorySize[]
  save: () => Promise<void>
}>()

const emit = defineEmits<{
  cancel: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="grid gap-4 sm:grid-cols-3">
    <div class="space-y-1">
      <FieldLabel>{{ t('itemDetail.name') }}</FieldLabel>
      <TextInput v-model="name"/>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('itemDetail.internalId') }}</FieldLabel>
      <TextInput v-model="internalId"/>
    </div>
    <div v-if="props.sizes.length > 0" class="space-y-1">
      <FieldLabel>{{ t('itemDetail.size') }}</FieldLabel>
      <SelectInput v-model="sizeId">
        <option value="">&#x2014;</option>
        <option v-for="s in props.sizes" :key="s.id" :value="String(s.id)">{{ s.label }}</option>
      </SelectInput>
    </div>
  </div>
  <div class="flex gap-2">
    <SaveButton :action="props.save"/>
    <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
  </div>
</template>
