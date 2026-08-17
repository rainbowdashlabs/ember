/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {ProcedureItem, ProcedureTemplateItem} from '@/api/procedures'
import type {MemberIdentity} from '@/api/types'
import type {PitchProcedure, PitchProcedureTemplate} from './pitchTypes'

/**
 * The procedure a demonstration walks through: taking on a new member. The steps are handed to
 * the application's own rows, so the lock, the strike-through and the notes are the real ones.
 */
function checkedAt(days: number): string {
    const date = new Date()
    date.setDate(date.getDate() - days)
    return date.toISOString()
}

function step(id: number, title: string, description: string,
              rest: Partial<ProcedureItem> = {}): ProcedureItem {
    return {
        id, procedureId: 1, title, description, note: null, isPublic: true, userAssigned: false,
        position: id, checked: false, checkedAt: null, checkedBy: null, ...rest,
    }
}

const ASSIGNEES: MemberIdentity[] = [
    {stationUid: 'wache', memberUid: 'm-clara', name: 'Clara Weiß'},
    {stationUid: 'wache', memberUid: 'm-jonas', name: 'Jonas Behr'},
]

export const PROCEDURE_INTAKE: PitchProcedure = {
    assignees: ASSIGNEES,
    items: [
        step(1, 'Aufnahmeantrag unterschrieben', 'Von den Erziehungsberechtigten unterschrieben abgeben',
            {checked: true, checkedAt: checkedAt(9), userAssigned: true}),
        step(2, 'Einverständnis zu Fotos geklärt', 'Ja oder Nein festhalten — beides ist in Ordnung',
            {checked: true, checkedAt: checkedAt(9), userAssigned: true}),
        step(3, 'Zugang angelegt', 'Konto anlegen und Einladung verschicken',
            {checked: true, checkedAt: checkedAt(7), isPublic: false}),
        step(4, 'Kleidung ausgegeben', 'Größen messen und Ausstattung zuweisen',
            {note: 'Jacke Größe 152 fehlt noch, Nachbestellung läuft'}),
        step(5, 'In der Gruppe vorgestellt', 'Beim nächsten Dienstabend', {isPublic: false}),
    ],
    blockedBy: {5: ['Kleidung ausgegeben']},
}

function templateStep(id: number, title: string, description: string,
                      rest: Partial<ProcedureTemplateItem> = {}): ProcedureTemplateItem {
    return {id, templateId: 1, title, description, isPublic: true, userAssigned: false, position: id, ...rest}
}

const TEMPLATE_ITEMS: ProcedureTemplateItem[] = [
    templateStep(1, 'Aufnahmeantrag unterschrieben', 'Von den Erziehungsberechtigten unterschrieben abgeben',
        {userAssigned: true}),
    templateStep(2, 'Einverständnis zu Fotos geklärt', 'Ja oder Nein festhalten — beides ist in Ordnung',
        {userAssigned: true}),
    templateStep(3, 'Zugang angelegt', 'Konto anlegen und Einladung verschicken', {isPublic: false}),
    templateStep(4, 'Kleidung ausgegeben', 'Größen messen und Ausstattung zuweisen'),
    templateStep(5, 'In der Gruppe vorgestellt', 'Beim nächsten Dienstabend', {isPublic: false}),
]

export const PROCEDURE_TEMPLATE: PitchProcedureTemplate = {
    items: TEMPLATE_ITEMS,
    dependencies: {3: [1], 4: [1], 5: [4]},
    getItemById: (id: number) => TEMPLATE_ITEMS.find(entry => entry.id === id),
}
