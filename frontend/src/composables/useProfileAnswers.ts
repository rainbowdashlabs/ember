/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, shallowRef} from 'vue'
import type {LaidOutField} from '@/components/profilefields/ProfileFieldsLayout.vue'
import {valueFields} from '@/components/profilefields/fieldLayout'
import type {FieldOrigin} from '@/api/generated/schema'
import {decodeMergedValues, profileKey, type ProfileFieldValueEntry} from '@/util/profileFields'

/** One stored answer, naming who asked its question where both a station and its association ask. */
export type StoredProfileAnswer = ProfileFieldValueEntry & {origin?: FieldOrigin}

/** The questions put to one member and what they answered, as a port reads them. */
export interface ProfileAnswersSnapshot {
    fields: LaidOutField[]
    values: StoredProfileAnswer[]
}

/** One answer on its way back, as JSON, with the table its question lives in. */
export interface ProfileAnswerEntry {
    fieldId: number
    value: string
    origin: FieldOrigin
}

/**
 * Where one screen reads and writes a member's answers, and which of them this reader may write.
 *
 * <p>The station's member management, the member themselves, a guardian and the association each reach
 * the answers through their own address and pass their own locks; everything else about holding the
 * answers is the same and lives in {@link useProfileAnswers}.
 */
export interface ProfileAnswersPort {
    load(memberId: number): Promise<ProfileAnswersSnapshot>
    save(memberId: number, answers: ProfileAnswerEntry[]): Promise<void>
    canEdit(field: LaidOutField): boolean
}

/**
 * A member's answers as one screen holds them while they are filled in.
 *
 * <p>Every answer is held under its question's origin and id together. A station numbers its own
 * questions and its association numbers its own, so the bare id named two questions on a profile that
 * shows both, and a screen keyed by it showed one question's answer under the other and saved it into
 * the wrong table.
 *
 * <p>The save sends every answer this reader may write, not only the ones changed: the server skips what
 * did not change and records the rest. Which ones were touched is kept for the screen to say whether
 * there is anything to save.
 *
 * @param port where this screen reads and writes the answers
 */
export function useProfileAnswers(port: ProfileAnswersPort) {
    const fields = shallowRef<LaidOutField[]>([])
    const values = shallowRef<ReadonlyMap<string, string>>(new Map())
    const edited = shallowRef<ReadonlySet<string>>(new Set())

    function keyOf(field: LaidOutField): string {
        return profileKey(field.id, field.origin ?? 'STATION')
    }

    /** Takes the questions and answers as read, forgetting whatever was typed before. */
    function apply(snapshot: ProfileAnswersSnapshot) {
        fields.value = snapshot.fields
        values.value = decodeMergedValues(snapshot.values)
        edited.value = new Set()
    }

    /**
     * Puts the questions again, keeping every answer held. A member who becomes another kind of member
     * is asked other questions, and what was typed into the ones they are still asked stays.
     */
    function reask(next: LaidOutField[]) {
        fields.value = next
    }

    async function load(memberId: number) {
        apply(await port.load(memberId))
    }

    function valueOf(field: LaidOutField): string {
        return values.value.get(keyOf(field)) ?? ''
    }

    function update(field: LaidOutField, value: string) {
        const key = keyOf(field)
        values.value = new Map([...values.value, [key, value]])
        edited.value = new Set([...edited.value, key])
    }

    const dirty = computed(() => edited.value.size > 0)

    /** Every answer this reader may write, as the server takes it. */
    function answers(): ProfileAnswerEntry[] {
        return valueFields(fields.value)
            .filter(field => port.canEdit(field))
            .map(field => ({
                fieldId: field.id,
                value: JSON.stringify(valueOf(field)),
                origin: field.origin ?? 'STATION',
            }))
    }

    async function save(memberId: number) {
        await port.save(memberId, answers())
        edited.value = new Set()
    }

    return {fields: computed(() => fields.value), dirty, valueOf, update, apply, reask, load, answers, save}
}

/** What {@link useProfileAnswers} hands a screen. */
export type ProfileAnswers = ReturnType<typeof useProfileAnswers>
