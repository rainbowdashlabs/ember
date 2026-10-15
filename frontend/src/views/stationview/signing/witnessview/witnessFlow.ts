/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {StationUserType} from '@/api/generated/schema'
import type {WitnessFieldResponse, WitnessPersonResponse} from '@/api/generated/schema'

/**
 * The screens of signing in front of a witness. The witness holds the device for `person`, `document`,
 * `signer` and `confirm`; the person who signs holds it for the screens in between, which
 * {@link handedOver} names.
 */
export type WitnessStep = 'person' | 'document' | 'signer' | 'handOver' | 'read' | 'draw' | 'takeBack' | 'confirm'

/** Every screen in the order it comes, which numbers them for "Schritt 3 von 8". */
export const WITNESS_STEPS: readonly WitnessStep[] = ['person', 'document', 'signer', 'handOver', 'read', 'draw', 'takeBack', 'confirm']

/**
 * Where a screen stands among all of them, counted from 1.
 *
 * @param step the screen
 */
export function positionOf(step: WitnessStep): number {
    return WITNESS_STEPS.indexOf(step) + 1
}

/** The screens the person who signs holds the device for. */
const HANDED_OVER: ReadonlySet<WitnessStep> = new Set(['handOver', 'read', 'draw', 'takeBack'])

/**
 * Whether the device is in the hands of the person who signs, so nothing of the witness may be reachable.
 *
 * @param step the screen
 */
export function handedOver(step: WitnessStep): boolean {
    return HANDED_OVER.has(step)
}

/**
 * Where a chosen field leads: straight to handing the device over where the field names one person, to
 * choosing who stands in front where it names several, and nowhere where it names nobody.
 *
 * @param field the field the witness chose
 * @return the next screen and who signs, or null where nobody can sign the field now
 */
export function afterChoosing(field: WitnessFieldResponse): {step: WitnessStep; signer: WitnessPersonResponse | null} | null {
    const [only, ...others] = field.signers
    if (!only) return null
    if (others.length === 0) return {step: 'handOver', signer: only}
    return {step: 'signer', signer: null}
}

/**
 * The person a field's document is about, as the witness picked them, for a field opened from its tile.
 *
 * @param field the field
 * @return the person, or null where the document names nobody any more
 */
export function personOf(field: WitnessFieldResponse): WitnessPersonResponse | null {
    if (field.memberId === null || field.memberId === undefined) return null
    const signing = field.signers.find(signer => signer.memberId === field.memberId)
    return {memberId: field.memberId, name: field.memberName, userType: signing?.userType ?? StationUserType.MEMBER}
}

/**
 * The people whose name contains what was typed, ignoring case; everybody where nothing was typed.
 *
 * @param people the people the witness reaches
 * @param search what was typed
 */
export function peopleMatching(people: readonly WitnessPersonResponse[], search: string): WitnessPersonResponse[] {
    const wanted = search.trim().toLocaleLowerCase()
    if (!wanted) return [...people]
    return people.filter(person => person.name.toLocaleLowerCase().includes(wanted))
}
