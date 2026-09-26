/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {useI18n} from 'vue-i18n'
import type {FlowProblem} from '@/api/movements'
import {apiErrorBody} from '@/util/apiError'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * Puts what is wrong with a chain into words.
 *
 * <p>The backend names the rule that is broken and leaves the sentence to whoever shows it, which is
 * the only way the same fault reads in the reader's language. A chain carrying a fault and a change
 * refused for one say the same thing, so they are worded in the same place.
 */
export function useFlowProblems() {
    const {t, te} = useI18n()

    function textOf(code: string, detail?: string | null): string {
        const key = `flows.problem.${code}`
        return te(key) ? t(key, {detail: detail ?? ''}) : code
    }

    /** What stops this chain from being walked, in words. */
    function problemText(problem?: FlowProblem | null): string {
        return problem ? textOf(problem.code, problem.detail) : ''
    }

    /**
     * Why a change to a chain was refused.
     *
     * <p>A refusal names the same rules as the chain itself. Anything else the backend rejected is
     * shown as it came, and a failure that carries nothing falls back to the general wording.
     */
    function refusalText(e: unknown): string {
        const body = apiErrorBody(e)
        if (body?.error === 'FlowRefusedException' && body.message) return textOf(body.message)
        return refusalFailure(e).message
    }

    /**
     * The same refusal, described, for the places with room to say what to do about it.
     *
     * <p>Where the backend named a rule, that sentence wins and the failure supplies everything around
     * it: what sort of failure this was and what to do next. It is marked as nothing to report, because
     * a chain that may not be edited while a movement is walking it is the product working as intended,
     * and a bug report filed against that buries the real ones.
     *
     * <p>Anything else is described as it stands, report button and all, because then it really is one.
     *
     * @param e the thing that was thrown
     * @return the refusal, described
     */
    function refusalFailure(e: unknown): Failure {
        const described = describeFailure(e, t)
        const body = apiErrorBody(e)
        if (body?.error === 'FlowRefusedException' && body.message) {
            return {...described, message: textOf(body.message), reportable: false}
        }
        return described
    }

    return {problemText, refusalText, refusalFailure}
}
