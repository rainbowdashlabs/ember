/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, reactive, ref, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {appointmentDocuments, events} from '@/api'
import {EventType, QueueOrder, type BatchCreateRequest, type RegistrationDeadline, type TemplateDetailResponse} from '@/api/generated/schema'
import {noDeadline} from '../eventshared/registrationDeadline'
import {signUpRulesOf} from '../templateeditview/signUpRules'
import {useEventFederationShare} from '../eventeditview/useEventFederationShare'
import {eventFederationPort, templateFederationPort} from '../eventeditview/eventOwnerPorts'

/** What an applied template hands to every appointment of a batch beyond the fields the form shows. */
interface AppliedTemplate {
    id: number
    sheetId: number | null
    reminderDays: number[]
    questions: number
    documents: number
    files: number
    fieldDefaults: number
    repeating: boolean
}

/**
 * The settings a batch takes from an appointment template, beyond the name, description, category,
 * attendance sheet, audiences, registration switches and fields the form already shows.
 *
 * <p>Places, the waiting list order, the minimum and the deadline go into the form, where they can
 * still be changed. The reminders, the questions, the documents to bring, the files and the field
 * starting values go with the batch request, and the server hands them to every appointment it
 * creates. Sharing with partner stations is written to each appointment once the batch exists, for
 * somebody who may share appointments.
 *
 * <p>What cannot reach the batch is named: the repetition of a template for repeating appointments,
 * since a batch makes one-time appointments; the field starting values where the batch is taken on
 * another attendance sheet than the template's; and the sharing for somebody who may not share, or
 * the partners the station no longer works with.
 *
 * @param canFederate whether the reader may share appointments with partner stations
 */
export function useBatchTemplate(canFederate: Ref<boolean>) {
    const {t} = useI18n()
    const federationShare = useEventFederationShare(canFederate)

    const settings = reactive({
        registrationLimit: undefined as number | undefined,
        queueOrder: QueueOrder.TIME as QueueOrder,
        minRegistrations: undefined as number | undefined,
        thresholdDays: undefined as number | undefined,
        registrationDeadline: noDeadline() as RegistrationDeadline,
    })
    const applied = ref<AppliedTemplate | null>(null)
    const shareNote = ref('')

    /**
     * Takes over what the template sets for every appointment, replacing what an earlier template set.
     *
     * @param detail     the template as the server reads it
     * @param templateId the template
     */
    async function apply(detail: TemplateDetailResponse, templateId: number) {
        const tpl = detail.template
        Object.assign(settings, signUpRulesOf(tpl))
        const [files, documents, defaults, share] = await Promise.all([
            events.listTemplateAttachments(templateId),
            appointmentDocuments.listRequirements({kind: 'event-templates', id: templateId}),
            events.getTemplateFieldDefaults(templateId),
            federationShare.takeFrom(templateFederationPort(templateId)),
        ])
        if (share.skipped) shareNote.value = t('events.federationTemplateSkipped')
        else if (share.droppedPartners > 0) shareNote.value = t('events.federationTemplatePartnersDropped', {count: share.droppedPartners})
        else shareNote.value = ''
        applied.value = {
            id: templateId,
            sheetId: tpl.attendanceTemplateId ?? null,
            reminderDays: detail.reminderDays ?? [],
            questions: detail.registrationFields.length,
            documents: documents.length,
            files: files.length,
            fieldDefaults: defaults.length,
            repeating: tpl.eventType != null && tpl.eventType !== EventType.ONE_TIME,
        }
    }

    /** What the batch hands to every appointment from the template, as the reader is told it. */
    const carried = computed<string[]>(() => {
        const template = applied.value
        if (!template) return []
        const named: string[] = []
        if (template.reminderDays.length > 0) named.push(t('batchCreate.carriedReminders', {days: [...template.reminderDays].sort((a, b) => a - b).join(', ')}))
        if (template.questions > 0) named.push(t('batchCreate.carriedQuestions', {count: template.questions}))
        if (template.documents > 0) named.push(t('batchCreate.carriedDocuments', {count: template.documents}))
        if (template.files > 0) named.push(t('batchCreate.carriedFiles', {count: template.files}))
        if (template.fieldDefaults > 0) named.push(t('batchCreate.carriedFieldDefaults', {count: template.fieldDefaults}))
        if (federationShare.state.federationShared) named.push(t('batchCreate.carriedSharing'))
        return named
    })

    /**
     * What the template sets that the batch leaves out, as the reader is told it.
     *
     * @param sheetId the attendance sheet the batch is taken on now, empty for none
     */
    function leftOut(sheetId: string): string[] {
        const template = applied.value
        const named: string[] = []
        if (template?.repeating) named.push(t('batchCreate.leftOutRepeating'))
        if (template && template.fieldDefaults > 0 && String(template.sheetId ?? '') !== sheetId) {
            named.push(t('batchCreate.leftOutFieldDefaults'))
        }
        if (shareNote.value) named.push(shareNote.value)
        return named
    }

    /**
     * The part of the batch request the template and the settings decide.
     *
     * @param requiresRegistration whether the batch takes sign-ups, without which none of this applies
     */
    function requestPart(requiresRegistration: boolean): Partial<BatchCreateRequest> {
        const template = applied.value
        return {
            registrationLimit: requiresRegistration ? settings.registrationLimit ?? null : null,
            queueOrder: requiresRegistration && settings.registrationLimit ? settings.queueOrder : null,
            minRegistrations: requiresRegistration ? settings.minRegistrations ?? null : null,
            thresholdDays: requiresRegistration && settings.minRegistrations ? settings.thresholdDays ?? null : null,
            registrationDeadline: requiresRegistration ? settings.registrationDeadline : noDeadline(),
            reminderDays: template?.reminderDays ?? [],
            eventTemplateId: template?.id ?? null,
        }
    }

    /**
     * Shares every appointment of the batch the way the template shares its appointments, where the
     * reader may and the template shares at all.
     *
     * @param eventIds the appointments the batch created
     */
    async function shareEach(eventIds: number[]) {
        if (!federationShare.state.federationShared) return
        for (const id of eventIds) await federationShare.save(eventFederationPort(id))
    }

    return {settings, applied, carried, apply, leftOut, requestPart, shareEach}
}
