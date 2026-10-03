/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * What a discovery page offers whoever looks at its tiles. Whether a station may be asked to federate,
 * whether its code may be had and whether it is the reader's own come with each entry from the server;
 * the page only says whether it hands out invite codes at all.
 */
export interface DiscoveryViewer {
    offersInvite: boolean
}

/**
 * The viewer of a discovery page.
 *
 * @param offersInvite whether the page hands out invite codes
 */
export function useDiscoveryViewer(offersInvite: boolean): DiscoveryViewer {
    return {offersInvite}
}
