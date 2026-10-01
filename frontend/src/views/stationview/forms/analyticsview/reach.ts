/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {FormQuestionTally, FormResultGroup} from '@/api/generated/schema'
import type {Translate} from '@/util/failure'

/**
 * How many answered a question, and where not everybody was shown it, how many were.
 *
 * <p>"Not shown" is not "not answered". A question behind a branch that only some readers reach looks
 * like one most people skipped unless the results say how many reached it at all.
 *
 * @param tally the counted answers to the question
 * @param total how many responses are counted
 * @param t     the translator
 */
export function answeredLine(tally: FormQuestionTally | undefined, total: number, t: Translate): string {
    const answered = tally?.answerCount ?? 0
    const reached = tally?.reachedCount
    if (reached === undefined || reached === null || reached >= total) return t('forms.analytics.answered', {count: answered})
    return t('forms.analytics.answeredOfReached', {answered, reached, total})
}

/**
 * For grouped results, how many of each group were shown the question, where not all of them were.
 *
 * @param questionId the question
 * @param groups     the groups of respondents
 * @param names      what each group is called, in the same order
 * @param t          the translator
 * @return one line per group that did not all see the question
 */
export function reachLines(questionId: number, groups: FormResultGroup[], names: string[], t: Translate): string[] {
    return groups.flatMap((group, index) => {
        const reached = group.tallies.find(tally => tally.questionId === questionId)?.reachedCount
        if (reached === undefined || reached === null || reached >= group.responseCount) return []
        return [t('forms.analytics.groupReached', {group: names[index] ?? group.label, reached, total: group.responseCount})]
    })
}
