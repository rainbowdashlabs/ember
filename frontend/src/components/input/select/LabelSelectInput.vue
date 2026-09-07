/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {contrastTextColor} from '@/theme/contrast'

/**
 * The least a thing needs to be pickable here: an identifier and a word. A colour is optional, so
 * the same picker serves a coloured board label and an uncoloured kind of thing.
 */
export interface SelectableOption {
    id: number
    name: string
    color?: string | null
}

const props = withDefaults(
    defineProps<{
        labels: SelectableOption[]
        selected: SelectableOption[]
        /**
         * Names the reader has asked for that do not exist yet, shown as chips and handed back on
         * `update:drafts`. Only filled when `deferCreate` is set.
         */
        drafts?: string[]
        disabled?: boolean
        /** At most one may be picked, so choosing a second replaces the first. */
        single?: boolean
        /** Whether typing a new word offers to make it. */
        creatable?: boolean
        /**
         * Hand a new word back as a draft instead of making it at once.
         *
         * Without this the picker writes a row the moment somebody types a word and presses Enter,
         * so an abandoned form leaves one behind, and a mistyped word is written down as firmly as
         * a right one. With it, the parent form makes what the reader kept when the form is saved.
         */
        deferCreate?: boolean
        placeholder?: string
        emptyText?: string
    }>(),
    {
        drafts: () => [],
        disabled: false,
        single: false,
        creatable: true,
        deferCreate: false,
        placeholder: '',
        emptyText: '',
    },
)

const emit = defineEmits<{
    toggle: [labelId: number]
    create: [name: string]
    'update:drafts': [names: string[]]
}>()

const {t} = useI18n()

const search = ref('')
const open = ref(false)
const containerRef = ref<HTMLElement | null>(null)
const searchInputRef = ref<HTMLInputElement | null>(null)

const placeholderText = computed(() => props.placeholder || t('labelSelect.placeholder'))
const emptyMessage = computed(() => props.emptyText || t('labelSelect.empty'))

const filtered = computed(() => {
    if (!search.value) return props.labels
    const q = search.value.toLowerCase()
    return props.labels.filter(l => l.name.toLowerCase().includes(q))
})

const typed = computed(() => search.value.trim())

const canCreate = computed(() => {
    if (!props.creatable || !typed.value) return false
    const q = typed.value.toLowerCase()
    if (props.labels.some(l => l.name.toLowerCase() === q)) return false
    return !props.drafts.some(name => name.toLowerCase() === q)
})

const selectedIds = computed(() => new Set(props.selected.map(l => l.id)))

function toggle(id: number) {
    emit('toggle', id)
    search.value = ''
    if (props.single) open.value = false
}

function createLabel() {
    if (!canCreate.value) return
    if (props.deferCreate) {
        emit('update:drafts', props.single ? [typed.value] : [...props.drafts, typed.value])
    } else {
        emit('create', typed.value)
    }
    search.value = ''
    if (props.single) open.value = false
}

function dropDraft(name: string) {
    emit(
        'update:drafts',
        props.drafts.filter(draft => draft !== name),
    )
}

function handleClickOutside(e: MouseEvent) {
    if (containerRef.value && !containerRef.value.contains(e.target as Node)) open.value = false
}

watch(open, isOpen => {
    if (isOpen) nextTick(() => searchInputRef.value?.focus())
})
onMounted(() => document.addEventListener('click', handleClickOutside))
onBeforeUnmount(() => document.removeEventListener('click', handleClickOutside))
</script>

<template>
    <div ref="containerRef" class="relative">
        <div
            class="flex flex-wrap items-center gap-1 min-h-[2rem] px-2 py-1 rounded-theme bg-transparent cursor-pointer transition-colors hover:bg-[var(--bg-accent)]"
            :class="{'opacity-50 pointer-events-none': disabled}"
            data-testid="label-select"
            @click.stop="open = !open"
        >
            <BaseBadge
                v-for="label in selected"
                :key="label.id"
                :bg-class="label.color ? '' : 'bg-primary/15'"
                class="inline-flex items-center gap-1"
                :style="label.color ? {backgroundColor: label.color, color: contrastTextColor(label.color)} : undefined"
            >
                {{ label.name }}
                <span class="opacity-70 cursor-pointer" @click.stop="toggle(label.id)">x</span>
            </BaseBadge>
            <BaseBadge
                v-for="name in drafts"
                :key="`draft-${name}`"
                bg-class="bg-primary/15"
                class="inline-flex items-center gap-1 border border-dashed border-(--border)"
            >
                {{ name }}
                <span class="text-xs opacity-70">{{ t('labelSelect.draft') }}</span>
                <span class="opacity-70 cursor-pointer" @click.stop="dropDraft(name)">x</span>
            </BaseBadge>
            <span v-if="selected.length === 0 && drafts.length === 0" class="text-sm text-(--text-muted)">
                {{ placeholderText }}
            </span>
        </div>
        <div
            v-if="open"
            class="absolute z-20 mt-1 w-full rounded-theme border border-[var(--border)] bg-[var(--bg)] shadow-lg overflow-hidden max-h-48 overflow-y-auto"
        >
            <div class="p-2 border-b border-[var(--border)]">
                <input
                    ref="searchInputRef"
                    v-model="search"
                    type="text"
                    :placeholder="creatable ? t('labelSelect.searchOrCreate') : t('labelSelect.search')"
                    class="w-full text-sm bg-transparent outline-none"
                    @keydown.enter.prevent="canCreate ? createLabel() : undefined"
                />
            </div>
            <div
                v-for="label in filtered"
                :key="label.id"
                class="px-3 py-1.5 text-sm cursor-pointer hover:bg-primary/5 flex items-center gap-2"
                @click="toggle(label.id)"
            >
                <span v-if="label.color" class="w-3 h-3 rounded-full shrink-0" :style="{backgroundColor: label.color}"/>
                <span class="flex-1">{{ label.name }}</span>
                <font-awesome-icon
                    v-if="selectedIds.has(label.id)"
                    :icon="['fas', 'check']"
                    class="text-xs text-primary"
                />
            </div>
            <div
                v-if="canCreate"
                class="px-3 py-1.5 text-sm cursor-pointer hover:bg-primary/5 flex items-center gap-2 border-t border-[var(--border)]"
                data-testid="label-select-create"
                @click="createLabel"
            >
                <font-awesome-icon :icon="['fas', 'plus']" class="text-xs text-primary"/>
                <span>{{ t('labelSelect.create', {name: typed}) }}</span>
            </div>
            <p v-else-if="filtered.length === 0" class="px-3 py-2 text-xs text-(--text-muted)">
                {{ emptyMessage }}
            </p>
        </div>
    </div>
</template>
