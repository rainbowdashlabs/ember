/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import type {
    RunMemberWithProgress,
    TestProtocol,
    TestProtocolItem,
    TestProtocolRun,
    TestProtocolSection,
} from '@/api/generated/schema'
import type {MemberOption} from '@/components/input/select/memberOption'
import {protocolTree, type ProtocolTree} from '@/views/stationview/protocol/protocolTree'
import type {RunPlan} from '@/views/stationview/protocol/runplanview/useRunPlan'

/** Looks up a help text, so the demo protocol follows the help center's own sample names. */
type Translate = (key: string) => string

const STATION_ID = '00000000-0000-4000-8000-000000000001'
const CREATED_AT = '2026-03-02T18:00:00Z'

/** The sample sections, ids fixed so the demos below can point at them. */
export const DEMO_SECTION = {
    emergencyCall: 1,
    knots: 2,
    basicKnots: 3,
    advancedKnots: 4,
    hoses: 5,
} as const

function section(id: number, name: string, parentId: number | null, position: number): TestProtocolSection {
    return {id, name, parentId, position, description: '', maxPoints: null, passThreshold: null, protocolId: 1}
}

function item(id: number, sectionId: number, label: string, points: number, position: number, bonus = false): TestProtocolItem {
    return {id, sectionId, label, points, position, bonus, description: ''}
}

/** "Jugendflamme Stufe 1" with three top-level sections, a subsection two levels deep and one bonus point. */
export function demoProtocolSections(t: Translate): TestProtocolSection[] {
    return [
        section(DEMO_SECTION.emergencyCall, t('helpCenter.sample.protocol.emergencyCall'), null, 0),
        section(DEMO_SECTION.knots, t('helpCenter.sample.protocol.knots'), null, 1),
        section(DEMO_SECTION.basicKnots, t('helpCenter.sample.protocol.basicKnots'), DEMO_SECTION.knots, 0),
        section(DEMO_SECTION.advancedKnots, t('helpCenter.sample.protocol.advancedKnots'), DEMO_SECTION.basicKnots, 0),
        section(DEMO_SECTION.hoses, t('helpCenter.sample.protocol.hoses'), null, 2),
    ]
}

/** The points of the sample protocol, 33 at most, with the bowline as a bonus on top. */
export function demoProtocolItems(t: Translate): TestProtocolItem[] {
    return [
        item(1, DEMO_SECTION.emergencyCall, t('helpCenter.sample.protocol.fiveW'), 5, 0),
        item(2, DEMO_SECTION.emergencyCall, t('helpCenter.sample.protocol.number112'), 2, 1),
        item(3, DEMO_SECTION.emergencyCall, t('helpCenter.sample.protocol.number110'), 1, 2),
        item(4, DEMO_SECTION.basicKnots, t('helpCenter.sample.protocol.clove'), 5, 0),
        item(5, DEMO_SECTION.basicKnots, t('helpCenter.sample.protocol.sheetBend'), 5, 1),
        item(6, DEMO_SECTION.advancedKnots, t('helpCenter.sample.protocol.bowline'), 1, 0, true),
        item(7, DEMO_SECTION.hoses, t('helpCenter.sample.protocol.rollOutHose'), 10, 0),
        item(8, DEMO_SECTION.hoses, t('helpCenter.sample.protocol.coupleHoses'), 5, 1),
    ]
}

/** The sample protocol read as the tree every protocol screen walks. */
export function demoProtocolTree(t: Translate): ProtocolTree {
    return protocolTree(demoProtocolSections(t), demoProtocolItems(t))
}

function protocol(id: number, name: string, description: string, passThreshold: number | null): TestProtocol {
    return {id, name, description, passThreshold, stationId: STATION_ID, createdAt: CREATED_AT, updatedAt: CREATED_AT}
}

/** Two of the station's own protocols, the first one with a pass mark. */
export function demoProtocols(t: Translate): TestProtocol[] {
    return [
        protocol(1, t('helpCenter.sample.protocol.jugendflamme1'), t('helpCenter.sample.protocol.jugendflamme1Text'), 25),
        protocol(2, t('helpCenter.sample.protocol.bronzeBadge'), '', null),
    ]
}

/** The members allowed to grade protocols, as the examiner menu offers them. */
export function demoExaminers(t: Translate): MemberOption[] {
    return [
        {value: '1', name: t('helpCenter.sample.people.annaSchmidt')},
        {value: '2', name: t('helpCenter.sample.people.tomMueller')},
        {value: '3', name: t('helpCenter.sample.people.lisaWeber')},
    ]
}

/**
 * A planned run: Anna examines the emergency call, Tom and Lisa the knots with everything under them,
 * and nobody was named for the hoses.
 */
export function demoRunPlan(t: Translate): RunPlan {
    const sections = demoProtocolSections(t)
    const tree = demoProtocolTree(t)
    const examiners = demoExaminers(t)
    const own = new Map<number, string[]>([
        [DEMO_SECTION.emergencyCall, ['1']],
        [DEMO_SECTION.knots, ['2', '3']],
    ])
    const ownOf = (sectionId: number) => own.get(sectionId) ?? []
    const nameOf = (memberId: string) => examiners.find(examiner => examiner.value === memberId)?.name ?? memberId

    function inheritedNames(of: TestProtocolSection): string[] {
        const names = new Set<string>()
        let parentId = of.parentId
        while (parentId !== null) {
            for (const memberId of ownOf(parentId)) names.add(nameOf(memberId))
            parentId = sections.find(candidate => candidate.id === parentId)?.parentId ?? null
        }
        return [...names]
    }

    return {
        run: ref<TestProtocolRun | null>(null),
        tree: computed(() => tree),
        candidateOptions: computed(() => examiners),
        planned: computed(() => true),
        unassigned: computed(() => sections.filter(candidate => candidate.id === DEMO_SECTION.hoses)),
        ownOf,
        setOwn: () => undefined,
        inheritedNames,
        load: () => Promise.resolve(),
        copyPrevious: () => Promise.resolve(true),
        save: () => Promise.resolve(),
    }
}

/** One member of a run with their progress, as the run page lists them. */
export interface DemoRunMember {
    name: string
    progress: RunMemberWithProgress
}

function runMember(id: number, name: string, done: number[], totalScore: number, completed: boolean,
                   lockedBy: number | null): DemoRunMember {
    return {
        name,
        progress: {
            doneSectionIds: done,
            sectionsDone: done.length,
            sectionsTotal: 3,
            member: {
                id, memberId: id, runId: 1, totalScore, completed,
                locked: lockedBy !== null, lockedBy, lockedAt: lockedBy === null ? null : CREATED_AT,
            },
        },
    }
}

/** Three members of a run: one finished, one being graded right now and one not started. */
export function demoRunMembers(t: Translate): DemoRunMember[] {
    return [
        runMember(11, t('helpCenter.sample.people.lenaMueller'),
            [DEMO_SECTION.emergencyCall, DEMO_SECTION.knots, DEMO_SECTION.hoses], 31, true, null),
        runMember(12, t('helpCenter.sample.people.maxBauer'), [DEMO_SECTION.emergencyCall], 7, false, 2),
        runMember(13, t('helpCenter.sample.people.jonasWeber'), [], 0, false, null),
    ]
}
