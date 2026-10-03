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
import type {GuardianCondition} from '@/api/generated/schema'
import type {BlockRestrictionChoices} from '@/composables/useBlockEditorOptions'

/**
 * Who a block is shown to, as chips on the block: the user types, groups and tags it is restricted to,
 * whether all of them or any one has to match, and whether the member must or must not have a second
 * guardian. Nothing is drawn for a block shown to everybody.
 */
const props = defineProps<{
    restriction: RestrictionSelection | null | undefined
    guardianCondition?: GuardianCondition | null
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

const condition = computed(() => props.guardianCondition
    ? t(`stationPages.editor.guardianConditionOf.${props.guardianCondition}`)
    : null)

/** The words before the chips: one part, every part, or any one of them. */
const lead = computed(() => {
    if (chips.value.length < 2) return t('stationPages.editor.visibilityOnly')
    return props.restriction?.mode === 'OR' ? t('stationPages.editor.visibilityAny') : t('stationPages.editor.visibilityAll')
})
</script>

<template>
    <div v-if="chips.length > 0 || condition" class="flex flex-wrap items-center gap-1 mb-2" data-testid="cell-restriction">
        <AppIcon :icon="['fas', 'eye']" class="text-xs text-(--text-muted)"/>
        <span v-if="chips.length > 0" class="text-xs text-(--text-muted)">{{ lead }}</span>
        <SecondaryBadge v-for="chip in chips" :key="chip">{{ chip }}</SecondaryBadge>
        <SecondaryBadge v-if="condition" data-testid="cell-guardian-chip">{{ condition }}</SecondaryBadge>
    </div>
</template>
