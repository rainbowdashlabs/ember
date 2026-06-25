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
import SelectInput from '@/components/input/select/SelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
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
}>()

const {t} = useI18n()

const open = ref(true)
const name = ref('')
const internalId = ref('')
const description = ref('')
const parentId = ref<number | null>(props.defaultParentId ?? null)
const kindId = ref<number | null>(null)
const submitting = ref(false)
const error = ref('')

const enabledKinds = computed(() => props.kinds.filter(k => k.enabled))
const sortedContainers = computed(() => [...props.containers].sort((a, b) => a.name.localeCompare(b.name)))

async function submit() {
  if (!name.value.trim()) {
    error.value = t('inventory.storage.errors.nameRequired')
    return
  }
  submitting.value = true
  error.value = ''
  try {
    await inventoryContainers.createContainer({
      parentId: parentId.value,
      internalId: internalId.value.trim() || null,
      name: name.value.trim(),
      kindId: kindId.value,
      description: description.value.trim(),
    })
    emit('created')
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('inventory.storage.errors.createFailed')
  } finally {
    submitting.value = false
  }
}

function onClose() {
  open.value = false
  emit('close')
}
</script>

<template>
  <Modal v-model="open" size="md" @update:modelValue="(v) => { if (!v) onClose() }">
    <SubHeader class="mb-3">{{ t('inventory.storage.newContainer') }}</SubHeader>
    <div v-if="error" class="mb-3">
      <Alert variant="error">{{ error }}</Alert>
    </div>
    <div class="flex flex-col gap-3">
      <label class="flex flex-col gap-1 text-sm">
        <span>{{ t('inventory.storage.fields.name') }}</span>
        <TextInput v-model="name" :placeholder="t('inventory.storage.fields.namePlaceholder')" />
      </label>
      <label class="flex flex-col gap-1 text-sm">
        <span>{{ t('inventory.storage.fields.parent') }}</span>
        <SelectInput v-model="parentId">
          <option :value="null">{{ t('inventory.storage.fields.parentNone') }}</option>
          <option v-for="c in sortedContainers" :key="c.id" :value="c.id">{{ c.name }}</option>
        </SelectInput>
      </label>
      <label class="flex flex-col gap-1 text-sm">
        <span>{{ t('inventory.storage.fields.kind') }}</span>
        <SelectInput v-model="kindId">
          <option :value="null">{{ t('inventory.storage.fields.kindNone') }}</option>
          <option v-for="k in enabledKinds" :key="k.id" :value="k.id">{{ k.label }}</option>
        </SelectInput>
      </label>
      <label class="flex flex-col gap-1 text-sm">
        <span>{{ t('inventory.storage.fields.internalId') }}</span>
        <TextInput v-model="internalId" :placeholder="t('inventory.storage.fields.internalIdPlaceholder')" />
      </label>
      <label class="flex flex-col gap-1 text-sm">
        <span>{{ t('inventory.storage.fields.description') }}</span>
        <TextAreaInput v-model="description" rows="3" />
      </label>
    </div>
    <div class="flex justify-end gap-2 mt-4">
      <SecondaryButton @click="onClose">{{ t('common.cancel') }}</SecondaryButton>
      <PrimaryButton :disabled="submitting" @click="submit">
        {{ submitting ? t('common.saving') : t('common.create') }}
      </PrimaryButton>
    </div>
  </Modal>
</template>
