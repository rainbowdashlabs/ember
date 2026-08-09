/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

/** Identifier segment appended to a resource path — numeric ids and UUID strings both occur. */
export type ResourceId = number | string

/** A single query-parameter value as accepted by the backend. */
export type QueryValue = string | number | boolean

/** Query parameters of a request; `null` and `undefined` entries never reach the wire. */
export type QueryParams = Record<string, QueryValue | null | undefined>

/** Offset-based pagination request, as understood by the paged list endpoints. */
export interface PageQuery {
    offset?: number
    limit?: number
}

/**
 * Strips `null` and `undefined` entries so an optional filter is simply absent from the
 * request instead of being serialized as an empty value.
 */
export function queryParams(params: QueryParams): Record<string, QueryValue> {
    const result: Record<string, QueryValue> = {}
    for (const [key, value] of Object.entries(params)) {
        if (value === null || value === undefined) continue
        result[key] = value
    }
    return result
}

/**
 * Builds the `offset`/`limit` pair, falling back to the endpoint's own defaults for whichever
 * side the caller left out. Sides that stay undefined are omitted entirely.
 */
export function pageParams(query: PageQuery = {}, defaults: PageQuery = {}): Record<string, QueryValue> {
    return queryParams({
        offset: query.offset ?? defaults.offset,
        limit: query.limit ?? defaults.limit,
    })
}

/** Runtime behaviour of a generated resource. */
export interface CrudOptions {
    /** Verb used by `update`; `put` unless the endpoint expects a partial update. */
    updateMethod?: 'put' | 'patch'
}

async function readAt<T>(path: string, params?: QueryParams): Promise<T> {
    const res = await client.get<T>(path, params ? {params: queryParams(params)} : undefined)
    return res.data
}

async function writeAt<T>(method: 'post' | 'put' | 'patch', path: string, body: unknown): Promise<T> {
    const res = await client[method]<T>(path, body)
    return res.data
}

async function deleteAt(path: string): Promise<void> {
    await client.delete(path)
}

/** The five endpoints every collection resource exposes. */
export interface CrudResource<T, CreateBody, UpdateBody, Detail, Created, Updated, Id extends ResourceId> {
    list(params?: QueryParams): Promise<T[]>
    get(id: Id): Promise<Detail>
    create(body: CreateBody): Promise<Created>
    update(id: Id, body: UpdateBody): Promise<Updated>
    remove(id: Id): Promise<void>
}

/** The same five endpoints for a collection nested under a parent resource. */
export interface ScopedCrudResource<T, CreateBody, UpdateBody, Detail, Created, Updated, Id extends ResourceId, Scope> {
    list(scope: Scope, params?: QueryParams): Promise<T[]>
    get(scope: Scope, id: Id): Promise<Detail>
    create(scope: Scope, body: CreateBody): Promise<Created>
    update(scope: Scope, id: Id, body: UpdateBody): Promise<Updated>
    remove(scope: Scope, id: Id): Promise<void>
}

/**
 * Builds the `list` / `get` / `create` / `update` / `remove` quintet for a collection served
 * under `basePath`. `T` is the row type returned by `list`; the remaining type arguments
 * cover the endpoints whose payload differs from it (detail views, create/update echoes) and
 * default to the ones that already fit.
 */
export function createCrudResource<
    T,
    CreateBody = T,
    UpdateBody = CreateBody,
    Detail = T,
    Created = T,
    Updated = Created,
    Id extends ResourceId = number,
>(
    basePath: string,
    options: CrudOptions = {},
): CrudResource<T, CreateBody, UpdateBody, Detail, Created, Updated, Id> {
    const updateMethod = options.updateMethod ?? 'put'
    return {
        list: (params?: QueryParams) => readAt<T[]>(basePath, params),
        get: (id: Id) => readAt<Detail>(`${basePath}/${id}`),
        create: (body: CreateBody) => writeAt<Created>('post', basePath, body),
        update: (id: Id, body: UpdateBody) => writeAt<Updated>(updateMethod, `${basePath}/${id}`, body),
        remove: (id: Id) => deleteAt(`${basePath}/${id}`),
    }
}

/**
 * Same as {@link createCrudResource} for a collection whose path depends on a parent id, for
 * example `/inventories/{inventoryId}/fields`. Every member takes that scope as its first
 * argument.
 */
export function createScopedCrudResource<
    T,
    CreateBody = T,
    UpdateBody = CreateBody,
    Detail = T,
    Created = T,
    Updated = Created,
    Id extends ResourceId = number,
    Scope = number,
>(
    basePath: (scope: Scope) => string,
    options: CrudOptions = {},
): ScopedCrudResource<T, CreateBody, UpdateBody, Detail, Created, Updated, Id, Scope> {
    const updateMethod = options.updateMethod ?? 'put'
    return {
        list: (scope: Scope, params?: QueryParams) => readAt<T[]>(basePath(scope), params),
        get: (scope: Scope, id: Id) => readAt<Detail>(`${basePath(scope)}/${id}`),
        create: (scope: Scope, body: CreateBody) => writeAt<Created>('post', basePath(scope), body),
        update: (scope: Scope, id: Id, body: UpdateBody) =>
            writeAt<Updated>(updateMethod, `${basePath(scope)}/${id}`, body),
        remove: (scope: Scope, id: Id) => deleteAt(`${basePath(scope)}/${id}`),
    }
}
