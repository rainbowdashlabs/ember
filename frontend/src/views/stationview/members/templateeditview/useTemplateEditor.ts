/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, shallowRef, watch, type Ref} from 'vue'
import {documents, memberGroups, stationMembers, userTags} from '@/api'
import type {
    DocumentTemplateKind,
    DocumentTemplateResponse,
    FontFamilyOption,
    MemberGroup,
    MemberWithName,
    Placeholder,
    UserTag,
} from '@/api/generated/schema'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {draftOf, emptyDraft, requestOf, type TemplateDraft} from './templateDraft'
import {placeholdersOfTemplate} from './placeholderpicker/placeholderTree'
import type {TemplateScreens} from '../templateScreens'

/** The lists of a station a template's audience, its blocks and its preview choose from. */
interface StationChoices {
    groups: MemberGroup[]
    tags: UserTag[]
    members: MemberWithName[]
    documentTags: string[]
}

/** No lists, which is what an association has. */
function noChoices(): StationChoices {
    return {groups: [], tags: [], members: [], documentTags: []}
}

/**
 * The station's lists, each read where the reader may and left empty where not: they only make
 * choosing easier and are no reason to keep anybody from writing a template.
 */
async function stationChoices(): Promise<StationChoices> {
    const [groups, tags, members, documentTags] = await Promise.all([
        memberGroups.listGroups().catch(() => []),
        userTags.listTags().catch(() => []),
        stationMembers.listMembers().catch(() => []),
        documents.listTags().catch(() => []),
    ])
    return {groups, tags, members: members.filter(member => !member.formerAt), documentTags}
}

/**
 * One template in the editor: the draft being written, what the owner's templates can name, the font
 * families they reach, the lists the audience and the preview choose from, and for a PDF template the
 * PDF it fills.
 *
 * <p>An association has no members, groups or document tags of its own, so its lists stay empty; its
 * stations choose the audience of its templates themselves.
 *
 * <p>The values of an appointment are offered only while the template is for appointments, since only
 * a document generated for an appointment fills them.
 *
 * <p>A new PDF of a PDF template becomes the template's current version and only that: the fields in
 * the draft stay as they are, saved or not, to be checked over the new pages.
 *
 * @param templateId the template being changed, or null for a new one
 * @param newKind    what a new template is made of
 * @param screens    whose template it is
 */
export function useTemplateEditor(
    templateId: Ref<number | null>, newKind: Ref<DocumentTemplateKind>, screens: TemplateScreens) {
    const source = screens.source
    const draft = ref<TemplateDraft>(emptyDraft(newKind.value))
    const saved = ref<DocumentTemplateResponse | null>(null)
    const pdf = shallowRef<Blob | null>(null)
    const catalogue = ref<Placeholder[]>([])
    const placeholders = computed(() => placeholdersOfTemplate(catalogue.value, draft.value.forAppointments))
    const choices = shallowRef<StationChoices>(noChoices())
    const fonts = ref<FontFamilyOption[]>([])

    const labels = computed<ReadonlyMap<string, string>>(() =>
        new Map(catalogue.value.map(placeholder => [placeholder.key, placeholder.label])))

    /** What a letter's blocks can name and be restricted to. */
    const letterCatalogue = computed(() => ({
        placeholders: placeholders.value,
        labels: labels.value,
        choices: {groups: choices.value.groups, tags: choices.value.tags},
    }))

    const loader = useAsyncLoader(async () => {
        const [offered, lists, fontList] = await Promise.all([
            source.catalogue(),
            screens.hasMembers ? stationChoices() : Promise.resolve(noChoices()),
            screens.fonts.list().then(list => list.reachable).catch(() => []),
        ])
        catalogue.value = offered.placeholders
        choices.value = lists
        fonts.value = fontList
        if (templateId.value === null) return
        saved.value = await source.get(templateId.value)
        draft.value = draftOf(saved.value)
    })

    const saving = useAsyncAction(async () => {
        const request = requestOf(draft.value)
        const written = saved.value
            ? await source.update(saved.value.id, request)
            : await source.create(request)
        saved.value = written
        draft.value = draftOf(written)
        return written
    })

    const uploading = useAsyncAction(async (file: File) => {
        if (!saved.value) return null
        saved.value = await source.uploadPdf(saved.value.id, file)
        return saved.value
    })

    const pdfLoader = useAsyncAction(async (id: number) => {
        pdf.value = await source.templatePdf(id)
    })

    watch(() => saved.value?.pdf?.id ?? null, original => {
        pdf.value = null
        if (original !== null && saved.value) void pdfLoader.run(saved.value.id)
    })

    const archiving = useAsyncAction(async (archived: boolean) => {
        if (!saved.value) return null
        const written = archived
            ? await source.archive(saved.value.id)
            : await source.restore(saved.value.id)
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
        groups: computed(() => choices.value.groups),
        tags: computed(() => choices.value.tags),
        members: computed(() => choices.value.members),
        documentTags: computed(() => choices.value.documentTags),
        fonts,
        loader,
        saving,
        archiving,
    }
}
