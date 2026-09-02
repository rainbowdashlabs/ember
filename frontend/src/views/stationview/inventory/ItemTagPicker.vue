/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import LabelSelectInput from '@/components/input/select/LabelSelectInput.vue'
import type {InventoryTag} from '@/api/inventoryTags'

/**
 * The words a piece wears, picked from the ones the station already uses.
 *
 * The model is a list of words rather than of identifiers, because a word picked from the list and
 * a word made up on the spot have to be the same thing to the form above. Nothing is written down
 * until that form is saved, so an abandoned form leaves no word behind and a typo is never written
 * down as firmly as a right word.
 */
const props = withDefaults(
    defineProps<{
        tags: InventoryTag[]
        disabled?: boolean
        /** Whether the reader may write down a word that is not there yet. */
        creatable?: boolean
    }>(),
    {disabled: false, creatable: true},
)

const names = defineModel<string[]>('names', {default: () => []})

const {t} = useI18n()

const lowered = computed(() => names.value.map(name => name.trim().toLowerCase()))

const selected = computed(() => props.tags.filter(tag => lowered.value.includes(tag.name.trim().toLowerCase())))

const drafts = computed(() => {
    const known = new Set(props.tags.map(tag => tag.name.trim().toLowerCase()))
    return names.value.filter(name => !known.has(name.trim().toLowerCase()))
})

function toggle(id: number) {
    const tag = props.tags.find(candidate => candidate.id === id)
    if (!tag) return
    const wanted = tag.name.trim().toLowerCase()
    names.value = lowered.value.includes(wanted)
        ? names.value.filter(name => name.trim().toLowerCase() !== wanted)
        : [...names.value, tag.name]
}

function setDrafts(next: string[]) {
    const known = new Set(props.tags.map(tag => tag.name.trim().toLowerCase()))
    names.value = [...names.value.filter(name => known.has(name.trim().toLowerCase())), ...next]
}
</script>

<template>
    <LabelSelectInput
        :labels="tags"
        :selected="selected"
        :drafts="drafts"
        :disabled="disabled"
        :creatable="creatable"
        defer-create
        :placeholder="t('inventory.tag.none')"
        :empty-text="t('inventory.tag.empty')"
        @toggle="toggle"
        @update:drafts="setDrafts"
    />
</template>
