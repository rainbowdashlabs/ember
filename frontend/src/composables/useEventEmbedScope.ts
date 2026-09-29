/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {inject, provide, type InjectionKey} from 'vue'
import type {EventPickerScope} from '@/api/events'

const EVENT_EMBED_SCOPE: InjectionKey<EventPickerScope> = Symbol('eventEmbedScope')

/**
 * Says which events the block editor below may name.
 *
 * <p>The block editor is shared by station pages and news entries. A page is read by anybody, so
 * its event blocks offer public events only, and that is what every editor gets unless told
 * otherwise. A news entry is written inside the station and provides `VISIBLE`, so its author may
 * also name an event the station keeps to itself.
 */
export function provideEventEmbedScope(scope: EventPickerScope): void {
    provide(EVENT_EMBED_SCOPE, scope)
}

/** The events the enclosing editor may name, `PUBLIC` where nothing said otherwise. */
export function useEventEmbedScope(): EventPickerScope {
    return inject(EVENT_EMBED_SCOPE, 'PUBLIC')
}
