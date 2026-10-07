/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    JobMemberStatus,
    type GenerationJobSummary,
    type JobMemberResult,
    type JobPreviewRequest,
    type MemberGaps,
} from '@/api/generated/schema'
import type {RestrictionSelection} from '@/api/types'
import {refusalSentence, type Translate} from '@/util/failure'
import {missingLabels} from '../generation'

/** Whom a run generates for, as the requests to look at a run and to start one name it. */
export type MemberSelection = Pick<JobPreviewRequest, 'memberIds' | 'audience'>

/**
 * Whom a run generates for: the members chosen in the member list where there are any, otherwise the
 * members the audience takes in.
 *
 * @param memberIds the members chosen one by one, or null where the audience decides
 * @param audience  the audience
 */
export function selectionFor(memberIds: readonly number[] | null, audience: RestrictionSelection): MemberSelection {
    return memberIds === null ? {memberIds: null, audience} : {memberIds: [...memberIds], audience: null}
}

/** Whether a run is still generating. */
export function isRunning(job: GenerationJobSummary): boolean {
    return job.finishedAt === null || job.finishedAt === undefined
}

/** How many members of a run are done, filed or failed. */
export function doneOf(job: GenerationJobSummary): number {
    return job.filed + job.failed
}

/**
 * What keeps a member's document from being drawn, or what data it lacks.
 *
 * @param gap the member and what stands in the way
 * @param t   the translator
 */
export function gapText(gap: MemberGaps, t: Translate): string {
    if (gap.refusalCode) return refusalSentence(gap.refusalCode, null, t)
    return t('documentTemplates.bulk.gapMissing', {values: missingLabels(gap.missing)})
}

/** The members of a run whose document could not be filed. */
export function failuresOf(members: readonly JobMemberResult[]): JobMemberResult[] {
    return members.filter(member => member.status === JobMemberStatus.FAILED)
}

/**
 * Why a member's document could not be filed.
 *
 * @param result how the run went for the member
 * @param t      the translator
 */
export function failureText(result: JobMemberResult, t: Translate): string {
    return result.refusalCode ? refusalSentence(result.refusalCode, result.detail, t) : ''
}
