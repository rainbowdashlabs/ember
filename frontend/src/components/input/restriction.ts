/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type WritableComputedRef} from 'vue'
import {StationUserTypeLabels, type RestrictionSelection} from '@/api/types'
import {userTypesOf} from '@/util/stationUserTypes'

/**
 * A fresh, empty restriction selection. Use to initialise a `ref<RestrictionSelection>`
 * before loading persisted restrictions into it.
 */
export function emptyRestriction(): RestrictionSelection {
    return {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'}
}

/**
 * Reads one audience as the server sent it into the shape the editor binds.
 *
 * A missing audience is an empty one: a feature that carries two of them answers with both even
 * where neither names anybody, and an absent part means nobody was named rather than nothing was
 * loaded.
 */
export function toRestriction(audience?: Partial<RestrictionSelection> | null): RestrictionSelection {
    return {
        userTypes: audience?.userTypes ?? [],
        groupIds: audience?.groupIds ?? [],
        tagIds: audience?.tagIds ?? [],
        memberIds: audience?.memberIds ?? [],
        mode: audience?.mode ?? 'AND',
    }
}

/**
 * Presents three separate selections - user types, groups and tags - as the single
 * {@link RestrictionSelection} the shared restriction editor speaks, and writes edits back to
 * them.
 *
 * Several features store the three parts as separate fields rather than as one object, so this is
 * the adapter between that storage shape and the editor's. The parts always combine with AND, so
 * that is fixed here rather than being carried by every caller.
 *
 * <p>Individually named members are optional, because most of these editors do not offer them and
 * would otherwise have to invent a fourth list to pass. A caller that leaves them out gets an empty
 * member list, exactly as before; one that passes them keeps whatever was already stored, rather
 * than dropping it the next time somebody saves.
 */
export function toRestrictionSelection(
    userTypes: {value: string[]},
    groupIds: {value: number[]},
    tagIds: {value: number[]},
    memberIds?: {value: number[]},
): WritableComputedRef<RestrictionSelection> {
    return computed({
        get: (): RestrictionSelection => ({
            userTypes: userTypesOf(userTypes.value),
            groupIds: groupIds.value,
            tagIds: tagIds.value,
            memberIds: memberIds?.value ?? [],
            mode: 'AND',
        }),
        set: (value: RestrictionSelection) => {
            userTypes.value = value.userTypes
            groupIds.value = value.groupIds
            tagIds.value = value.tagIds
            if (memberIds) memberIds.value = value.memberIds
        },
    })
}

/** Something a restriction names by id, as the screen has it loaded. */
interface NamedChoice {
    id: number
    name: string
}

/** What the user types, groups and tags of a restriction are called, each kind on its own. */
export interface RestrictionLabels {
    userTypes: string[]
    groups: string[]
    tags: string[]
}

/**
 * Names the user types, groups and tags of a restriction. A group or tag the screen has not loaded is
 * named by its number.
 */
export function restrictionLabels(
    restriction: Pick<RestrictionSelection, 'userTypes' | 'groupIds' | 'tagIds'>,
    groups: readonly NamedChoice[],
    tags: readonly NamedChoice[],
): RestrictionLabels {
    const nameOf = (choices: readonly NamedChoice[], id: number) => choices.find(choice => choice.id === id)?.name ?? `#${id}`
    return {
        userTypes: restriction.userTypes.map(type => StationUserTypeLabels[type]),
        groups: restriction.groupIds.map(id => nameOf(groups, id)),
        tags: restriction.tagIds.map(id => nameOf(tags, id)),
    }
}
