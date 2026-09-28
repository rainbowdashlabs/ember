/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {OptionField} from '@/util/formOptions'

/** Draws a number from zero up to one, the way `Math.random` does; tests hand in their own. */
export type Random = () => number

/** A question as shuffling needs it: the page it stands on, whether its options shuffle, and its settings. */
export interface ShuffleableQuestion {
    pageKey: string
    shuffle: boolean
    config: Record<string, unknown>
}

const OPTION_FIELDS: readonly OptionField[] = ['options', 'statements']

/** The same items in a random order, the given list left as it was. */
export function shuffled<T>(items: readonly T[], random: Random = Math.random): T[] {
    const result = [...items]
    for (let i = result.length - 1; i > 0; i--) {
        const j = Math.floor(random() * (i + 1))
        const held = result[i]!
        result[i] = result[j]!
        result[j] = held
    }
    return result
}

/** A question's settings with its options, or its statements, in a random order. */
function withShuffledOptions(config: Record<string, unknown>, random: Random): Record<string, unknown> {
    const result = {...config}
    for (const field of OPTION_FIELDS) {
        const list = config[field]
        if (Array.isArray(list)) result[field] = shuffled(list, random)
    }
    return result
}

/**
 * The questions of a form in the order one reader meets them.
 *
 * <p>A question set to shuffle gets its options, or the statements of a Likert grid, in a random
 * order. A form set to shuffle its questions shuffles them within each page, never across: a page is
 * a part of the form somebody put together on purpose, and the order of the pages is theirs.
 *
 * <p>Done once when the form is opened, so the order holds while the reader goes back and forth.
 *
 * @param questions        the questions, page by page
 * @param shuffleQuestions whether the form shuffles its questions
 * @param random           where the randomness comes from
 */
export function presentQuestions<Q extends ShuffleableQuestion>(
    questions: readonly Q[],
    shuffleQuestions: boolean,
    random: Random = Math.random,
): Q[] {
    const prepared = questions.map(question => question.shuffle
        ? {...question, config: withShuffledOptions(question.config, random)}
        : question)
    if (!shuffleQuestions) return prepared
    const pageOrder = [...new Set(prepared.map(question => question.pageKey))]
    return pageOrder.flatMap(pageKey => shuffled(prepared.filter(question => question.pageKey === pageKey), random))
}
