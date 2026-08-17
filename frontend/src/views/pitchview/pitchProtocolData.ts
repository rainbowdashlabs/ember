/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {EvaluationResponse, TestProtocolItem, TestProtocolSection} from '@/api/protocol'
import type {StationMember} from '@/api/types'
import type {PitchEvaluation, PitchGrading} from './pitchTypes'

/**
 * The badge test a demonstration works through. The grading panel and the evaluation table of the
 * application read these objects, so scores, sub-sections and the colour scale are the real ones.
 */
function section(id: number, name: string, maxPoints: number,
                 parentId: number | null = null, position = id): TestProtocolSection {
    return {id, protocolId: 1, parentId, name, description: '', maxPoints, passThreshold: null, position}
}

const KNOTS = section(1, 'Knoten und Stiche', 8)
const FIRST_AID = section(2, 'Erste Hilfe', 8)
const EQUIPMENT = section(3, 'Gerätekunde', 6)
const ON_VEHICLE = section(4, 'Am Fahrzeug', 4, 3)
const QUESTIONS = section(5, 'Fragen zur Wache', 6)

const SECTIONS = [KNOTS, FIRST_AID, EQUIPMENT, ON_VEHICLE, QUESTIONS]

function item(id: number, sectionId: number, label: string, points: number): TestProtocolItem {
    return {id, sectionId, label, description: '', points, position: id}
}

const ITEMS: TestProtocolItem[] = [
    item(1, 3, 'Strahlrohr benannt und gehalten', 1),
    item(2, 3, 'Kupplung sauber verbunden', 2),
    item(3, 3, 'Saugschlauch zugeordnet', 1),
    item(4, 3, 'Verteiler bedient', 2),
    item(5, 4, 'Geräteraum richtig zugeordnet', 2),
    item(6, 4, 'Beladung benannt', 2),
]

/** The section the tester is standing in, with three of its points already ticked. */
export const PROTOCOL_GRADING: PitchGrading = {
    section: EQUIPMENT,
    childSections: [ON_VEHICLE],
    sectionItems: (sectionId: number) => ITEMS.filter(entry => entry.sectionId === sectionId),
    checks: new Map([[1, true], [2, true], [5, true]]),
    score: 5,
    maxPoints: 10,
    done: false,
}

function member(id: number, name: string): StationMember {
    return {id, stationId: 'wache', accountId: id, name}
}

const MEMBERS = [member(1, 'Anna Müller'), member(2, 'Ben Krüger'),
    member(3, 'Clara Weiß'), member(4, 'Jonas Behr')]

const SCORES: Record<number, number[]> = {
    1: [8, 7, 6, 6],
    2: [6, 8, 4, 6],
    3: [5, 4, 3, 3],
    4: [4, 3, 2, 2],
    5: [6, 5, 3, 3],
}

const EVAL_DATA: EvaluationResponse = {
    protocolName: 'Jugendflamme Stufe 1',
    testDate: new Date().toISOString(),
    sections: SECTIONS,
    sectionMaxPoints: {1: 8, 2: 8, 3: 6, 4: 4, 5: 6},
    passThreshold: 24,
    members: MEMBERS.map((entry, index) => ({
        memberId: entry.id,
        totalScore: SECTIONS.filter(s => !s.parentId)
            .reduce((sum, s) => sum + (SCORES[s.id]?.[index] ?? 0)
                + SECTIONS.filter(child => child.parentId === s.id)
                    .reduce((childSum, child) => childSum + (SCORES[child.id]?.[index] ?? 0), 0), 0),
        sectionScores: Object.fromEntries(
            SECTIONS.map(s => [s.id, SCORES[s.id]?.[index] ?? 0])),
    })),
}

export const PROTOCOL_EVALUATION: PitchEvaluation = {
    evalData: EVAL_DATA,
    memberMap: new Map(MEMBERS.map(entry => [entry.id, entry])),
}
