/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {EventField, StationEvent} from '@/api/events'
import {CellContentType, type CellContentTypeName} from '@/api/pageManage'
import type {RowEditData} from '@/components/content/blockeditor/EditorRow.vue'
import type {RestrictionSelection} from '@/components/input/restriction'
import {eventFieldText} from '@/views/stationview/events/eventshared/eventFieldText'
import {occurrenceLabel} from '@/util/occurrenceLabel'
import {announcementSentences, type Say} from './announcementSentences'

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
    /** The entry's first blocks: the event itself, its description, and what the event block does not show. */
    rows: RowEditData[]
    /** The appointment's view audience, which the entry starts with. */
    audience: RestrictionSelection
    /** Whether that audience names anybody, which is what keeps the entry inside the station. */
    restricted: boolean
    fields: CarriedField[]
    /** The occurrence being announced, written out for the reader. */
    dateLabel: string
    /** Whether the draft opens with a block showing the event itself. */
    embedded: boolean
}

/** The words the draft is written in, handed over rather than looked up, so this stays testable. */
export interface DraftWords {
    until: string
    yes: string
    no: string
    say: Say
}

/** Where the draft finds the appointment. */
export interface AnnouncedEvent {
    event: StationEvent
    /** The public id an event block names it by, or null where none could be had. */
    eventUid: string | null
    /** The occurrence being announced, or null where the appointment has no date at all. */
    date: string | null
    /** The appointment's custom fields, of which the overview ones are carried. */
    fields: EventField[]
    /** The station's clock, which the moments in the text are written on. */
    timezone?: string | null
}

/**
 * Turns an appointment into the first draft of the entry announcing it, as blocks.
 *
 * <p>The draft opens with an event block, which shows the name, the one occurrence and a link, and
 * keeps showing them as the appointment changes. Below it come the appointment's own description,
 * which the author will most likely want to reword, and then what the event block does not show:
 * how signing up works and the fields marked for the overview. Values are written as a reader would
 * read them, because a field's value is text whatever its type says and a naive draft would announce
 * a yes/no field as `true`.
 *
 * <p>The text blocks are a copy and do not follow the appointment afterwards, which is the honest
 * behaviour for something that may already have reached a partner station.
 *
 * @param announced the appointment and the occurrence being announced
 * @param audience  the appointment's view audience, which the entry starts restricted to
 * @param names     the station's members by id, for the fields that name people
 * @param words     the labels and sentences the draft is written with
 */
export function buildAnnouncementDraft(
    announced: AnnouncedEvent,
    audience: RestrictionSelection,
    names: Map<number, string>,
    words: DraftWords,
): AnnouncementDraft {
    const {event, eventUid, date, timezone} = announced
    const restricted =
        audience.userTypes.length > 0
        || audience.groupIds.length > 0
        || audience.tagIds.length > 0
        || audience.memberIds.length > 0

    const carried: CarriedField[] = announced.fields
        .filter(field => field.overview && field.name)
        .map(field => ({
            name: field.name ?? '',
            text: eventFieldText(field, names, {yes: words.yes, no: words.no}),
            isPublic: field.isPublic ?? false,
        }))
        .filter(field => field.text !== '')

    const details = [
        announcementSentences(event, date, words.say, timezone).join(' '),
        ...carried.map(field => `**${field.name}:** ${field.text}`),
    ].filter(Boolean).join('\n\n')

    const blocks = [
        eventUid ? block(CellContentType.FEATURED_EVENT, '', {eventUid, date: repeats(event) ? date : null}) : null,
        event.description?.trim() ? block(CellContentType.MARKDOWN, event.description.trim()) : null,
        details ? block(CellContentType.MARKDOWN, details) : null,
    ].filter(cell => cell !== null)

    return {
        title: event.name ?? '',
        rows: blocks.map((cell, index) => ({id: 0, sortOrder: index, cells: [cell]})),
        audience,
        restricted,
        fields: carried,
        dateLabel: date ? occurrenceLabel(date, event.startTime, event.endTime, words.until) : '',
        embedded: !!eventUid,
    }
}

/**
 * Whether the appointment happens on more than one day of its own. Only then does the event block
 * need to be told which one; a one-off appointment, even one running over several days, is shown
 * with its own start.
 */
function repeats(event: StationEvent): boolean {
    return !!event.eventType && event.eventType !== 'ONE_TIME'
}

function block(contentType: CellContentTypeName, content: string, config: Record<string, unknown> = {}) {
    return {id: 0, sortOrder: 0, widthPercent: 100, contentType, content, config}
}
