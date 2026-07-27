/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

/**
 * Posts a multipart form to an authenticated endpoint. Builds the FormData from the given
 * fields, skipping undefined and null values, and lets the browser set the multipart
 * boundary header itself — never set Content-Type manually for FormData requests.
 */
export async function uploadFile<T = unknown>(
    url: string,
    fields: Record<string, string | Blob | File | null | undefined>,
    method: 'post' | 'put' = 'post',
): Promise<T> {
    const formData = new FormData()
    for (const [key, value] of Object.entries(fields)) {
        if (value === null || value === undefined) continue
        formData.append(key, value)
    }
    const res = await client[method]<T>(url, formData)
    return res.data
}
