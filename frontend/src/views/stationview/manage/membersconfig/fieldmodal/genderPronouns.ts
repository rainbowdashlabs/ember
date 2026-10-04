/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {PronounRole, type PronounLanguage, type PronounSet} from '@/api/generated/schema'
import {pronounLanguages} from '@/api/profileFields'
import {browserShallowRef} from '@/util/browserState'

/** The pronouns of every answer of a gender field, by answer and then by language code. */
export type GenderPronouns = Record<string, Record<string, PronounSet>>

/** The pronouns of one answer, by language code. */
export type AnswerPronouns = Record<string, PronounSet>

/** The two answers the server predefines. */
export type PronounPreset = 'MALE' | 'FEMALE'

/** What an answer stands for: one of the two predefined sets, the member's name, or words of its own. */
export type PronounChoice = PronounPreset | 'NAME' | 'OWN'

/** The word of a pronoun set each role is kept in. */
export const ROLE_WORD: Readonly<Record<PronounRole, keyof PronounSet>> = {
    [PronounRole.SUBJECT]: 'subject',
    [PronounRole.OBJECT]: 'object',
    [PronounRole.DATIVE]: 'dative',
    [PronounRole.POSSESSIVE]: 'possessive',
}

/**
 * What the server offers for the pronouns of a gender field.
 *
 * @property languages the languages a document is written in, each with the roles it tells apart
 * @property presets   the two predefined answers and their pronouns in every language
 */
export interface PronounOffer {
    languages: readonly PronounLanguage[]
    presets: Readonly<Record<PronounPreset, AnswerPronouns>>
}

/**
 * @param languages the languages as the server sends them
 * @returns the offer, with each predefined answer's pronouns keyed by language code as a field keeps them
 */
export function pronounOffer(languages: readonly PronounLanguage[]): PronounOffer {
    return {
        languages,
        presets: {
            MALE: Object.fromEntries(languages.map(entry => [entry.code, entry.male])),
            FEMALE: Object.fromEntries(languages.map(entry => [entry.code, entry.female])),
        },
    }
}

const loading = browserShallowRef<Promise<PronounOffer> | null>(null)

/**
 * The server's pronoun offer, asked once per page and shared by everybody who needs it. A request that
 * fails is asked again the next time.
 */
export function loadPronounOffer(): Promise<PronounOffer> {
    if (!loading.value) {
        const request = pronounLanguages().then(pronounOffer)
        request.catch(() => {
            loading.value = null
        })
        loading.value = request
    }
    return loading.value
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
 * @param presets  the two predefined answers
 */
export function choiceOf(pronouns: AnswerPronouns | undefined, presets: PronounOffer['presets']): PronounChoice {
    if (saysNothing(pronouns) || !pronouns) return 'NAME'
    if (matches(pronouns, presets.MALE)) return 'MALE'
    if (matches(pronouns, presets.FEMALE)) return 'FEMALE'
    return 'OWN'
}

/**
 * The pronouns an answer takes when one of the choices is picked for it. Picking words of its own starts
 * from whatever the answer already had, so nothing typed is lost by looking at the choice.
 *
 * @param choice  what was picked
 * @param current what the answer had before
 * @param presets the two predefined answers
 */
export function pronounsFor(
    choice: PronounChoice, current: AnswerPronouns | undefined, presets: PronounOffer['presets'],
): AnswerPronouns | undefined {
    switch (choice) {
        case 'MALE':
        case 'FEMALE':
            return structuredClone(presets[choice])
        case 'NAME':
            return undefined
        case 'OWN':
            return current ? structuredClone(current) : {}
    }
}
