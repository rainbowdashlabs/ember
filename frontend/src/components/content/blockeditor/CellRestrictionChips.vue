/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import AppIcon from '@/components/display/AppIcon.vue'
import {StationUserTypeLabels, type RestrictionSelection} from '@/api/types'
import type {BlockRestrictionChoices} from '@/composables/useBlockEditorOptions'

/**
 * Who a block is shown to, as chips on the block: the user types, groups and tags it is restricted to,
 * and whether all of them or any one has to match. Nothing is drawn for a block shown to everybody.
 */
const props = defineProps<{
    restriction: RestrictionSelection | null | undefined
    choices: BlockRestrictionChoices
}>()

const {t} = useI18n()

const chips = computed(() => {
    const restriction = props.restriction
    if (!restriction) return []
    return [
        ...restriction.userTypes.map(type => StationUserTypeLabels[type]),
        ...restriction.groupIds.map(id => props.choices.groups.find(group => group.id === id)?.name ?? `#${id}`),
        ...restriction.tagIds.map(id => props.choices.tags.find(tag => tag.id === id)?.name ?? `#${id}`),
    ]
})

/** The words before the chips: one part, every part, or any one of them. */
const lead = computed(() => {
    if (chips.value.length < 2) return t('stationPages.editor.visibilityOnly')
    return props.restriction?.mode === 'OR' ? t('stationPages.editor.visibilityAny') : t('stationPages.editor.visibilityAll')
})
</script>

<template>
    <div v-if="chips.length > 0" class="flex flex-wrap items-center gap-1 mb-2" data-testid="cell-restriction">
        <AppIcon :icon="['fas', 'eye']" class="text-xs text-(--text-muted)"/>
        <span class="text-xs text-(--text-muted)">{{ lead }}</span>
        <SecondaryBadge v-for="chip in chips" :key="chip">{{ chip }}</SecondaryBadge>
    </div>
</template>
