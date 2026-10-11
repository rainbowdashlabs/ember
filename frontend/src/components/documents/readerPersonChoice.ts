/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, watch, type ComputedRef, type Ref} from 'vue'
import type {ReaderPerson} from '@/composables/useReaderPeople'

/**
 * Which of the reader's people a screen shows: the reader themselves at first, and whoever was chosen
 * after that for as long as they are among the people.
 *
 * @param people the reader and the members in their care
 * @return the chosen person's member id, null until the people are known
 */
export function useReaderPersonChoice(people: ComputedRef<ReaderPerson[]>): {selected: Ref<number | null>} {
    const selected = ref<number | null>(null)
    watch(people, list => {
        if (!list.some(person => person.id === selected.value)) selected.value = list[0]?.id ?? null
    }, {immediate: true})
    return {selected}
}
