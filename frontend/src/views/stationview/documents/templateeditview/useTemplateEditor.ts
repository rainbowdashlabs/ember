/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, shallowRef, watch, type Ref} from 'vue'
import {documents, stationMembers} from '@/api'
import {audienceLists} from '@/components/documents/generation'
import {
    DocumentLanguage,
    type DocumentFontsResponse,
    type DocumentTemplateKind,
    type DocumentTemplateResponse,
    type FontFamilyOption,
    type MemberGroup,
    type MemberWithName,
    type Placeholder,
    type PlaceholderCatalogueResponse,
    type UserTag,
} from '@/api/generated/schema'
import type {PlaceholderLabels} from '@/components/input/markdowneditor/placeholderChip'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {provideFontSamples} from '@/composables/useFontSamples'
import {useEditorFonts} from '@/composables/useEditorFonts'
import {draftOf, emptyDraft, requestOf, type TemplateDraft} from './templateDraft'
import {letterFamilies} from './letterFonts'
import {placeholdersOfTemplate} from './placeholderpicker/placeholderTree'
import {placeholderLabels, providePlaceholderDates, type PlaceholderDates} from './placeholderpicker/placeholderDates'
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
    const [lists, members, documentTags] = await Promise.all([
        audienceLists(),
        stationMembers.listMembers().catch(() => []),
        documents.listTags().catch(() => []),
    ])
    return {...lists, members: members.filter(member => !member.formerAt), documentTags}
}

/**
 * One template in the editor: the draft being written, what the owner's templates can name, the font
 * families they reach and the default font a text naming none prints in, the lists the audience and the
 * preview choose from, and for a PDF template the PDF it fills.
 *
 * <p>The font pickers below draw their samples through the owner's font source, which this provides.
 * While the editor is open, the font files of the families the letter uses are loaded through the same
 * source, so its text shows in them ({@link useEditorFonts}); they go when the editor closes. The
 * placeholder pickers below get the date formats of the catalogue and the language their examples are
 * written in: the template's, or the owner's until the template names one. The labels of keys carry the
 * example of their date format the same way.
 *
 * <p>The default font is named as the owner's font list names it, which knows the font this instance
 * prints in; where the list cannot be read, the default font goes unnamed.
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
    const offer = shallowRef<PlaceholderCatalogueResponse>({placeholders: [], dateFormats: [], language: DocumentLanguage.DE})
    const catalogue = computed<Placeholder[]>(() => offer.value.placeholders)
    const placeholders = computed(() => placeholdersOfTemplate(catalogue.value, draft.value.forAppointments))
    const choices = shallowRef<StationChoices>(noChoices())
    const fontList = shallowRef<DocumentFontsResponse | null>(null)
    const fonts = computed<readonly FontFamilyOption[]>(() => fontList.value?.reachable ?? [])
    provideFontSamples(screens.fonts.sample)
    const usedFamilies = computed(() => letterFamilies(draft.value))
    useEditorFonts(screens.fonts.file, () => fontList.value, () => usedFamilies.value)

    const dates = computed<PlaceholderDates>(() => ({
        formats: offer.value.dateFormats,
        language: draft.value.language ?? offer.value.language,
    }))
    providePlaceholderDates(dates)

    const labels = computed<PlaceholderLabels>(() => placeholderLabels(catalogue.value, dates.value))

    /** What a letter's blocks can name, set words in and be restricted to. */
    const letterCatalogue = computed(() => ({
        placeholders: placeholders.value,
        labels: labels.value,
        choices: {groups: choices.value.groups, tags: choices.value.tags},
        fonts: fonts.value,
    }))

    const loader = useAsyncLoader(async () => {
        const id = templateId.value
        const [offered, lists, reached, template] = await Promise.all([
            source.catalogue(),
            screens.hasMembers ? stationChoices() : Promise.resolve(noChoices()),
            screens.fonts.list().catch(() => null),
            id === null ? Promise.resolve(null) : source.get(id),
        ])
        offer.value = offered
        choices.value = lists
        fontList.value = reached
        if (!template) return
        saved.value = template
        draft.value = draftOf(template)
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
        defaultFamily: computed(() => fontList.value?.defaultFamily ?? ''),
        loader,
        saving,
        archiving,
    }
}
