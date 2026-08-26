/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {inject, provide, type InjectionKey} from 'vue'

/**
 * The two things an association's list says about a person that a station's never has to.
 *
 * <p>A station's list is one station's, so naming the station on every row would say nothing. An
 * association's reaches across all of them, and it also has people it may not touch: a station's
 * owner, and the reader's own membership. Both are refused by the server, and a row that says why
 * beats a button that fails.
 *
 * <p>Injected rather than handed through the four components between the screen and a row, none of
 * which has any use for them.
 */
export interface MemberRowExtras {
    /** A short line under the name, or empty for none. */
    note(memberId: number): string
    /** Why this row offers no actions, or empty when it offers them normally. */
    blockedReason(memberId: number): string
    /**
     * Whether groups and tags are columns at all.
     *
     * <p>They are a station's own, so across the stations of an association most rows would have
     * nothing under either and the two columns would be holes rather than information.
     */
    stationLocalColumns: boolean
}

const NONE: MemberRowExtras = {note: () => '', blockedReason: () => '', stationLocalColumns: true}

const MEMBER_ROW_EXTRAS: InjectionKey<MemberRowExtras> = Symbol('memberRowExtras')

export function provideMemberRowExtras(extras: MemberRowExtras): void {
    provide(MEMBER_ROW_EXTRAS, extras)
}

/** What a row should add, which for a station's own list is nothing. */
export function useMemberRowExtras(): MemberRowExtras {
    return inject(MEMBER_ROW_EXTRAS, NONE)
}
