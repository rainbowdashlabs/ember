/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
const DEV = import.meta.env.DEV

function reportError(source: string, message: string, stack: string, context: string) {
    if (!DEV) return
    fetch('/api/v1/dev/errors', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ source, message, stack, context }),
    }).catch(() => { /* ignore - dev endpoint may not be available */ })
}

/**
 * Installs global error handlers for dev mode:
 * - window.onerror for uncaught JS errors
 * - window.onunhandledrejection for unhandled promise rejections
 * - Returns the Vue error handler function to pass to app.config.errorHandler
 */
export function installDevErrorHandlers() {
    if (!DEV) return undefined

    window.addEventListener('error', (event) => {
        reportError(
            'window',
            event.message ?? String(event.error),
            event.error?.stack ?? `${event.filename}:${event.lineno}:${event.colno}`,
            event.filename ?? window.location.pathname,
        )
    })

    window.addEventListener('unhandledrejection', (event) => {
        const err = event.reason
        reportError(
            'promise',
            err?.message ?? String(err),
            err?.stack ?? '',
            window.location.pathname,
        )
    })

    // Return the Vue error handler
    return (err: unknown, _instance: unknown, info: string) => {
        const error = err instanceof Error ? err : new Error(String(err))
        reportError(
            'vue',
            error.message,
            error.stack ?? '',
            info,
        )
        console.error('[Vue Error]', err, info)
    }
}

/**
 * Reports an API error (4xx/5xx) to the dev error writer.
 * Call from the axios response interceptor.
 */
export function reportApiError(method: string, url: string, status: number, message: string, stack?: string) {
    if (!DEV) return
    if (status === 401 || status === 403) return // skip auth errors
    reportError(
        'axios',
        `${status} ${method.toUpperCase()} ${url}: ${message}`,
        stack ?? new Error().stack ?? '',
        `${method.toUpperCase()} ${url}`,
    )
}

/**
 * Reports a caught error from application code (e.g. inside catch blocks).
 * Use this instead of silently swallowing errors.
 */
export function reportCaughtError(err: unknown, context?: string) {
    if (!DEV) return
    const error = err instanceof Error ? err : new Error(String(err))
    console.error('[Caught Error]', context ?? '', error)
    reportError(
        'caught',
        error.message,
        error.stack ?? '',
        context ?? window.location.pathname,
    )
}
