/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, ref} from 'vue'

/**
 * What a report is about, where it was opened from something that failed rather than from the button.
 *
 * @param summary   what the reader saw go wrong, in their terms, so the report says it even where they
 *                  describe it differently
 * @param technical the server's own answer, which the reader cannot write and an operator needs
 */
export interface ReportAbout {
    summary: string
    technical?: string
}

const open = ref(false)
const about = ref<ReportAbout | null>(null)

/**
 * Asks for the problem report form, optionally about something that has just failed.
 *
 * <p>Module state rather than a composable because the callers are not all components: the request
 * interceptor offers a report on a server fault, and `src/api` may not reach into `src/composables`.
 * The toast helpers live here for the same reason.
 *
 * @param context what the report is about, or nothing where the reader opened the form themselves
 */
export function openProblemReport(context?: ReportAbout) {
    about.value = context ?? null
    open.value = true
}

/** Closes the form, forgetting whatever it was about. */
export function closeProblemReport() {
    open.value = false
    about.value = null
}

/** Whether the form is asked for, which the one modal in the layout renders against. */
export function problemReportOpen() {
    return readonly(open)
}

/** What the form is about, where it was opened from a failure. */
export function problemReportAbout() {
    return readonly(about)
}
