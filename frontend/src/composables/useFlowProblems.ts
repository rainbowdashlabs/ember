/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {useI18n} from 'vue-i18n'
import type {FlowProblem} from '@/api/movements'
import {apiErrorBody, apiErrorMessage} from '@/util/apiError'

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
        return apiErrorMessage(e) ?? t('common.error')
    }

    return {problemText, refusalText}
}
