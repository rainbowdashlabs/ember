/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, shallowRef, watch, type Ref} from 'vue'
import {documents, documentTemplates, memberGroups, stationMembers, userTags} from '@/api'
import type {
    DocumentTemplateKind,
    DocumentTemplateResponse,
    MemberGroup,
    MemberWithName,
    Placeholder,
    UserTag,
} from '@/api/generated/schema'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {draftOf, emptyDraft, requestOf, type TemplateDraft} from './templateDraft'

/**
 * One template in the editor: the draft being written, what the station's templates can name, the
 * lists the audience and the preview choose from, and for a PDF template the PDF it fills.
 *
 * <p>The lists that need rights the editor of templates may not hold (the members, the document tags)
 * are read where they can be and stay empty where they cannot: they only make choosing easier and are
 * no reason to keep anybody from writing a template.
 *
 * <p>A new PDF of a PDF template becomes the template's current version and only that: the fields in
 * the draft stay as they are, saved or not, to be checked over the new pages.
 *
 * @param templateId the template being changed, or null for a new one
 * @param newKind    what a new template is made of
 */
export function useTemplateEditor(templateId: Ref<number | null>, newKind: Ref<DocumentTemplateKind>) {
    const draft = ref<TemplateDraft>(emptyDraft(newKind.value))
    const saved = ref<DocumentTemplateResponse | null>(null)
    const pdf = shallowRef<Blob | null>(null)
    const placeholders = ref<Placeholder[]>([])
    const groups = ref<MemberGroup[]>([])
    const tags = ref<UserTag[]>([])
    const members = ref<MemberWithName[]>([])
    const documentTags = ref<string[]>([])

    const labels = computed<ReadonlyMap<string, string>>(() =>
        new Map(placeholders.value.map(placeholder => [placeholder.key, placeholder.label])))

    /** What a letter's blocks can name and be restricted to. */
    const letterCatalogue = computed(() => ({
        placeholders: placeholders.value,
        labels: labels.value,
        choices: {groups: groups.value, tags: tags.value},
    }))

    const loader = useAsyncLoader(async () => {
        const [catalogue, groupList, tagList, memberList, tagNames] = await Promise.all([
            documentTemplates.getCatalogue(),
            memberGroups.listGroups().catch(() => []),
            userTags.listTags().catch(() => []),
            stationMembers.listMembers().catch(() => []),
            documents.listTags().catch(() => []),
        ])
        placeholders.value = catalogue.placeholders
        groups.value = groupList
        tags.value = tagList
        members.value = memberList.filter(member => !member.formerAt)
        documentTags.value = tagNames
        if (templateId.value === null) return
        saved.value = await documentTemplates.getTemplate(templateId.value)
        draft.value = draftOf(saved.value)
    })

    const saving = useAsyncAction(async () => {
        const request = requestOf(draft.value)
        const written = saved.value
            ? await documentTemplates.updateTemplate(saved.value.id, request)
            : await documentTemplates.createTemplate(request)
        saved.value = written
        draft.value = draftOf(written)
        return written
    })

    const uploading = useAsyncAction(async (file: File) => {
        if (!saved.value) return null
        saved.value = await documentTemplates.uploadPdf(saved.value.id, file)
        return saved.value
    })

    const pdfLoader = useAsyncAction(async (id: number) => {
        pdf.value = await documentTemplates.templatePdf(id)
    })

    watch(() => saved.value?.pdf?.id ?? null, original => {
        pdf.value = null
        if (original !== null && saved.value) void pdfLoader.run(saved.value.id)
    })

    const archiving = useAsyncAction(async (archived: boolean) => {
        if (!saved.value) return null
        const written = archived
            ? await documentTemplates.archiveTemplate(saved.value.id)
            : await documentTemplates.restoreTemplate(saved.value.id)
        saved.value = written
        return written
    })

    return {
        draft,
        saved,
        pdf,
        pdfLoader,
        uploading,
        placeholders,
        labels,
        letterCatalogue,
        groups,
        tags,
        members,
        documentTags,
        loader,
        saving,
        archiving,
    }
}
