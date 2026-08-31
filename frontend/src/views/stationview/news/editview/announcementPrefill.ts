/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {EventField, StationEvent} from '@/api/events'
import type {RestrictionSelection} from '@/components/input/restriction'
import {eventFieldText} from '@/views/stationview/events/eventshared/eventFieldText'

/** One overview field of the appointment, as it is written into the draft. */
export interface CarriedField {
    name: string
    text: string
    /** Whether the appointment shows this field on its own public page. */
    isPublic: boolean
}

/** Everything an announcement takes from the appointment it is written about. */
export interface AnnouncementDraft {
    title: string
    markdown: string
    /** The appointment's view audience, which the entry starts with. */
    audience: RestrictionSelection
    /** Whether that audience names anybody, which is what keeps the entry inside the station. */
    restricted: boolean
    fields: CarriedField[]
    /** The occurrence being announced, written out for the reader. */
    dateLabel: string
    /** Whether a link back to the appointment was written into the draft. */
    linked: boolean
}

/** The words the draft is written in, handed over rather than looked up, so this stays testable. */
export interface DraftWords {
    when: string
    until: string
    linkLabel: string
    yes: string
    no: string
}

function pad2(value: number): string {
    return String(value).padStart(2, '0')
}

function timeOf(iso?: string | null): string {
    if (!iso) return ''
    const parsed = new Date(iso)
    return `${pad2(parsed.getHours())}:${pad2(parsed.getMinutes())}`
}

/**
 * The one occurrence, written out.
 *
 * <p>A weekly appointment has no single date, so an announcement that only names the appointment
 * says nothing about which evening it is about. That is the retyping mistake this whole thing
 * exists to prevent, so the date the reader was looking at is what goes in.
 */
export function occurrenceLabel(date: string | null, event: StationEvent, words: DraftWords): string {
    if (!date) return ''
    const day = new Date(`${date}T00:00:00`).toLocaleDateString('de-DE', {
        weekday: 'long',
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
    })
    const start = timeOf(event.startTime)
    const end = timeOf(event.endTime)
    if (!start) return day
    if (!end) return `${day}, ${start}`
    return `${day}, ${start} ${words.until} ${end}`
}

/**
 * Turns an appointment into the first draft of the entry announcing it.
 *
 * <p>What comes across is what the appointment shows at a glance: its name, the one occurrence
 * being announced and the fields marked for the overview, which in practice are the meeting point
 * and the place. Values are written as a reader would read them, because a field's value is text
 * whatever its type says and a naive draft would announce a yes/no field as `true`.
 *
 * <p>The draft never follows the appointment afterwards. Moving the appointment leaves the
 * announcement wrong in the way a printed poster is wrong, which is the honest behaviour for
 * something that may already have reached a partner station.
 *
 * @param event    the appointment being announced
 * @param date     the occurrence being announced, or null where the appointment has no date at all
 * @param fields   the appointment's custom fields, of which the overview ones are carried
 * @param audience the appointment's view audience, which the entry starts restricted to
 * @param names    the station's members by id, for the fields that name people
 * @param words    the labels the draft is written with
 * @param link     the address of the appointment for a reader of this station, where there is one
 */
export function buildAnnouncementDraft(
    event: StationEvent,
    date: string | null,
    fields: EventField[],
    audience: RestrictionSelection,
    names: Map<number, string>,
    words: DraftWords,
    link: string | null,
): AnnouncementDraft {
    const restricted =
        audience.userTypes.length > 0
        || audience.groupIds.length > 0
        || audience.tagIds.length > 0
        || audience.memberIds.length > 0

    const carried: CarriedField[] = fields
        .filter(field => field.overview && field.name)
        .map(field => ({
            name: field.name ?? '',
            text: eventFieldText(field, names, {yes: words.yes, no: words.no}),
            isPublic: field.isPublic ?? false,
        }))
        .filter(field => field.text !== '')

    const dateLabel = occurrenceLabel(date, event, words)
    const lines: string[] = []
    if (dateLabel) lines.push(`**${words.when}** ${dateLabel}`)
    for (const field of carried) lines.push(`**${field.name}:** ${field.text}`)

    // A news entry is read on this station's page, on a partner's page and on the public blog, and
    // only the first of those has an address for an appointment at all. An entry that starts
    // restricted reaches nobody but this station's own members, so there the link works for every
    // reader it will ever have; anywhere else it would be written for readers who cannot follow it.
    const linked = restricted && !!link
    if (linked) lines.push(`[${words.linkLabel}](${link})`)

    return {
        title: event.name ?? '',
        markdown: lines.join('\n\n'),
        audience,
        restricted,
        fields: carried,
        dateLabel,
        linked,
    }
}
