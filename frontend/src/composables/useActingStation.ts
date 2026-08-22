/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {onUnmounted, watch, type Ref} from 'vue'
import {getActingStation, setActingStation} from '@/util/actingStationState'

/**
 * Answers every request from this screen for one particular station, for as long as it is open.
 *
 * <p>An association's knowledge base, news and calendar live on the station it owns, and the screens that
 * edit them are the station's own screens, reused whole. They ask for "the station", and while one of them
 * is open on the association's side the answer is the association's station, not whichever one the reader
 * belongs to.
 *
 * <p>Call it from the page, not the view: a page's setup runs before the view it renders is mounted, which
 * is what puts the station in place before the first request goes out.
 *
 * @param uid the station to act for, or {@code null} while it is not known yet
 */
export function useActingStation(uid: Ref<string | null | undefined>) {
    const previous = getActingStation()

    watch(uid, next => setActingStation(next ?? null), {immediate: true})

    onUnmounted(() => setActingStation(previous))
}
