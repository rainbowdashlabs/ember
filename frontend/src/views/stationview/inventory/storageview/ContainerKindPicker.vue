/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import {inventoryContainers} from '@/api'
import type {InventoryContainerKind} from '@/api/inventoryContainers'

const NEW_KIND_SENTINEL = -1

const props = defineProps<{
  kinds: InventoryContainerKind[]
  modelValue: number | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: number | null): void
  (e: 'kind-created', kind: InventoryContainerKind): void
}>()

const {t} = useI18n()

const selection = ref<number | null>(props.modelValue)
const newKindLabel = ref('')
const newKindIcon = ref('box')

watch(() => props.modelValue, (v) => {
  if (v !== selection.value && (v ?? null) !== (selection.value ?? null)) {
    selection.value = v
  }
})

watch(selection, (v) => {
  emit('update:modelValue', v === NEW_KIND_SENTINEL ? NEW_KIND_SENTINEL : v)
})

const enabledKinds = computed(() => props.kinds.filter(k => k.enabled))
const isCreating = computed(() => selection.value === NEW_KIND_SENTINEL)

function slugify(label: string): string {
  return label
      .trim()
      .toLowerCase()
      .normalize('NFKD')
      .replace(/[̀-ͯ]/g, '')
      .replace(/[^a-z0-9]+/g, '_')
      .replace(/^_+|_+$/g, '')
      .slice(0, 32)
}

/**
 * If the picker is in create-new mode, persists the new kind and switches the
 * selection to its id; otherwise returns the existing selection unchanged.
 * Throws when the operator chose "new" without filling in the label.
 */
async function resolve(): Promise<number | null> {
  if (!isCreating.value) return selection.value
  const label = newKindLabel.value.trim()
  if (!label) {
    throw new Error(t('inventory.storage.errors.newKindLabelRequired'))
  }
  const slugBase = slugify(label) || 'kind'
  let key = slugBase
  const existingKeys = new Set(props.kinds.map(k => k.key))
  let counter = 2
  while (existingKeys.has(key)) {
    key = `${slugBase}_${counter++}`
  }
  const created = await inventoryContainers.createKind({
    key,
    label,
    icon: newKindIcon.value.trim() || 'box',
    sortOrder: Math.max(0, ...props.kinds.map(k => k.sortOrder)) + 10,
    enabled: true,
  })
  selection.value = created.id
  newKindLabel.value = ''
  newKindIcon.value = 'box'
  emit('kind-created', created)
  return created.id
}

defineExpose({resolve})
</script>

<template>
  <div class="flex flex-col gap-3">
    <SelectInput v-model="selection">
      <option :value="null">{{ t('inventory.storage.fields.kindNone') }}</option>
      <option v-for="k in enabledKinds" :key="k.id" :value="k.id">{{ k.label }}</option>
      <option :value="NEW_KIND_SENTINEL">＋ {{ t('inventory.storage.fields.newKind') }}</option>
    </SelectInput>
    <template v-if="isCreating">
      <label class="flex flex-col gap-1 text-sm">
        <span>{{ t('inventory.storage.fields.newKindLabel') }}</span>
        <TextInput v-model="newKindLabel" :placeholder="t('inventory.storage.fields.newKindLabelPlaceholder')" />
      </label>
      <label class="flex flex-col gap-1 text-sm">
        <span>{{ t('inventory.storage.fields.newKindIcon') }}</span>
        <TextInput v-model="newKindIcon" :placeholder="t('inventory.storage.fields.newKindIconPlaceholder')" />
      </label>
    </template>
  </div>
</template>
