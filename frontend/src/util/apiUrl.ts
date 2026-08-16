/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * The address of an API endpoint for whichever side is asking.
 *
 * In the browser this is the relative path the axios client already uses, which the dev server and
 * the container both proxy. During a server render there is no origin to resolve a relative path
 * against, so the call has to name the backend outright — which is what makes a server-rendered
 * page able to carry its content instead of an empty shell.
 *
 * @param path the endpoint below `/api/v1`, starting with a slash
 */
export function apiUrl(path: string): string {
    if (import.meta.server) {
        return `${useRuntimeConfig().backendUrl}/api/v1${path}`
    }
    return `/api/v1${path}`
}
