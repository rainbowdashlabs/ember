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
import ArtPicker from '../ArtPicker.vue'
import type {InventorySize} from '@/api/inventory'
import type {InventoryArt} from '@/api/inventoryArts'

const name = defineModel<string>('name', {required: true})
const internalId = defineModel<string>('internalId', {required: true})
const sizeId = defineModel<string>('sizeId', {required: true})
const artId = defineModel<number | null>('artId', {default: null})
const artDraft = defineModel<string>('artDraft', {default: ''})

const props = withDefaults(
    defineProps<{
      sizes: InventorySize[]
      save: () => Promise<void>
      /** The kinds this inventory holds, empty where kinds do not apply. */
      arts?: InventoryArt[]
      showArt?: boolean
    }>(),
    {arts: () => [], showArt: false},
)

const emit = defineEmits<{
  cancel: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="grid gap-4 sm:grid-cols-3">
    <div class="space-y-1">
      <FieldLabel>{{ t('itemDetail.name') }}</FieldLabel>
      <TextInput v-model="name" data-testid="item-edit-name"/>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('itemDetail.internalId') }}</FieldLabel>
      <TextInput v-model="internalId"/>
    </div>
    <div v-if="props.showArt" class="space-y-1">
      <FieldLabel>{{ t('inventory.art.field') }}</FieldLabel>
      <ArtPicker v-model:artId="artId" v-model:draft="artDraft" :arts="props.arts"/>
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
    <SaveButton :action="props.save" data-testid="item-edit-save"/>
    <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
  </div>
</template>
