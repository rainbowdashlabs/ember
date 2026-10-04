/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {DocumentLanguage, PronounRole} from '@/api/generated/schema'
import {pronounOffer} from './genderPronouns'

/** The pronoun offer as the server sends it, for the tests of the gender field. */
export const PRONOUN_OFFER = pronounOffer([
    {
        language: DocumentLanguage.DE,
        code: 'de',
        roles: [PronounRole.SUBJECT, PronounRole.OBJECT, PronounRole.DATIVE, PronounRole.POSSESSIVE],
        male: {subject: 'er', object: 'ihn', dative: 'ihm', possessive: 'sein'},
        female: {subject: 'sie', object: 'sie', dative: 'ihr', possessive: 'ihr'},
    },
    {
        language: DocumentLanguage.EN,
        code: 'en',
        roles: [PronounRole.SUBJECT, PronounRole.OBJECT, PronounRole.POSSESSIVE],
        male: {subject: 'he', object: 'him', dative: null, possessive: 'his'},
        female: {subject: 'she', object: 'her', dative: null, possessive: 'her'},
    },
])
