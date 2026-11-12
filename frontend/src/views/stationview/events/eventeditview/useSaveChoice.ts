/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, watch} from 'vue'
import {events} from '@/api'
import {
    EditScopeKind,
    type ChangeNotice,
    type ChangePreview,
    type EditScope,
    type EventChangePart,
    type EventRequest,
} from '@/api/generated/schema'
import {todayIsoDate} from '@/util/format'
import {describeFailure, type Failure, type Translate} from '@/util/failure'

/** What the editor chose in the save dialog. */
export interface SaveChoice {
    scope: EditScope
    notice: ChangeNotice
}

/**
 * Holds the save dialog of an appointment that already exists: which dates the change applies to,
 * whether the people it concerns are told, the note, and the counts the server previews for that
 * choice. The counts are asked again whenever the dates change, since they decide who is told.
 *
 * @param eventId       the appointment being edited
 * @param payload       the appointment as the editor would save it
 * @param changedParts  the parts the editor changed, which a notice names
 * @param t             the translator a refused preview is described with
 */
export function useSaveChoice(
    eventId: () => number | null,
    payload: () => EventRequest,
    changedParts: () => EventChangePart[],
    t: Translate,
) {
    const open = ref(false)
    const kind = ref<EditScopeKind>(EditScopeKind.ALL)
    const date = ref('')
    const tell = ref(false)
    const note = ref('')
    const preview = ref<ChangePreview | null>(null)
    const previewFailure = ref<Failure | null>(null)

    const scope = computed<EditScope>(() =>
        kind.value === EditScopeKind.ALL ? {kind: EditScopeKind.ALL} : {kind: kind.value, date: date.value})

    /** Opens the dialog on the date the editor came from, or the next one, and telling nobody. */
    function start(initialDate: string | null) {
        kind.value = EditScopeKind.ALL
        date.value = initialDate && initialDate >= todayIsoDate() ? initialDate : ''
        tell.value = false
        note.value = ''
        open.value = true
    }

    async function refresh() {
        const id = eventId()
        if (!open.value || id == null) return
        previewFailure.value = null
        if (kind.value !== EditScopeKind.ALL && !date.value) {
            preview.value = null
            return
        }
        try {
            preview.value = await events.previewChange(id, {...payload(), scope: scope.value})
        } catch (e) {
            preview.value = null
            previewFailure.value = describeFailure(e, t)
        }
    }

    watch([open, kind, date], refresh)

    /** Whether there is anything to tell about beyond a moved start. */
    const canTell = computed(() => changedParts().length > 0 || preview.value?.startMoves === true)

    const choice = computed<SaveChoice>(() => ({
        scope: scope.value,
        notice: {
            tell: tell.value && canTell.value,
            changedParts: changedParts(),
            note: note.value.trim() ? note.value.trim() : null,
        },
    }))

    return {open, kind, date, tell, note, preview, previewFailure, canTell, choice, start}
}
