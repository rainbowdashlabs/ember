/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, type Ref} from 'vue'
import {protocol} from '@/api'
import type {ExaminerCandidate, RunExaminers, TestProtocolItem, TestProtocolRun, TestProtocolSection} from '@/api/generated/schema'
import type {MemberOption} from '@/components/input/select/memberOption'
import {protocolTree} from '../protocolTree'

/**
 * The examiners of one run as they are being planned: who is named on each section, who reaches it
 * from a section above, and which sections nobody examines yet.
 *
 * <p>The plan is held as member ids in text, the form the member menu reads and writes, and turned
 * into numbers only on the way to the server.
 *
 * @param runId the run being planned
 */
export function useRunPlan(runId: Ref<number>) {
    const run = ref<TestProtocolRun | null>(null)
    const sections = ref<TestProtocolSection[]>([])
    const items = ref<TestProtocolItem[]>([])
    const candidates = ref<ExaminerCandidate[]>([])
    const own = ref(new Map<number, string[]>())

    const tree = computed(() => protocolTree(sections.value, items.value))

    const candidateOptions = computed<MemberOption[]>(() =>
        candidates.value.map(candidate => ({value: String(candidate.memberId), name: candidate.name})))

    const nameOf = computed(() => new Map(candidates.value.map(candidate => [String(candidate.memberId), candidate.name])))

    /** Everybody examining each section: named on it, or on a section above it. */
    const effective = computed(() => {
        const result = new Map<number, Set<string>>()
        const walk = (parentId: number | null, inherited: Set<string>) => {
            for (const section of tree.value.childrenOf(parentId)) {
                const examiners = new Set([...inherited, ...(own.value.get(section.id) ?? [])])
                result.set(section.id, examiners)
                walk(section.id, examiners)
            }
        }
        walk(null, new Set())
        return result
    })

    const planned = computed(() => [...own.value.values()].some(memberIds => memberIds.length > 0))

    /** The sections nobody examines, which every examiner of the run may then grade. */
    const unassigned = computed(() => planned.value
        ? sections.value.filter(section => (effective.value.get(section.id)?.size ?? 0) === 0
            && tree.value.itemsOf(section.id).length > 0)
        : [])

    function ownOf(sectionId: number): string[] {
        return own.value.get(sectionId) ?? []
    }

    function setOwn(sectionId: number, memberIds: string[]) {
        const next = new Map(own.value)
        next.set(sectionId, memberIds)
        own.value = next
    }

    /** Who examines a section because they were named on a section above it. */
    function inheritedNames(section: TestProtocolSection): string[] {
        const above = section.parentId == null ? new Set<string>() : effective.value.get(section.parentId) ?? new Set<string>()
        return [...above].map(id => nameOf.value.get(id) ?? id)
    }

    function adopt(plan: RunExaminers) {
        own.value = new Map(plan.sections.map(entry => [entry.sectionId, entry.memberIds.map(String)]))
    }

    async function load() {
        const detail = await protocol.getRun(runId.value)
        run.value = detail.run
        const [protocolData, plan, offered] = await Promise.all([
            protocol.getProtocol(detail.run.protocolId),
            protocol.getExaminers(runId.value),
            protocol.listExaminerCandidates(),
        ])
        sections.value = protocolData.sections
        items.value = protocolData.items
        candidates.value = offered
        adopt(plan)
    }

    /** Takes the plan of the last planned run of the same protocol. Returns whether there was one. */
    async function copyPrevious(): Promise<boolean> {
        const previous = await protocol.getPreviousExaminers(runId.value)
        if (previous.sections.length === 0) return false
        adopt(previous)
        return true
    }

    async function save() {
        const plan: RunExaminers = {
            sections: [...own.value.entries()]
                .filter(([, memberIds]) => memberIds.length > 0)
                .map(([sectionId, memberIds]) => ({sectionId, memberIds: memberIds.map(Number)})),
        }
        await protocol.setExaminers(runId.value, plan)
    }

    return {
        run, tree, candidateOptions, planned, unassigned,
        ownOf, setOwn, inheritedNames, load, copyPrevious, save,
    }
}

/** A run's plan as the planning page holds it. */
export type RunPlan = ReturnType<typeof useRunPlan>
