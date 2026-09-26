/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'
import ContainerKindPicker from '@/views/stationview/inventory/storageview/ContainerKindPicker.vue'
import ContainerParentPicker from '@/views/stationview/inventory/storageview/ContainerParentPicker.vue'
import {describeFailure, type Failure} from '@/util/failure'
import {normaliseScannedPayload} from '@/components/scanner/useBarcodeScanner'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {inventoryContainers} from '@/api'
import type {InventoryContainer, InventoryContainerKind} from '@/api/inventoryContainers'

const props = defineProps<{
  kinds: InventoryContainerKind[]
  containers: InventoryContainer[]
  defaultParentId?: number | null
}>()

const emit = defineEmits<{
  created: []
  close: []
  'kind-created': [kind: InventoryContainerKind]
}>()

const {t} = useI18n()

const open = ref(true)
const name = ref('')
const internalId = ref('')
const description = ref('')
const parentId = ref<number | null>(props.defaultParentId ?? null)
const kindId = ref<number | null>(null)
const kindPicker = ref<InstanceType<typeof ContainerKindPicker> | null>(null)
const validationError = ref('')

const createFailure = ref<Failure | null>(null)

const {running: submitting, run: runCreate} = useAsyncAction(async () => {
  const resolvedKindId = (await kindPicker.value?.resolve()) ?? null
  try {
    await inventoryContainers.createContainer({
      parentId: parentId.value,
      internalId: internalId.value.trim() || null,
      name: name.value.trim(),
      kindId: resolvedKindId,
      description: description.value.trim(),
    })
  } catch (e) {
    createFailure.value = describeFailure(e, t)
    return
  }
  emit('created')
})

/**
 * A container that has not been named yet takes the name of what it is.
 *
 * <p>Most of them are called exactly that: a shelf is "Regal" and a locker is "Spind", and typing
 * the word again after choosing it is work for nothing. A name somebody wrote is never touched.
 */
function nameAfterKind(kind: InventoryContainerKind) {
  if (!name.value.trim()) name.value = kind.label
}

async function submit() {
  validationError.value = ''
  createFailure.value = null
  if (!name.value.trim()) {
    validationError.value = t('inventory.storage.errors.nameRequired')
    return
  }
  await runCreate()
}

function onClose() {
  open.value = false
  emit('close')
}
</script>

<template>
  <Modal v-model="open" size="md" @update:modelValue="(v) => { if (!v) onClose() }">
    <SubHeader class="mb-3">{{ t('inventory.storage.newContainer') }}</SubHeader>
    <div v-if="validationError || createFailure" class="mb-3 space-y-2">
      <FailureAlert :message="validationError" expected/>
      <FailureAlert :failure="createFailure"/>
    </div>
    <div class="flex flex-col gap-3">
      <label class="flex flex-col gap-1 text-sm">
        <span>{{ t('inventory.storage.fields.name') }}</span>
        <TextInput v-model="name" :placeholder="t('inventory.storage.fields.namePlaceholder')" />
      </label>
      <div class="flex flex-col gap-1 text-sm">
        <span>{{ t('inventory.storage.fields.parent') }}</span>
        <ContainerParentPicker v-model="parentId" :containers="containers" :kinds="kinds" />
      </div>
      <div class="flex flex-col gap-1 text-sm">
        <span>{{ t('inventory.storage.fields.kind') }}</span>
        <ContainerKindPicker
            ref="kindPicker"
            v-model="kindId"
            :kinds="kinds"
            @kind-created="(k) => emit('kind-created', k)"
            @picked="nameAfterKind"
        />
      </div>
      <label class="flex flex-col gap-1 text-sm">
        <span>{{ t('inventory.storage.fields.internalId') }}</span>
        <div class="flex items-center gap-2">
          <TextInput v-model="internalId" class="flex-1" :placeholder="t('inventory.storage.fields.internalIdPlaceholder')" />
          <ScanButton @decoded="internalId = normaliseScannedPayload($event)" />
        </div>
      </label>
      <label class="flex flex-col gap-1 text-sm">
        <span>{{ t('inventory.storage.fields.description') }}</span>
        <TextAreaInput v-model="description" :rows="3" />
      </label>
    </div>
    <ButtonRow pair align="end" class="mt-4">
      <SecondaryButton @click="onClose">{{ t('common.cancel') }}</SecondaryButton>
      <PrimaryButton :disabled="submitting" @click="submit">
        {{ submitting ? t('common.saving') : t('common.create') }}
      </PrimaryButton>
    </ButtonRow>
  </Modal>
</template>
