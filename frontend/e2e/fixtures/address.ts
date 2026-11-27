/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */

/**
 * The page one level below an address, with the address's query kept.
 *
 * <p>Station and cluster addresses end in the query naming their place (`?station=`, `?cluster=`), so a
 * segment appended to the whole address would land inside the query instead of the path.
 *
 * @param address the full address of a page, as `page.url()` returns it
 * @param segment the path segment to go to below it, such as `edit`
 */
export function below(address: string, segment: string): string {
    const url = new URL(address)
    url.pathname = `${url.pathname.replace(/\/$/, '')}/${segment}`
    return url.toString()
}

/**
 * The last segment of an address's path, which is where a detail page carries its id.
 *
 * @param address the full address of a page, as `page.url()` returns it
 */
export function lastSegment(address: string): string {
    return new URL(address).pathname.split('/').pop() as string
}
