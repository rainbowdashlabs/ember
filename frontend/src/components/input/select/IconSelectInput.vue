/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import type {AcceptableValue} from 'reka-ui'
import DropdownPanel from './dropdown/DropdownPanel.vue'
import DropdownListbox from './dropdown/DropdownListbox.vue'
import DropdownOption from './dropdown/DropdownOption.vue'

export interface IconOption {
    value: string
    label: string
    icon: string[]
    color?: string
}

/**
 * One choice out of a short list where each entry carries an icon, such as a priority. An
 * `autoOpen` one opens as it appears, for a field that is only shown once somebody asked to edit it.
 */
const model = defineModel<string>()

const props = defineProps<{
    options: IconOption[]
    disabled?: boolean
    autoOpen?: boolean
}>()

const open = ref(false)

const selected = computed(() => props.options.find(o => o.value === model.value))

function select(value: AcceptableValue | AcceptableValue[] | undefined) {
    model.value = String(value ?? '')
    open.value = false
}

onMounted(() => {
    if (props.autoOpen) open.value = true
})
</script>

<template>
    <div>
        <DropdownPanel v-model:open="open" match-width panel-class="max-h-72">
            <template #trigger>
                <button
                    type="button"
                    :disabled="disabled"
                    aria-haspopup="listbox"
                    class="w-full flex items-center gap-2 rounded-theme border border-[var(--border)] bg-[var(--bg)] px-3 py-2 text-sm text-left transition-colors hover:border-primary disabled:opacity-50"
                >
                    <font-awesome-icon v-if="selected" :icon="selected.icon" :class="selected.color" />
                    <span class="flex-1">{{ selected?.label ?? '' }}</span>
                    <font-awesome-icon :icon="['fas', 'chevron-down']" class="text-xs text-(--text-muted)" />
                </button>
            </template>
            <DropdownListbox :model-value="model" @update:model-value="select">
                <DropdownOption v-for="opt in options" :key="opt.value" :value="opt.value">
                    <font-awesome-icon :icon="opt.icon" :class="opt.color" />
                    <span>{{ opt.label }}</span>
                </DropdownOption>
            </DropdownListbox>
        </DropdownPanel>
    </div>
</template>
