/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import EntitySearchPicker from '@/components/input/search/EntitySearchPicker.vue'
import ColorInput from '@/components/input/ColorInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import GearGlyph from '@/components/inventory/GearGlyph.vue'
import {GEAR_ICONS, type GearIcon} from '@/util/gearIcons'
import {glyphFor} from '@/util/glyph'
import {listSearch} from '@/util/listSearch'

/**
 * The picture an inventory, a kind or a container kind is drawn with.
 *
 * <p>Built on the search picker every other list in the product uses, so the catalogue is searchable
 * and walkable with the keyboard without a second implementation of either. The swatches sit beneath
 * it: a station colouring nine inventories wants them to look like one set rather than nine separate
 * decisions, and the colour field underneath is for the one that means a particular red.
 */
const icon = defineModel<string | null | undefined>('icon')
const color = defineModel<string | null | undefined>('color')

const props = defineProps<{
  /** Offered where a picture may be left out. A container kind always has one. */
  allowNoIcon?: boolean
}>()

const {t} = useI18n()

/** One click apart, so a station's inventories look like one set. */
const SWATCHES = [
  '#dc2626',
  '#ea580c',
  '#d97706',
  '#65a30d',
  '#15803d',
  '#0f766e',
  '#0284c7',
  '#2563eb',
  '#7c3aed',
  '#be185d',
  '#57534e',
  '#1f2937',
] as const

const custom = ref(false)

const entries = computed(() => [...GEAR_ICONS])
const searchFn = listSearch(entries, entry => [t(entry.labelKey), entry.name, ...entry.aliases].join(' '))
const displayFn = (entry: GearIcon) => t(entry.labelKey)
const keyFn = (entry: GearIcon) => entry.name

const chosen = computed(() => GEAR_ICONS.find(entry => entry.name === icon.value) ?? null)

const preview = computed(() => glyphFor({icon: icon.value, color: color.value, homogeneous: true}))

const pickerModel = computed({
  get: () => icon.value ?? null,
  set: value => {
    icon.value = value
  },
})

function pickIcon(entry: GearIcon) {
  icon.value = entry.name
}

function pickColor(value: string | null) {
  color.value = value
  custom.value = false
}
</script>

<template>
  <div class="space-y-2" data-testid="gear-icon-picker">
    <FieldLabel>{{ t('inventory.iconPicker.label') }}</FieldLabel>
    <EntitySearchPicker
        v-model="pickerModel"
        :search-fn="searchFn"
        :display-fn="displayFn"
        :key-fn="keyFn"
        :selected-display="chosen ? t(chosen.labelKey) : (icon ?? null)"
        :placeholder="t('inventory.iconPicker.placeholder')"
        :empty-label="t('inventory.iconPicker.empty')"
        @pick="pickIcon"
    >
      <template #row="{item, highlighted}">
        <GearGlyph :glyph="glyphFor({icon: item.name, color, homogeneous: true})"
                   :surface="highlighted ? 'highlight' : 'page'"/>
        <span class="flex min-w-0 flex-1 flex-col items-start text-left">
          <span class="truncate font-medium">{{ t(item.labelKey) }}</span>
          <span class="truncate text-xs text-(--text-muted)">{{ t(`inventory.iconPicker.groups.${item.group}`) }}</span>
        </span>
      </template>
      <template #picked>
        <GearGlyph :glyph="preview"/>
        <span class="flex-1 truncate text-sm">{{ chosen ? t(chosen.labelKey) : icon }}</span>
      </template>
    </EntitySearchPicker>

    <div class="flex flex-wrap items-center gap-1.5">
      <button
          v-for="swatch in SWATCHES"
          :key="swatch"
          type="button"
          :aria-label="swatch"
          :aria-pressed="color === swatch"
          :class="[
            'h-6 w-6 rounded-full border transition-transform',
            color === swatch ? 'border-(--text) scale-110' : 'border-(--border) hover:scale-105',
          ]"
          :style="{backgroundColor: swatch}"
          data-testid="gear-color-swatch"
          @click="pickColor(swatch)"
      />
      <button
          type="button"
          :aria-pressed="!color"
          :class="[
            'rounded-theme border px-2 py-1 text-xs transition-colors',
            color ? 'border-(--border) text-(--text-muted) hover:border-primary' : 'border-(--text) text-(--text)',
          ]"
          data-testid="gear-color-none"
          @click="pickColor(null)"
      >
        {{ t('inventory.iconPicker.noColor') }}
      </button>
      <ColorInput v-if="custom" :model-value="color ?? '#6b7280'" @update:model-value="pickColor"/>
      <button
          v-else
          type="button"
          class="rounded-theme border border-(--border) px-2 py-1 text-xs text-(--text-muted) transition-colors hover:border-primary"
          data-testid="gear-color-custom"
          @click="custom = true"
      >
        {{ t('inventory.iconPicker.color') }}
      </button>
      <button
          v-if="props.allowNoIcon && icon"
          type="button"
          class="rounded-theme border border-(--border) px-2 py-1 text-xs text-(--text-muted) transition-colors hover:border-primary"
          data-testid="gear-icon-none"
          @click="icon = null"
      >
        {{ t('inventory.iconPicker.none') }}
      </button>
    </div>
  </div>
</template>
