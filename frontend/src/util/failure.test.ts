/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {createI18n} from 'vue-i18n'
import de from '@/i18n/de-DE'
import {describeFailure, FailureKind, formatRefusalDetail, refusalSentence, technicalSummary} from './failure'

/** Returns the key, so a test can see which message was chosen without depending on its wording. */
const t = (key: string) => key

function rejected(status: number, body?: Record<string, unknown>) {
    return {response: {status, data: body ?? {}}}
}

/**
 * A translator that has German for one refusal and nothing for any other, which is what adopting
 * these one at a time looks like. Everything unwritten comes back as its key, the way vue-i18n
 * answers for a key nobody has written.
 */
const translating = (key: string) => (key === 'refusal.F-012'
    ? 'Dieses Formular hast du bereits ausgefüllt.'
    : key)

describe('a refusal said in the reader\'s language', () => {
    it('prefers what we wrote over what the server said', () => {
        const failure = describeFailure(
            rejected(409, {code: 'F-012', message: 'You have already answered this form'}),
            translating,
        )

        expect(failure.message).toBe('Dieses Formular hast du bereits ausgefüllt.')
        expect(failure.code).toBe('F-012')
    })

    /** The server's own sentence still says what happened, which beats a German sentence that does not. */
    it('keeps the server\'s sentence for a refusal nobody has translated yet', () => {
        const failure = describeFailure(
            rejected(409, {code: 'Q-001', message: 'The paper is already in'}),
            translating,
        )

        expect(failure.message).toBe('The paper is already in')
    })

    it('keeps the server\'s own words for the report even when it shows ours', () => {
        const failure = describeFailure(
            rejected(409, {code: 'F-012', message: 'You have already answered this form'}),
            translating,
        )

        expect(failure.technical).toBe('You have already answered this form')
    })

    /**
     * A dot is how vue-i18n walks into a nested message, so a code carrying a hyphen is only safe
     * if the hyphen stays an ordinary character inside one segment. This asks the real translator
     * rather than assuming, because getting it wrong shows every reader an English sentence with a
     * German page around it and nothing anywhere says why.
     */
    it('reaches a hyphenated code through the real translator', () => {
        const i18n = createI18n({
            legacy: false,
            locale: 'de-DE',
            fallbackLocale: 'de-DE',
            messages: {'de-DE': {refusal: {'F-001': 'Das Formular gibt es nicht mehr.'}}, en: {}},
        })

        const failure = describeFailure(
            rejected(404, {code: 'F-001', message: 'That form is not here any more'}),
            (key: string) => i18n.global.t(key),
        )

        expect(failure.message).toBe('Das Formular gibt es nicht mehr.')
    })

    it('says nothing different where the refusal carries no code', () => {
        const failure = describeFailure(rejected(409, {message: 'You have already answered this form'}), translating)

        expect(failure.message).toBe('You have already answered this form')
        expect(failure.code).toBeUndefined()
    })
})

/** The German the product ships, so the wording around a detail is checked as a reader sees it. */
function german() {
    const i18n = createI18n({legacy: false, locale: 'de-DE', fallbackLocale: 'de-DE', messages: {'de-DE': de, en: {}}})
    return (key: string, named?: Record<string, unknown>) => i18n.global.t(key, named ?? {})
}

describe('the value a refusal was about', () => {
    /** CU-145, the association's pool: the number that says what would still fit is the whole point. */
    it('names what is left of the pool after our sentence', () => {
        const failure = describeFailure(rejected(400, {
            code: 'CU-145',
            message: 'That is more room than the cluster has left to hand out, so nothing was changed: 0 B free of 1.0 GiB',
            detail: {kind: 'ROOM', freeBytes: 0, totalBytes: 1024 ** 3},
        }), german())

        expect(failure.message).toBe(
            'Das ist mehr Speicherplatz, als der Verbund noch vergeben kann, es wurde nichts geändert (frei: 0 B von 1.0 GiB)')
    })

    it('names a count as a number', () => {
        const failure = describeFailure(rejected(409, {
            code: 'M-194',
            message: 'Content is still limited to this group: 3',
            detail: {kind: 'COUNT', count: 3, unit: null},
        }), german())

        expect(failure.message).toMatch(/ \(3\)$/)
    })

    it('leaves the server\'s sentence alone, since it already names the value', () => {
        const failure = describeFailure(rejected(400, {
            code: 'Q-999',
            message: 'Something nobody translated: 3',
            detail: {kind: 'COUNT', count: 3, unit: null},
        }), german())

        expect(failure.message).toBe('Something nobody translated: 3')
    })

    it('says our sentence alone where the refusal named no value', () => {
        const failure = describeFailure(rejected(400, {code: 'CU-145', message: 'That is more room'}), german())

        expect(failure.message).toBe('Das ist mehr Speicherplatz, als der Verbund noch vergeben kann, es wurde nichts geändert')
    })
})

describe('refusalSentence', () => {
    it('says a recorded refusal in German with what it was about', () => {
        expect(refusalSentence('D-091', {kind: 'TEXT', text: 'Schule'}, german()))
            .toBe('Für dieses Mitglied fehlen Daten, die die Vorlage braucht, es wurde kein Dokument abgelegt (Schule)')
    })

    it('names an untranslated refusal by its code', () => {
        expect(refusalSentence('X-999', null, translating)).toBe('X-999')
    })
})

describe('formatRefusalDetail', () => {
    const t = german()

    it('shows text as it was typed', () => {
        expect(formatRefusalDetail({kind: 'TEXT', text: 'Ausgabe'}, t)).toBe('Ausgabe')
    })

    it('shows a count with the unit it was counted in', () => {
        expect(formatRefusalDetail({kind: 'COUNT', count: 12, unit: null}, t)).toBe('12')
        expect(formatRefusalDetail({kind: 'COUNT', count: 31, unit: 'DAYS'}, t)).toBe('31 Tage')
        expect(formatRefusalDetail({kind: 'COUNT', count: 24, unit: 'HOURS'}, t)).toBe('24 Stunden')
        expect(formatRefusalDetail({kind: 'COUNT', count: 16, unit: 'YEARS'}, t)).toBe('16 Jahre')
        expect(formatRefusalDetail({kind: 'COUNT', count: 2, unit: 'LINE'}, t)).toBe('Zeile 2')
    })

    it('shows the room left in the units of the storage screens', () => {
        expect(formatRefusalDetail({kind: 'ROOM', freeBytes: 512 * 1024 ** 2, totalBytes: 2 * 1024 ** 3}, t))
            .toBe('frei: 512.0 MiB von 2.0 GiB')
    })
})

describe('describeFailure', () => {
    it('reads the kind off the status', () => {
        expect(describeFailure(rejected(401), t).kind).toBe(FailureKind.SIGNED_OUT)
        expect(describeFailure(rejected(403), t).kind).toBe(FailureKind.DENIED)
        expect(describeFailure(rejected(404), t).kind).toBe(FailureKind.GONE)
        expect(describeFailure(rejected(409), t).kind).toBe(FailureKind.CONFLICT)
        expect(describeFailure(rejected(413), t).kind).toBe(FailureKind.TOO_LARGE)
        expect(describeFailure(rejected(429), t).kind).toBe(FailureKind.TOO_OFTEN)
        expect(describeFailure(rejected(400), t).kind).toBe(FailureKind.REJECTED)
        expect(describeFailure(rejected(422), t).kind).toBe(FailureKind.REJECTED)
        expect(describeFailure(rejected(500), t).kind).toBe(FailureKind.SERVER_FAULT)
        expect(describeFailure(rejected(503), t).kind).toBe(FailureKind.SERVER_FAULT)
        expect(describeFailure(rejected(504), t).kind).toBe(FailureKind.TIMEOUT)
    })

    /**
     * The distinction the whole thing exists for. Telling somebody to file a bug because their station
     * never gave them a permission sends them to strangers who cannot help.
     */
    it('does not offer a report for what the reader or their station can put right', () => {
        expect(describeFailure(rejected(403), t).reportable).toBe(false)
        expect(describeFailure(rejected(401), t).reportable).toBe(false)
        expect(describeFailure(rejected(404), t).reportable).toBe(false)
        expect(describeFailure(rejected(409), t).reportable).toBe(false)
        expect(describeFailure(rejected(413), t).reportable).toBe(false)
        expect(describeFailure(rejected(429), t).reportable).toBe(false)
        expect(describeFailure(rejected(400), t).reportable).toBe(false)
    })

    it('offers a report where it looks like our fault', () => {
        expect(describeFailure(rejected(500), t).reportable).toBe(true)
        expect(describeFailure(rejected(504), t).reportable).toBe(true)
        expect(describeFailure({}, t).reportable).toBe(true)
    })

    /**
     * A refusal knows which field was wrong and we do not, so its own words win. A fault answers with a
     * class name, which is not a sentence, so ours do.
     */
    it('prefers the server wording for a refusal and ours for a fault', () => {
        const refused = describeFailure(rejected(400, {message: 'Der Ordner fehlt'}), t)
        expect(refused.message).toBe('Der Ordner fehlt')

        const broke = describeFailure(rejected(500, {message: 'NullPointerException'}), t)
        expect(broke.message).toBe('failure.SERVER_FAULT.message')
        expect(broke.technical).toBe('NullPointerException')
    })

    it('says nothing technical where the server said nothing worth keeping', () => {
        expect(describeFailure(rejected(400, {message: 'Der Ordner fehlt'}), t).technical).toBeUndefined()
    })

    /** Every kind has to say what to do about it, or the reader is back to guessing. */
    it('carries a message and guidance for every kind', () => {
        for (const [kind, thrown] of Object.entries(oneOfEach())) {
            const described = describeFailure(thrown, t)
            expect(described.kind, kind).toBe(kind)
            expect(described.message, kind).toBeTruthy()
            expect(described.guidance, kind).toBeTruthy()
        }
    })

    /** Somebody with no signal must not be told that Ember is broken. */
    it('knows a dropped connection from a broken server', () => {
        expect(describeFailure({code: 'ERR_NETWORK', message: 'Network Error'}, t).kind)
            .toBe(FailureKind.OFFLINE)
        expect(describeFailure({code: 'ERR_NETWORK'}, t).reportable).toBe(false)
        expect(describeFailure({code: 'ECONNABORTED', message: 'timeout of 5000ms exceeded'}, t).kind)
            .toBe(FailureKind.TIMEOUT)
    })

    it('keeps the status where there was one', () => {
        expect(describeFailure(rejected(418), t).status).toBe(418)
        expect(describeFailure({}, t).status).toBeUndefined()
    })
})

/**
 * A page the server renders fetches with Nuxt's own client rather than axios, and what that leaves
 * behind is a different shape: the status and the body sit on the error itself, and the response it
 * came with is dropped on the way from the server to the browser.
 */
describe('a refusal fetched while the server renders', () => {
    const refusal = {error: 'Not Found', message: 'No page is reached by this link', code: 'P-011'}

    it('reads the status and the body off an error handed over from the server', () => {
        const failure = describeFailure({statusCode: 404, statusMessage: 'Not Found', data: refusal}, t)

        expect(failure.kind).toBe(FailureKind.GONE)
        expect(failure.status).toBe(404)
        expect(failure.code).toBe('P-011')
        expect(failure.message).toBe('No page is reached by this link')
    })

    it('reads them off a fetch that failed in the browser', () => {
        const failure = describeFailure({statusCode: 404, data: refusal, response: {status: 404, _data: refusal}}, t)

        expect(failure.kind).toBe(FailureKind.GONE)
        expect(failure.code).toBe('P-011')
    })

    it('names the status in the report', () => {
        expect(technicalSummary({statusCode: 404, data: refusal}))
            .toBe('HTTP 404 · Not Found · No page is reached by this link')
    })
})

describe('technicalSummary', () => {
    it('names the status and whatever the server said', () => {
        expect(technicalSummary(rejected(500, {message: 'NullPointerException'})))
            .toBe('HTTP 500 · NullPointerException')
        expect(technicalSummary(rejected(403, {error: 'ForbiddenResponse', message: 'Kein Zugriff'})))
            .toBe('HTTP 403 · ForbiddenResponse · Kein Zugriff')
    })

    it('says so where nothing came back at all', () => {
        expect(technicalSummary({message: 'Network Error'})).toBe('no response · Network Error')
    })
})

/** One thrown thing per kind, so the loop above covers all of them and not only the ones with a status. */
function oneOfEach(): Record<string, unknown> {
    return {
        [FailureKind.OFFLINE]: {code: 'ERR_NETWORK', message: 'Network Error'},
        [FailureKind.TIMEOUT]: {code: 'ECONNABORTED', message: 'timeout of 5000ms exceeded'},
        [FailureKind.SIGNED_OUT]: rejected(401),
        [FailureKind.DENIED]: rejected(403),
        [FailureKind.GONE]: rejected(404),
        [FailureKind.CONFLICT]: rejected(409),
        [FailureKind.REJECTED]: rejected(400),
        [FailureKind.TOO_LARGE]: rejected(413),
        [FailureKind.TOO_OFTEN]: rejected(429),
        [FailureKind.SERVER_FAULT]: rejected(500),
        [FailureKind.UNKNOWN]: {},
    }
}
