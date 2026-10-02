/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Ref} from 'vue'
import type {FormAnswerValue} from '@/api/generated/schema'

/**
 * The answers and the page walk as a form stood once it was opened, which is what a draft is measured
 * against.
 *
 * <p>A form opens with answers already in it: every question starts with an empty answer of its own
 * shape, a ranking starts full, an answer sent earlier or a draft kept earlier fills it in. None of
 * that is the reader's doing, so none of it is worth keeping. Only a difference from how the form
 * opened is.
 *
 * @param answers the answers, by question id
 * @param path    the pages visited, the page shown last
 */
export function useAnswerBaseline(answers: Ref<Record<number, FormAnswerValue>>, path: Readonly<Ref<string[]>>) {
    let settled = snapshot()

    function snapshot(): string {
        return JSON.stringify([answers.value, path.value])
    }

    /** Takes the form as it stands now as the way it opened. */
    function settle() {
        settled = snapshot()
    }

    /** Whether the reader has changed an answer or walked elsewhere since the form opened. */
    function changed(): boolean {
        return snapshot() !== settled
    }

    return {settle, changed}
}
