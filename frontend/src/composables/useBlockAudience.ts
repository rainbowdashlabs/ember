/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {inject, provide, type InjectionKey} from 'vue'
import type {BlockAudience} from '@/api/pageManage'

const BLOCK_AUDIENCE: InjectionKey<BlockAudience> = Symbol('blockAudience')

/**
 * Says who reads the blocks below, for writing and for reading them alike.
 *
 * <p>The block editor and the block renderer are shared by station pages, news entries and wiki
 * articles. A page is read by anybody, so its news and event blocks name and show public entries and
 * appointments only, and that is what every surface gets unless told otherwise. A news entry or a
 * wiki article is read by the station's members and provides `MEMBERS`, so its blocks name and show
 * everything every member may see, internal ones included.
 */
export function provideBlockAudience(audience: BlockAudience): void {
    provide(BLOCK_AUDIENCE, audience)
}

/** Who reads the enclosing content, `PUBLIC` where nothing said otherwise. */
export function useBlockAudience(): BlockAudience {
    return inject(BLOCK_AUDIENCE, 'PUBLIC')
}
