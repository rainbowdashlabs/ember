/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {DiscoveredBoard} from '@/api/generated/schema'

function partnerBoard(fields: Pick<DiscoveredBoard, 'partnerStationName' | 'partnerStationUid' | 'remoteBoardUid' | 'name' | 'shortKey' | 'description' | 'shareMode'>): DiscoveredBoard {
    return {partnerId: 1, requiredUserType: 'MEMBER', ...fields}
}

/** Partner boards grouped by the partner station that shares them, as the overview groups them. */
export const demoPartnerStations: {stationName: string, boards: DiscoveredBoard[]}[] = [
    {
        stationName: 'Feuerwehr Musterstadt',
        boards: [
            partnerBoard({
                partnerStationName: 'Feuerwehr Musterstadt',
                partnerStationUid: 'demo-musterstadt',
                remoteBoardUid: 'demo-board-1',
                name: 'Gemeinsame Übungen',
                shortKey: 'UEB',
                description: 'Übungen, die beide Wachen zusammen planen',
                shareMode: 'READ_ONLY',
            }),
            partnerBoard({
                partnerStationName: 'Feuerwehr Musterstadt',
                partnerStationUid: 'demo-musterstadt',
                remoteBoardUid: 'demo-board-2',
                name: 'Fahrzeugtausch',
                shortKey: 'FZG',
                description: 'Wer leiht wem wann welches Fahrzeug',
                shareMode: 'FULL',
            }),
        ],
    },
    {
        stationName: 'Jugendfeuerwehr Beispielhausen',
        boards: [
            partnerBoard({
                partnerStationName: 'Jugendfeuerwehr Beispielhausen',
                partnerStationUid: 'demo-beispielhausen',
                remoteBoardUid: 'demo-board-3',
                name: 'Zeltlager',
                shortKey: 'ZELT',
                description: 'Planung für das gemeinsame Zeltlager im Sommer',
                shareMode: 'READ_ONLY',
            }),
        ],
    },
]

/** Whether the demo reader has bookmarked a partner board; only the vehicle board is. */
export function demoIsBookmarked(_partnerStationUid: string, remoteBoardUid: string): boolean {
    return remoteBoardUid === 'demo-board-2'
}
