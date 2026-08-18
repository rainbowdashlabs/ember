/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {QuizCatalog} from '@/api/quiz'
import type {KbItem} from '@/views/stationview/knowledge/knowledgebaseview/useKbItems'

/**
 * The wiki folder and the training catalogues a demonstration shows. Both are handed to the
 * application's own tile and selection components, so a preview cannot invent a pill or a label.
 */
export const WIKI_ITEMS: KbItem[] = [
    {
        key: 'folder-basics', icon: ['fas', 'folder'], iconClass: 'text-primary', title: 'Grundlagen',
        typeLabel: 'Ordner', countLabel: '12 Einträge', restricted: false, favourite: true, actions: [],
    },
    {
        key: 'folder-exam', icon: ['fas', 'folder'], iconClass: 'text-primary', title: 'Prüfungsfragen',
        typeLabel: 'Ordner', countLabel: '4 Einträge', restricted: true, favourite: false, actions: [],
    },
    {
        key: 'file-equipment', icon: ['fas', 'file-lines'], iconClass: 'text-(--text-muted)',
        title: 'Gerätekunde', description: 'Zusammenfassung für den Dienstabend',
        typeLabel: 'Seite', restricted: false, favourite: false, actions: [],
    },
    {
        key: 'file-slides', icon: ['fas', 'file-powerpoint'], iconClass: 'text-error',
        title: 'Gerätekunde', description: '24 Folien', typeLabel: 'Präsentation',
        levelLabel: 'Nur lesen', restricted: false, favourite: false, actions: [],
    },
]

function catalog(id: number, name: string, description: string): QuizCatalog {
    return {id, stationId: 'wache', name, description, trainingEnabled: true, createdAt: '', updatedAt: ''}
}

export const TRAINING_CATALOGS: QuizCatalog[] = [
    catalog(1, 'Grundlagen', '48 Fragen'),
    catalog(2, 'Gerätekunde', '31 Fragen'),
    catalog(3, 'Erste Hilfe', '19 Fragen'),
    catalog(4, 'Rechtsgrundlagen', '12 Fragen'),
]

export const TRAINING_SELECTION = new Set([1, 2])
