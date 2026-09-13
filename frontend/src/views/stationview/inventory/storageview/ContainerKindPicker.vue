/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import GearIconPicker from '@/components/input/select/GearIconPicker.vue'
import GearGlyph from '@/components/inventory/GearGlyph.vue'
import {glyphFor} from '@/util/glyph'
import {inventoryContainers} from '@/api'
import type {InventoryContainerKind} from '@/api/inventoryContainers'
import {showToast} from '@/util/toast'
import {useAsyncAction} from '@/composables/useAsyncAction'

const props = defineProps<{
  kinds: InventoryContainerKind[]
  modelValue: number | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: number | null): void
  (e: 'kind-created', kind: InventoryContainerKind): void
  /**
   * The kind that was just chosen, whether picked from the list or made on the spot.
   *
   * <p>Beside the id, because what a container is called usually is what it is, and a caller that
   * wants to offer that has the label here rather than having to look the id up again.
   */
  (e: 'picked', kind: InventoryContainerKind): void
}>()

const {t} = useI18n()

const selectedId = ref<number | null>(props.modelValue)
const inputValue = ref('')
const showSuggestions = ref(false)
const iconPickerOpen = ref(false)
const pendingLabel = ref('')
const pendingIcon = ref<string | null>(null)
const pendingColor = ref<string | null>(null)
const locallyCreated = ref<InventoryContainerKind[]>([])

const allKinds = computed(() => {
  const known = new Set(props.kinds.map(k => k.id))
  return [...props.kinds, ...locallyCreated.value.filter(k => !known.has(k.id))]
})

const selectedKind = computed(() => selectedId.value != null
    ? allKinds.value.find(k => k.id === selectedId.value) ?? null
    : null)

const enabledKinds = computed(() => allKinds.value.filter(k => k.enabled))

const suggestions = computed(() => {
  const q = inputValue.value.toLowerCase().trim()
  if (!q) {
    return enabledKinds.value.slice(0, 8)
  }
  return enabledKinds.value
      .filter(k => k.label.toLowerCase().includes(q) || k.key.toLowerCase().includes(q))
      .slice(0, 8)
})

const exactMatch = computed(() => {
  const q = inputValue.value.toLowerCase().trim()
  if (!q) return null
  return enabledKinds.value.find(k => k.label.toLowerCase() === q) ?? null
})

const canCreate = computed(() => {
  const q = inputValue.value.trim()
  return q.length > 0 && !exactMatch.value && !creating.value
})

watch(() => props.modelValue, (v) => {
  if (v !== selectedId.value) selectedId.value = v
})

watch(selectedId, (v) => {
  emit('update:modelValue', v)
})

function pick(kind: InventoryContainerKind) {
  selectedId.value = kind.id
  inputValue.value = ''
  showSuggestions.value = false
  emit('picked', kind)
}

function clearSelection() {
  selectedId.value = null
}

function startCreate() {
  pendingLabel.value = inputValue.value.trim()
  if (!pendingLabel.value) return
  pendingIcon.value = 'box'
  pendingColor.value = null
  iconPickerOpen.value = true
  showSuggestions.value = false
}

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

const {running: creating, run: runCreateKind} = useAsyncAction(async (iconName: string, color: string | null) => {
  const slugBase = slugify(pendingLabel.value) || 'kind'
  let key = slugBase
  const existing = new Set(props.kinds.map(k => k.key))
  let counter = 2
  while (existing.has(key)) {
    key = `${slugBase}_${counter++}`
  }
  const created = await inventoryContainers.createKind({
    key,
    label: pendingLabel.value,
    icon: iconName,
    color,
    sortOrder: Math.max(0, ...props.kinds.map(k => k.sortOrder)) + 10,
    enabled: true,
  })
  locallyCreated.value = [...locallyCreated.value, created]
  selectedId.value = created.id
  emit('kind-created', created)
  emit('picked', created)
  iconPickerOpen.value = false
  pendingLabel.value = ''
  inputValue.value = ''
  return true
})

async function commitPendingKind() {
  if (creating.value || !pendingIcon.value) return
  const ok = await runCreateKind(pendingIcon.value, pendingColor.value)
  if (!ok) showToast(t('inventory.storage.fields.kindCreateFailed'), 'error')
}

function cancelIconPicker() {
  iconPickerOpen.value = false
  pendingLabel.value = ''
  pendingIcon.value = null
  pendingColor.value = null
}

function hideSuggestionsSoon() {
  setTimeout(() => { showSuggestions.value = false }, 200)
}

async function resolve(): Promise<number | null> {
  return selectedId.value
}

defineExpose({resolve})
</script>

<template>
  <div class="flex flex-col gap-2">
    <div v-if="selectedKind" class="inline-flex items-center gap-2 self-start rounded-theme bg-(--bg-accent) px-2 py-1 text-sm">
      <GearGlyph :glyph="glyphFor({icon: selectedKind.icon, color: selectedKind.color, homogeneous: false})"
                 surface="accent"/>
      <span class="font-medium">{{ selectedKind.label }}</span>
      <IconButton
          :icon="['fas', 'xmark']"
          :label="t('common.remove')"
          class="!p-0 text-xs hover:text-error"
          @click="clearSelection"
      />
    </div>
    <div v-else class="relative">
      <TextInput
          v-model="inputValue"
          :placeholder="t('inventory.storage.fields.kindPlaceholder')"
          @focus="showSuggestions = true"
          @blur="hideSuggestionsSoon"
          @keydown.enter.prevent="canCreate ? startCreate() : (suggestions[0] ? pick(suggestions[0]) : null)"
      />
      <div
          v-if="showSuggestions && (suggestions.length > 0 || canCreate)"
          class="absolute top-full left-0 right-0 mt-1 z-20 rounded border border-(--border) bg-bg-light dark:bg-bg-dark shadow-lg py-1 max-h-64 overflow-y-auto"
      >
        <DropdownMenuItem
            v-for="kind in suggestions"
            :key="kind.id"
            @click="pick(kind)"
        >
          <GearGlyph :glyph="glyphFor({icon: kind.icon, color: kind.color, homogeneous: false})"/>
          {{ kind.label }}
        </DropdownMenuItem>
        <DropdownMenuItem
            v-if="canCreate"
            :icon="['fas', 'plus']"
            class="border-t border-(--border) mt-1 pt-2"
            @click="startCreate"
        >
          {{ t('inventory.storage.fields.kindAdd', {label: inputValue.trim()}) }}
        </DropdownMenuItem>
      </div>
    </div>

    <div v-if="iconPickerOpen" class="rounded-theme border border-(--border) p-3 flex flex-col gap-2">
      <p class="text-sm">
        {{ t('inventory.storage.fields.kindIconPickerHint', {label: pendingLabel}) }}
      </p>
      <GearIconPicker v-model:icon="pendingIcon" v-model:color="pendingColor"/>
      <div class="flex justify-end gap-2">
        <IconButton
            :icon="['fas', 'xmark']"
            :label="t('common.cancel')"
            @click="cancelIconPicker"
        />
        <PrimaryButton :disabled="creating || !pendingIcon" data-testid="kind-create-commit" @click="commitPendingKind">
          {{ t('common.save') }}
        </PrimaryButton>
      </div>
    </div>
  </div>
</template>
