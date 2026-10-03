/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, type Ref} from 'vue'
import {documents, documentTemplates, memberGroups, stationMembers, userTags} from '@/api'
import type {
    ChoiceField,
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
 * One letter template in the editor: the draft being written, what the station's templates can name,
 * and the lists the audience and the preview choose from.
 *
 * <p>The lists that need rights the editor of templates may not hold (the members, the document tags)
 * are read where they can be and stay empty where they cannot: they only make choosing easier and are
 * no reason to keep anybody from writing a template.
 *
 * @param templateId the template being changed, or null for a new one
 */
export function useTemplateEditor(templateId: Ref<number | null>) {
    const draft = ref<TemplateDraft>(emptyDraft())
    const saved = ref<DocumentTemplateResponse | null>(null)
    const placeholders = ref<Placeholder[]>([])
    const choiceFields = ref<ChoiceField[]>([])
    const groups = ref<MemberGroup[]>([])
    const tags = ref<UserTag[]>([])
    const members = ref<MemberWithName[]>([])
    const documentTags = ref<string[]>([])

    const labels = computed<ReadonlyMap<string, string>>(() =>
        new Map(placeholders.value.map(placeholder => [placeholder.key, placeholder.label])))

    const loader = useAsyncLoader(async () => {
        const [catalogue, groupList, tagList, memberList, tagNames] = await Promise.all([
            documentTemplates.getCatalogue(),
            memberGroups.listGroups().catch(() => []),
            userTags.listTags().catch(() => []),
            stationMembers.listMembers().catch(() => []),
            documents.listTags().catch(() => []),
        ])
        placeholders.value = catalogue.placeholders
        choiceFields.value = catalogue.choiceFields
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
        placeholders,
        labels,
        choiceFields,
        groups,
        tags,
        members,
        documentTags,
        loader,
        saving,
        archiving,
    }
}
