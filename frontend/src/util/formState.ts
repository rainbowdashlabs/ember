/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {PublicFormState, type PublicFormStateName} from '@/api/publicForms'
import {FormStatus} from '@/api/forms'

/** What a form needs to say whether it is taking answers: the state somebody set, and the dates. */
interface Dated {
    status: string
    startAt?: string | null
    endAt?: string | null
}

/**
 * Whether a form is taking answers, in the same four states the form's own page reports.
 *
 * <p>Its status alone does not say: a form stays open long after the end date its author gave it,
 * and one that opens next week is open already as far as the column is concerned. Reading the
 * status by itself left a poll badged as taking answers on the station's own list while its page
 * told every visitor it was closed.
 */
export function formStateOf(form: Dated): PublicFormStateName {
    if (form.status === FormStatus.DRAFT) return PublicFormState.NOT_PUBLISHED
    if (form.status === FormStatus.CLOSED) return PublicFormState.CLOSED
    const now = Date.now()
    if (form.startAt && now < Date.parse(form.startAt)) return PublicFormState.NOT_OPEN_YET
    if (form.endAt && now > Date.parse(form.endAt)) return PublicFormState.CLOSED
    return PublicFormState.OPEN
}
