/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import type {DocumentTemplateCopy} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import type {Translate} from '@/util/failure'
import {showToast} from '@/util/toast'
import type {TemplateScreens} from './templateScreens'

/** How long a notice about what the copy cannot print stays, long enough to read a list of fonts. */
const NOTICE_MS = 10000

/**
 * What the reader should know about a fresh copy: the fonts its owner does not reach and the pictures
 * missing from its owner's media library, each of which prints as it would anywhere it is missing.
 *
 * @param copy the copy as the server answered
 * @param t    translates the notices
 * @returns one notice per kind of gap, none where the copy prints as the original did
 */
export function copyNotices(copy: Pick<DocumentTemplateCopy, 'fontsOutOfReach' | 'picturesOutOfReach'>, t: Translate): string[] {
    return [
        ...(copy.fontsOutOfReach.length > 0
            ? [t('documentTemplates.copyFontsOutOfReach', {fonts: copy.fontsOutOfReach.join(', ')})]
            : []),
        ...(copy.picturesOutOfReach > 0
            ? [t('documentTemplates.copyPicturesOutOfReach', {count: copy.picturesOutOfReach})]
            : []),
    ]
}

/**
 * Copies a template of the list or the editor as a new template of the owner, named "Kopie von"
 * the original, and opens the editor on the copy. A station's copy of its association's template is
 * the station's own.
 *
 * @param screens whose templates they are
 */
export function useTemplateDuplication(screens: TemplateScreens) {
    const {t} = useI18n()
    const router = useRouter()

    return useAsyncAction(async (template: {id: number, name: string}) => {
        const copy = await screens.source.duplicate(template.id, t('documentTemplates.copyName', {name: template.name}))
        showToast(t('documentTemplates.duplicated'), 'success')
        copyNotices(copy, t).forEach(notice => showToast(notice, 'info', NOTICE_MS))
        await router.push({name: screens.editRoute, params: {id: copy.template.id}})
        return copy
    })
}
