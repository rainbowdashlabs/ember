/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import type {SessionInfo} from '@/api/types'

/**
 * Shared reactive state of the signed-in session. {@code useSession} is the only writer;
 * {@code usePermissions} and the session-driven theme sync are readers. Keeping the state
 * in its own module lets those readers stay free of an import cycle back into the session
 * composable.
 */
export const sessionInfo = ref<SessionInfo | null>(null)
export const sessionLoaded = ref(false)
export const sessionLoadFailed = ref(false)
export const sessionStationId = ref<string | null>(null)
/**
 * The cluster the loaded session was answered for. A session fetched without one carries no cluster
 * role and no cluster permissions, so a shell that opens on a cluster has to know whether what it
 * holds was asked for that cluster or for none.
 */
export const sessionClusterId = ref<string | null>(null)
