/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {PronounSet} from '@/api/generated/schema'

/** The pronouns of every answer of a gender field, by answer and then by language. */
export type GenderPronouns = Record<string, Record<string, PronounSet>>

/** The pronouns of one answer, by language. */
export type AnswerPronouns = Record<string, PronounSet>

/** The roles a pronoun plays in a sentence, as the server names them. */
export type PronounRole = keyof PronounSet

/** What an answer stands for: one of the two predefined sets, the member's name, or words of its own. */
export type PronounChoice = 'MALE' | 'FEMALE' | 'NAME' | 'OWN'

/**
 * The languages a document is written in, with the roles each tells apart. English has no dative of its
 * own; it is left empty and the server takes the object for it.
 */
export const PRONOUN_LANGUAGES: readonly {language: string, roles: readonly PronounRole[]}[] = [
    {language: 'de', roles: ['subject', 'object', 'dative', 'possessive']},
    {language: 'en', roles: ['subject', 'object', 'possessive']},
]

/** The two answers a new gender field starts with, and the pronouns they carry in every language. */
export const PRESETS: Readonly<Record<'MALE' | 'FEMALE', AnswerPronouns>> = {
    MALE: {
        de: {subject: 'er', object: 'ihn', dative: 'ihm', possessive: 'sein'},
        en: {subject: 'he', object: 'him', dative: null, possessive: 'his'},
    },
    FEMALE: {
        de: {subject: 'sie', object: 'sie', dative: 'ihr', possessive: 'ihr'},
        en: {subject: 'she', object: 'her', dative: null, possessive: 'her'},
    },
}

function sameSet(left: PronounSet | undefined, right: PronounSet): boolean {
    return !!left && left.subject === right.subject && left.object === right.object
        && (left.dative ?? null) === (right.dative ?? null) && left.possessive === right.possessive
}

function matches(pronouns: AnswerPronouns, preset: AnswerPronouns): boolean {
    return Object.keys(preset).every(language => sameSet(pronouns[language], preset[language] as PronounSet))
        && Object.keys(pronouns).every(language => language in preset)
}

/** Whether no language names a word, which is the same as using the name. */
export function saysNothing(pronouns: AnswerPronouns | undefined): boolean {
    if (!pronouns) return true
    return Object.values(pronouns).every(set =>
        [set.subject, set.object, set.dative, set.possessive].every(word => !word?.trim()))
}

/**
 * What an answer's pronouns stand for, read from the words themselves.
 *
 * @param pronouns the answer's pronouns, or nothing
 */
export function choiceOf(pronouns: AnswerPronouns | undefined): PronounChoice {
    if (saysNothing(pronouns) || !pronouns) return 'NAME'
    if (matches(pronouns, PRESETS.MALE)) return 'MALE'
    if (matches(pronouns, PRESETS.FEMALE)) return 'FEMALE'
    return 'OWN'
}

/**
 * The pronouns an answer takes when one of the choices is picked for it. Picking words of its own starts
 * from whatever the answer already had, so nothing typed is lost by looking at the choice.
 *
 * @param choice  what was picked
 * @param current what the answer had before
 */
export function pronounsFor(choice: PronounChoice, current: AnswerPronouns | undefined): AnswerPronouns | undefined {
    switch (choice) {
        case 'MALE':
        case 'FEMALE':
            return structuredClone(PRESETS[choice])
        case 'NAME':
            return undefined
        case 'OWN':
            return current ? structuredClone(current) : {}
    }
}

/**
 * The pronouns as the server keeps them: only for answers the field still offers, and none for an answer
 * that uses the name.
 *
 * @param pronouns every answer's pronouns as the editor holds them
 * @param answers  the answers the field offers
 */
export function storedPronouns(pronouns: GenderPronouns, answers: readonly string[]): GenderPronouns {
    const stored: GenderPronouns = {}
    for (const answer of answers) {
        const own = pronouns[answer]
        if (!saysNothing(own) && own) stored[answer] = own
    }
    return stored
}
