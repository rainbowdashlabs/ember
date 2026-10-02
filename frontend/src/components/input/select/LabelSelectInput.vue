/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import DropdownPanel from './dropdown/DropdownPanel.vue'
import DropdownListbox from './dropdown/DropdownListbox.vue'
import DropdownOption from './dropdown/DropdownOption.vue'
import DropdownSearch from './dropdown/DropdownSearch.vue'
import LabelSelectTrigger from './labelselect/LabelSelectTrigger.vue'

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

/** A word to make, as the list tells it apart from every label, whose ids are numbers. */
const CREATE = 'create'

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

const picked = computed(() => (props.single ? props.selected[0]?.id : props.selected.map(l => l.id)))

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

/** The list marks what is picked, but picking is handed to the parent rather than kept by the list. */
function onPick(event: Event, id: number) {
    event.preventDefault()
    toggle(id)
}

function onCreate(event: Event) {
    event.preventDefault()
    createLabel()
}

watch(open, isOpen => {
    if (!isOpen) search.value = ''
})
</script>

<template>
    <div>
        <DropdownPanel v-model:open="open" match-width panel-class="max-h-60">
            <template #trigger>
                <LabelSelectTrigger
                    :selected="selected"
                    :drafts="drafts"
                    :disabled="disabled"
                    :placeholder="placeholderText"
                    @remove="toggle"
                    @drop-draft="dropDraft"
                />
            </template>
            <DropdownListbox :model-value="picked" :multiple="!single" :label="placeholderText">
                <template #head>
                    <DropdownSearch
                        v-model="search"
                        :placeholder="creatable ? t('labelSelect.searchOrCreate') : t('labelSelect.search')"
                    />
                </template>
                <DropdownOption v-if="canCreate" :value="CREATE" data-testid="label-select-create" @select="onCreate">
                    <font-awesome-icon :icon="['fas', 'plus']" class="text-xs text-primary"/>
                    <span>{{ t('labelSelect.create', {name: typed}) }}</span>
                </DropdownOption>
                <DropdownOption v-for="label in filtered" :key="label.id" :value="label.id" @select="onPick($event, label.id)">
                    <span v-if="label.color" class="w-3 h-3 rounded-full shrink-0" :style="{backgroundColor: label.color}"/>
                    <span class="flex-1">{{ label.name }}</span>
                    <font-awesome-icon v-if="selectedIds.has(label.id)" :icon="['fas', 'check']" class="text-xs text-primary"/>
                </DropdownOption>
                <p v-if="!canCreate && filtered.length === 0" class="px-3 py-2 text-xs text-(--text-muted)">
                    {{ emptyMessage }}
                </p>
            </DropdownListbox>
        </DropdownPanel>
    </div>
</template>
