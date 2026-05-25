/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import {InventoryTypes} from '@/api/types'
import type {InventoryDetail, InventoryTypeName} from '@/api/types'
import {inventory} from '@/api'

const {t} = useI18n()

const props = defineProps<{
  detail: InventoryDetail
}>()

const emit = defineEmits<{
  saved: []
  error: [message: string]
}>()

const editName = ref(props.detail.name ?? '')
const editType = ref<string>(props.detail.inventoryType ?? InventoryTypes.INTERNAL)
const savingSettings = ref(false)

async function saveSettings() {
  savingSettings.value = true
  try {
    await inventory.updateInventory(props.detail.id, {
      name: editName.value,
      inventoryType: editType.value as InventoryTypeName,
      hasSizes: props.detail.hasSizes ?? false,
    })
    emit('saved')
  } catch {
    emit('error', t('common.error'))
  } finally {
    savingSettings.value = false
  }
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('inventory.edit.settings') }}</SubHeader>
    <div class="grid gap-4 sm:grid-cols-2">
      <div class="space-y-1">
        <FieldLabel>{{ t('inventory.manage.name') }}</FieldLabel>
        <TextInput v-model="editName"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('inventory.manage.typeLabel') }}</FieldLabel>
        <SelectInput v-model="editType">
          <option :value="InventoryTypes.INTERNAL">{{ t('inventory.manage.type.INTERNAL') }}</option>
          <option :value="InventoryTypes.EXTERNAL">{{ t('inventory.manage.type.EXTERNAL') }}</option>
          <option :value="InventoryTypes.MIXED">{{ t('inventory.manage.type.MIXED') }}</option>
        </SelectInput>
      </div>
    </div>
    <PrimaryButton :disabled="savingSettings || !editName.trim()" @click="saveSettings">
      {{ savingSettings ? t('common.loading') : t('common.save') }}
    </PrimaryButton>
  </NeutralContainer>
</template>
