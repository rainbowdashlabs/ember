/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {watch} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import InventoryKindField from '@/components/inventory/InventoryKindField.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {InventoryTypes, type InventoryTypeName} from '@/api/inventory'

const name = defineModel<string>('name', {required: true})
const type = defineModel<InventoryTypeName>('type', {required: true})
const hasSizes = defineModel<boolean>('hasSizes', {required: true})
const homogeneous = defineModel<boolean>('homogeneous', {required: true})

const emit = defineEmits<{
  cancel: []
  next: []
}>()

const {t} = useI18n()

/** A collection keeps no size list, so the one control follows the other. */
watch(homogeneous, value => {
  if (!value) hasSizes.value = false
})
</script>

<template>
  <div class="space-y-1">
    <FieldLabel>{{ t('inventory.manage.name') }}</FieldLabel>
    <TextInput v-model="name" data-testid="inventory-name" :placeholder="t('inventory.manage.namePlaceholder')" />
  </div>

  <div class="space-y-1">
    <FieldLabel>{{ t('inventory.manage.typeLabel') }}</FieldLabel>
    <SelectInput v-model="type">
      <option :value="InventoryTypes.INTERNAL">{{ t('inventory.manage.type.INTERNAL') }}</option>
      <option :value="InventoryTypes.EXTERNAL">{{ t('inventory.manage.type.EXTERNAL') }}</option>
      <option :value="InventoryTypes.MIXED">{{ t('inventory.manage.type.MIXED') }}</option>
    </SelectInput>
    <p class="text-xs text-(--text-muted)">{{ t('inventory.manage.typeHint') }}</p>
  </div>

  <InventoryKindField v-model="homogeneous" />

  <div v-if="homogeneous" class="flex items-center justify-between gap-4">
    <div>
      <label class="text-sm font-medium">{{ t('inventory.manage.hasSizes') }}</label>
      <p class="text-xs text-(--text-muted)">{{ t('inventory.manage.hasSizesHint') }}</p>
    </div>
    <ToggleInput v-model="hasSizes" data-testid="inventory-has-sizes" />
  </div>

  <div class="flex justify-end gap-3">
    <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
    <PrimaryButton :disabled="!name.trim()" @click="emit('next')">
      {{ hasSizes ? t('inventory.manage.next') : t('common.save') }}
    </PrimaryButton>
  </div>
</template>
