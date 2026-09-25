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
    closedAt?: string | null
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

/**
 * When the form stopped taking answers, or nothing while it still does.
 *
 * <p>A form can stop for either of two reasons and sometimes both: its closing date passed, or
 * somebody closed it. Whichever came first is when it actually stopped, and that is the date worth
 * showing to somebody wondering why it will not take their answer.
 */
export function closedSinceOf(form: Dated): string | null {
    if (formStateOf(form) !== PublicFormState.CLOSED) return null
    const byDate = form.endAt && Date.now() > Date.parse(form.endAt) ? form.endAt : null
    if (!form.closedAt) return byDate
    if (!byDate) return form.closedAt
    return Date.parse(form.closedAt) < Date.parse(byDate) ? form.closedAt : byDate
}
