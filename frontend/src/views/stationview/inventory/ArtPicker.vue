/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import LabelSelectInput from '@/components/input/select/LabelSelectInput.vue'
import type {InventoryArt} from '@/api/inventoryArts'

/**
 * The kind of thing a piece is, picked beside its name and never instead of it.
 *
 * A piece may have no kind, so nothing here insists on one. A kind typed in is kept as a word
 * until the form is saved, which is what stops an abandoned form and a mistyped word from leaving
 * a row behind.
 */
const props = withDefaults(
    defineProps<{
        arts: InventoryArt[]
        disabled?: boolean
        /** Whether the reader may write down a kind that is not there yet. */
        creatable?: boolean
    }>(),
    {disabled: false, creatable: true},
)

const artId = defineModel<number | null>('artId', {default: null})
const draft = defineModel<string>('draft', {default: ''})

const {t} = useI18n()

const selected = computed(() => {
    const found = props.arts.find(art => art.id === artId.value)
    return found ? [found] : []
})

const drafts = computed(() => (draft.value ? [draft.value] : []))

function toggle(id: number) {
    artId.value = artId.value === id ? null : id
    if (artId.value !== null) draft.value = ''
}

function setDrafts(names: string[]) {
    draft.value = names.length > 0 ? (names[names.length - 1] ?? '') : ''
    if (draft.value) artId.value = null
}
</script>

<template>
    <LabelSelectInput
        :labels="arts"
        :selected="selected"
        :drafts="drafts"
        :disabled="disabled"
        :creatable="creatable"
        single
        defer-create
        :placeholder="t('inventory.art.none')"
        :empty-text="t('inventory.art.empty')"
        @toggle="toggle"
        @update:drafts="setDrafts"
    />
</template>
