/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import {InventoryTypes, switchRefusal, type InventoryDetail, type InventoryTypeName, type SwitchBlocker} from '@/api/inventory'
import {inventory} from '@/api'
import SwitchRefusalAlert from './SwitchRefusalAlert.vue'

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
const editHomogeneous = ref(props.detail.homogeneous)
const editHasSizes = ref(props.detail.hasSizes ?? false)

/** A drawer of different things keeps no size list, so the one control follows the other. */
watch(editHomogeneous, value => {
  if (!value) editHasSizes.value = false
})

/** What stood in the way the last time the change of kind was refused. */
const blockers = ref<SwitchBlocker[]>([])
const refusalMessage = ref('')

async function saveSettings() {
  blockers.value = []
  refusalMessage.value = ''
  try {
    await inventory.updateInventory(props.detail.id, {
      name: editName.value,
      inventoryType: editType.value as InventoryTypeName,
      hasSizes: editHasSizes.value,
      homogeneous: editHomogeneous.value,
    })
    emit('saved')
  } catch (e) {
    const refusal = switchRefusal(e)
    if (refusal) {
      // The refusal names what is in the way, so it belongs beside the control that was refused
      // rather than as one more line of "something went wrong" at the top of the page.
      blockers.value = refusal.blockers
      refusalMessage.value = t('inventory.edit.kindRefused')
      return
    }
    emit('error', t('common.error'))
    throw e
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

    <div class="flex items-center justify-between gap-4">
      <div>
        <label class="text-sm font-medium">{{ t('inventory.manage.homogeneous') }}</label>
        <p class="text-xs text-(--text-muted)">{{ t('inventory.manage.homogeneousHint') }}</p>
      </div>
      <ToggleInput v-model="editHomogeneous" data-testid="inventory-homogeneous"/>
    </div>

    <div v-if="editHomogeneous" class="flex items-center justify-between gap-4">
      <div>
        <label class="text-sm font-medium">{{ t('inventory.manage.hasSizes') }}</label>
        <p class="text-xs text-(--text-muted)">{{ t('inventory.manage.hasSizesHint') }}</p>
      </div>
      <ToggleInput v-model="editHasSizes" data-testid="inventory-has-sizes"/>
    </div>

    <SwitchRefusalAlert :message="refusalMessage" :blockers="blockers"/>

    <SaveButton :disabled="!editName.trim()" :action="saveSettings"/>
  </NeutralContainer>
</template>
