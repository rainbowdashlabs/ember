/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'
import ContainerKindPicker from '@/views/stationview/inventory/storageview/ContainerKindPicker.vue'
import ContainerParentPicker from '@/views/stationview/inventory/storageview/ContainerParentPicker.vue'
import {mapContainerError} from '@/views/stationview/inventory/storageview/containerErrors'
import {normaliseScannedPayload} from '@/components/scanner/useBarcodeScanner'
import {inventoryContainers} from '@/api'
import type {InventoryContainer, InventoryContainerKind} from '@/api/inventoryContainers'
import {useAsyncAction} from '@/composables/useAsyncAction'

const props = defineProps<{
  container: InventoryContainer
  containers: InventoryContainer[]
  kinds: InventoryContainerKind[]
}>()

const emit = defineEmits<{
  saved: []
  cancel: []
  error: [message: string]
}>()

const {t} = useI18n()

const name = ref(props.container.name)
const internalId = ref(props.container.internalId ?? '')
const description = ref(props.container.description ?? '')
const kindId = ref<number | null>(props.container.kindId ?? null)
const parentId = ref<number | null>(props.container.parentId ?? null)
const kindPicker = ref<InstanceType<typeof ContainerKindPicker> | null>(null)

watch(() => props.container, (c) => {
  name.value = c.name
  internalId.value = c.internalId ?? ''
  description.value = c.description ?? ''
  kindId.value = c.kindId ?? null
  parentId.value = c.parentId ?? null
}, {deep: true})

const {running: submitting, error: saveError, run: runSave} = useAsyncAction(async () => {
  const resolvedKindId = (await kindPicker.value?.resolve()) ?? null
  await inventoryContainers.updateContainer(props.container.id, {
    parentId: parentId.value,
    internalId: internalId.value.trim() || null,
    name: name.value.trim(),
    kindId: resolvedKindId,
    description: description.value,
  })
  emit('saved')
}, {formatError: (e: any) => mapContainerError(t, e, 'inventory.storage.errors.updateFailed')})

async function save() {
  await runSave()
  if (saveError.value) emit('error', saveError.value)
}
</script>

<template>
  <NeutralContainer class="mb-4">
    <SubHeader class="mb-3">{{ t('inventory.storage.editTitle') }}</SubHeader>
    <div class="flex flex-col gap-3">
      <label class="flex flex-col gap-1 text-sm">
        <span>{{ t('inventory.storage.fields.name') }}</span>
        <TextInput v-model="name" />
      </label>
      <div class="flex flex-col gap-1 text-sm">
        <span>{{ t('inventory.storage.fields.parent') }}</span>
        <ContainerParentPicker
            v-model="parentId"
            :containers="containers"
            :kinds="kinds"
            :exclude-id="container.id"
        />
      </div>
      <div class="flex flex-col gap-1 text-sm">
        <span>{{ t('inventory.storage.fields.kind') }}</span>
        <ContainerKindPicker ref="kindPicker" :kinds="kinds" v-model="kindId" />
      </div>
      <label class="flex flex-col gap-1 text-sm">
        <span>{{ t('inventory.storage.fields.internalId') }}</span>
        <div class="flex items-center gap-2">
          <TextInput v-model="internalId" class="flex-1" />
          <ScanButton @decoded="internalId = normaliseScannedPayload($event)" />
        </div>
      </label>
      <label class="flex flex-col gap-1 text-sm">
        <span>{{ t('inventory.storage.fields.description') }}</span>
        <TextAreaInput v-model="description" :rows="3" />
      </label>
    </div>
    <div class="flex justify-end gap-2 mt-4">
      <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
      <PrimaryButton :disabled="submitting" @click="save">
        {{ submitting ? t('common.saving') : t('common.save') }}
      </PrimaryButton>
    </div>
  </NeutralContainer>
</template>
