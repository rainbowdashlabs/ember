/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {describeFailure, FailureKind, type Failure} from '@/util/failure'

/**
 * Why a public page could not be fetched, in terms a reader with no account can act on.
 *
 * <p>These pages are read by people who have no station to ask, no administrator to tell and no idea
 * what Ember is. One sentence about something having gone wrong is therefore a dead end there: it
 * leaves the reader unable to tell a broken application from something that was simply taken down, and
 * the two call for opposite things. The distinction drawn here is exactly that one.
 *
 * <p>Where the server says the thing is not there, the page says so as a plain fact and offers no bug
 * report, because there is nothing to report and nobody the reader could report it to. Everything else
 * keeps the description it arrived with, so a dropped connection reads as a dropped connection and a
 * server that fell over still offers the way to say so.
 *
 * @param loadError the rejection a fetch left behind, or nothing where it succeeded
 * @param notFound  keys for the sentence and the guidance used when the server says it is gone
 * @return the failure to render, or nothing while there is none
 */
export function usePublicFailure(
    loadError: Ref<unknown>,
    notFound: {message: string; guidance: string},
): Ref<Failure | null> {
    const {t} = useI18n()
    return computed(() => {
        if (!loadError.value) return null
        const described = describeFailure(loadError.value, t)
        if (described.kind !== FailureKind.GONE) return described
        return {
            ...described,
            message: t(notFound.message),
            guidance: t(notFound.guidance),
            reportable: false,
        }
    })
}
