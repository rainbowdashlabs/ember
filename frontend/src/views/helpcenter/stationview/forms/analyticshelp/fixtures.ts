/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    FormQuestionType,
    TagVisibility,
    type FormAnalytics,
    type FormQuestionInfo,
    type FormResponseEntry,
    type FormResultGroup,
    type MemberGroup,
    type UserTag,
} from '@/api/generated/schema'
import type {Translate} from '@/util/failure'
import {numberedOptions} from '@/util/formOptions'

const STATION = 'wache'
const LIKED_ID = 1
const SATISFACTION_ID = 2

/** The answers of twelve members to a choice and a rating question, counted over everyone. */
export function sampleResults(t: Translate): FormAnalytics {
    return {
        formId: 1,
        totalResponses: 12,
        groupsOverlap: false,
        missingResponses: [],
        responseIds: [1],
        questions: [likedQuestion(t), {
            questionId: SATISFACTION_ID,
            questionType: FormQuestionType.RATING,
            title: t('helpCenter.sample.forms.satisfaction'),
            config: {questionType: FormQuestionType.RATING, scale: 5},
        }],
        groups: [{
            key: 'all', label: '', responseCount: 12, tallies: [
                {questionId: LIKED_ID, answerCount: 12, optionCounts: {o0: 7, o1: 3, o2: 2}, otherCount: 0},
                {questionId: SATISFACTION_ID, answerCount: 12, ratingCounts: [1, 1, 2, 3, 5]},
            ],
        }],
    }
}

/** The one response the individual tab pages through to first. */
export function sampleResponse(): FormResponseEntry {
    return {
        id: 1, formId: 1, memberId: 1, submittedBy: null, submittedByName: null,
        memberIdentity: {stationUid: '', memberUid: '', name: 'Max Mustermann', nameColor: null, displayTag: null, stationName: null},
        submittedAt: '2026-05-15T12:32:00Z', updatedAt: '2026-05-15T12:32:00Z',
        acknowledgedAt: null, acknowledgedBy: null, acknowledgedByIdentity: null,
    }
}

/** What the sample response answered, as the stored answer of each question. */
export function sampleAnswerOf(questionId: number): string {
    if (questionId === LIKED_ID) return JSON.stringify({type: FormQuestionType.CHOICE, selected: ['o0']})
    if (questionId === SATISFACTION_ID) return JSON.stringify({type: FormQuestionType.RATING, rating: 4})
    return ''
}

/** The groups the filter bar offers. */
export function sampleGroups(t: Translate): MemberGroup[] {
    return [
        group(1, t('helpCenter.formsAnalytics.dummyGroupYouth')),
        group(2, t('helpCenter.formsAnalytics.dummyGroupActive')),
        group(3, t('helpCenter.formsAnalytics.dummyGroupBoard')),
    ]
}

/** The tags the filter bar offers. */
export function sampleTags(t: Translate): UserTag[] {
    return [{id: 1, stationId: STATION, name: t('helpCenter.formsAnalytics.dummyTag'), color: null, visibility: TagVisibility.BADGE, position: 0}]
}

/** The choice question compared across the youth group and the active members. */
export function sampleGroupedQuestions(t: Translate): FormQuestionInfo[] {
    return [likedQuestion(t)]
}

/** The answers of the youth group and the active members, side by side. */
export function sampleGroupedResults(t: Translate): FormResultGroup[] {
    return [
        {key: '1', label: t('helpCenter.formsAnalytics.dummyGroupYouth'), responseCount: 8,
            tallies: [{questionId: LIKED_ID, answerCount: 8, optionCounts: {o0: 2, o1: 3, o2: 5}, otherCount: 0}]},
        {key: '2', label: t('helpCenter.formsAnalytics.dummyGroupActive'), responseCount: 12,
            tallies: [{questionId: LIKED_ID, answerCount: 12, optionCounts: {o0: 9, o1: 4, o2: 1}, otherCount: 0}]},
    ]
}

function likedQuestion(t: Translate): FormQuestionInfo {
    return {
        questionId: LIKED_ID,
        questionType: FormQuestionType.CHOICE,
        title: t('helpCenter.formsAnalytics.dummyQuestion'),
        config: {questionType: FormQuestionType.CHOICE, options: numberedOptions(
            t('helpCenter.formsAnalytics.dummyOptionDrills'),
            t('helpCenter.formsAnalytics.dummyOptionCommunity'),
            t('helpCenter.formsAnalytics.dummyOptionTrips'),
        )},
    }
}

function group(id: number, name: string): MemberGroup {
    return {id, stationId: STATION, name, color: null, position: id, groupSetId: null, userTypes: []}
}
