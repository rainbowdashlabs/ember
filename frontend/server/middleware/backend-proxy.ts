/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {defineEventHandler, proxyRequest} from 'h3'
import {useRuntimeConfig} from '#imports'

/**
 * Hands the paths the backend owns straight to it, at the address this server holds while it runs.
 *
 * The route rules this replaces resolved their target while the application was being built, so an
 * image built without the backend address set proxied to the build machine's default for the rest
 * of its life, whatever its runtime environment said. Reading the address per request is what makes
 * one image usable in more than one place.
 *
 * Everything else falls through untouched: returning nothing from a middleware lets the request
 * carry on to the pages.
 */
function belongsToBackend(path: string): boolean {
    return path.startsWith('/api/')
        || path === '/sitemap.xml'
        || path.startsWith('/sitemap-station-')
}

export default defineEventHandler(event => {
    if (!belongsToBackend(event.path)) return

    const {backendUrl} = useRuntimeConfig(event)
    return proxyRequest(event, `${backendUrl}${event.path}`)
})
