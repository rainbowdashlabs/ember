/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {eventGroups, events} from '@/api'
import type {
    EventAttachment,
    EventFieldDefault,
    FederationShareResponse,
    FieldDefaultEntry,
    PartnerPlacesView,
    ShareScope,
} from '@/api/generated/schema'

/**
 * The files of one appointment or one appointment template, as the attachments card reads and
 * writes them.
 *
 * <p>The owner is fixed when the port is made, so nothing that uses it ever reads an owner off a
 * row: the rows of a template carry the template's id in a field named for an appointment.
 */
export interface AttachmentPort {
    list(): Promise<EventAttachment[]>
    attach(fileId: number, label: string | null, internal: boolean): Promise<EventAttachment>
    update(attachmentId: number, label: string | null, internal: boolean): Promise<void>
    reorder(attachmentIds: number[]): Promise<void>
    detach(attachmentId: number): Promise<void>
}

/** The starting values one appointment or one appointment template gives its attendance fields. */
export interface FieldDefaultsPort {
    get(): Promise<EventFieldDefault[]>
    set(entries: FieldDefaultEntry[]): Promise<EventFieldDefault[]>
}

/** What one partner station may do with a shared appointment; templates have no such arrangement. */
export interface PartnerPlacesPort {
    list(): Promise<PartnerPlacesView[]>
    set(partnerId: number, slotBudget: number | null, partnerConfirms: boolean): Promise<void>
}

/** Whether and with which partner stations one appointment or one appointment template is shared. */
export interface FederationSharePort {
    get(): Promise<FederationShareResponse>
    set(scope: ShareScope, partnerIds?: number[]): Promise<void>
    remove(): Promise<void>
    /** Present for an appointment only, which is the one owner partners can be handed places of. */
    places?: PartnerPlacesPort
}

export function eventAttachmentPort(eventId: number): AttachmentPort {
    return {
        list: () => events.listEventAttachments(eventId),
        attach: (fileId, label, internal) => events.attachEventFile(eventId, fileId, label, internal),
        update: (attachmentId, label, internal) => events.updateEventAttachment(eventId, attachmentId, label, internal),
        reorder: attachmentIds => events.reorderEventAttachments(eventId, attachmentIds),
        detach: attachmentId => events.detachEventFile(eventId, attachmentId),
    }
}

export function templateAttachmentPort(templateId: number): AttachmentPort {
    return {
        list: () => events.listTemplateAttachments(templateId),
        attach: (fileId, label, internal) => events.attachTemplateFile(templateId, fileId, label, internal),
        update: (attachmentId, label, internal) =>
            events.updateTemplateAttachment(templateId, attachmentId, label, internal),
        reorder: attachmentIds => events.reorderTemplateAttachments(templateId, attachmentIds),
        detach: attachmentId => events.detachTemplateFile(templateId, attachmentId),
    }
}

export function eventFieldDefaultsPort(eventId: number): FieldDefaultsPort {
    return {
        get: () => events.getFieldDefaults(eventId),
        set: entries => events.setFieldDefaults(eventId, entries),
    }
}

export function templateFieldDefaultsPort(templateId: number): FieldDefaultsPort {
    return {
        get: () => events.getTemplateFieldDefaults(templateId),
        set: entries => events.setTemplateFieldDefaults(templateId, entries),
    }
}

export function eventFederationPort(eventId: number): FederationSharePort {
    return {
        get: () => events.getFederationShare(eventId),
        set: (scope, partnerIds) => events.setFederationShare(eventId, scope, partnerIds),
        remove: () => events.removeFederationShare(eventId),
        places: {
            list: () => events.getPartnerPlaces(eventId),
            set: (partnerId, slotBudget, partnerConfirms) =>
                events.setPartnerPlaces(eventId, partnerId, slotBudget, partnerConfirms),
        },
    }
}

/** How an event group, and with it every part, is shared with partner stations. */
export function eventGroupFederationPort(eventGroupId: number): FederationSharePort {
    return {
        get: () => eventGroups.getFederationShare(eventGroupId),
        set: (scope, partnerIds) => eventGroups.setFederationShare(eventGroupId, scope, partnerIds),
        remove: () => eventGroups.removeFederationShare(eventGroupId),
    }
}

export function templateFederationPort(templateId: number): FederationSharePort {
    return {
        get: () => events.getTemplateFederationShare(templateId),
        set: (scope, partnerIds) => events.setTemplateFederationShare(templateId, scope, partnerIds),
        remove: () => events.removeTemplateFederationShare(templateId),
    }
}
