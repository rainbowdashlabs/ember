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
import InventoryKindField from '@/components/inventory/InventoryKindField.vue'
import GearIconPicker from '@/components/input/select/GearIconPicker.vue'
import {InventoryTypes, switchRefusal, type InventoryDetail, type InventoryTypeName, type SwitchBlocker} from '@/api/inventory'
import {inventory} from '@/api'
import {describeFailure, type Failure} from '@/util/failure'
import SwitchRefusalAlert from './SwitchRefusalAlert.vue'

const {t} = useI18n()

const props = defineProps<{
  detail: InventoryDetail
}>()

const emit = defineEmits<{
  saved: []
  error: [failure: Failure]
}>()

const editName = ref(props.detail.name ?? '')
const editType = ref<string>(props.detail.inventoryType ?? InventoryTypes.INTERNAL)
const editHomogeneous = ref(props.detail.homogeneous)
const editHasSizes = ref(props.detail.hasSizes ?? false)
const editIcon = ref<string | null>(props.detail.icon ?? null)
const editColor = ref<string | null>(props.detail.color ?? null)

/** A collection keeps no size list, so the one control follows the other. */
watch(editHomogeneous, value => {
  if (!value) editHasSizes.value = false
})

/** What stood in the way the last time the change of kind was refused. */
const blockers = ref<SwitchBlocker[]>([])
const refusalMessage = ref('')

/**
 * Writes the settings back, and tells the reader apart from the two ways it can fail.
 *
 * <p>A refused change of kind names what is in the way, so it belongs beside the control that was
 * refused rather than as one more line at the top of the page. Everything else goes up as a described
 * failure, so the page can say what the server said and offer a report where it is ours to fix.
 */
async function saveSettings() {
  blockers.value = []
  refusalMessage.value = ''
  try {
    await inventory.updateInventory(props.detail.id, {
      name: editName.value,
      inventoryType: editType.value as InventoryTypeName,
      hasSizes: editHasSizes.value,
      homogeneous: editHomogeneous.value,
      icon: editIcon.value,
      color: editColor.value,
    })
    emit('saved')
  } catch (e) {
    const refusal = switchRefusal(e)
    if (refusal) {
      blockers.value = refusal.blockers
      refusalMessage.value = t('inventory.edit.kindRefused')
      return
    }
    emit('error', describeFailure(e, t))
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
        <p class="text-xs text-(--text-muted)">{{ t('inventory.edit.typeSwitchHint') }}</p>
      </div>
    </div>

    <GearIconPicker v-model:icon="editIcon" v-model:color="editColor" allow-no-icon/>

    <InventoryKindField v-model="editHomogeneous"/>

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
