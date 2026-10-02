/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useId, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import InventoryKindField from '@/components/inventory/InventoryKindField.vue'
import GearIconPicker from '@/components/input/select/GearIconPicker.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {InventoryType} from '@/api/generated/schema'

const name = defineModel<string>('name', {required: true})
const type = defineModel<InventoryType>('type', {required: true})
const hasSizes = defineModel<boolean>('hasSizes', {required: true})
const homogeneous = defineModel<boolean>('homogeneous', {required: true})
const icon = defineModel<string | null>('icon')
const color = defineModel<string | null>('color')

const emit = defineEmits<{
  cancel: []
  next: []
}>()

const {t} = useI18n()
const hasSizesLabelId = useId()

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
      <option :value="InventoryType.INTERNAL">{{ t('inventory.manage.type.INTERNAL') }}</option>
      <option :value="InventoryType.EXTERNAL">{{ t('inventory.manage.type.EXTERNAL') }}</option>
      <option :value="InventoryType.MIXED">{{ t('inventory.manage.type.MIXED') }}</option>
    </SelectInput>
    <p class="text-xs text-(--text-muted)">{{ t('inventory.manage.typeHint') }}</p>
  </div>

  <GearIconPicker v-model:icon="icon" v-model:color="color" allow-no-icon />

  <InventoryKindField v-model="homogeneous" />

  <div v-if="homogeneous" class="flex items-center justify-between gap-4">
    <div>
      <span :id="hasSizesLabelId" class="text-sm font-medium">{{ t('inventory.manage.hasSizes') }}</span>
      <p class="text-xs text-(--text-muted)">{{ t('inventory.manage.hasSizesHint') }}</p>
    </div>
    <ToggleInput v-model="hasSizes" :aria-labelledby="hasSizesLabelId" data-testid="inventory-has-sizes" />
  </div>

  <ButtonRow pair align="end">
    <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
    <PrimaryButton :disabled="!name.trim()" @click="emit('next')">
      {{ hasSizes ? t('inventory.manage.next') : t('common.save') }}
    </PrimaryButton>
  </ButtonRow>
</template>
