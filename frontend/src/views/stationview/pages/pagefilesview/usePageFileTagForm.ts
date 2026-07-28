/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {
    createPageTag,
    deletePageTag,
    updatePageTag,
    type PageFileTag,
} from '@/api/pageManage'

/**
 * Create / rename / delete flow for file tags, including the modal state the tag dialog binds to.
 */
export function usePageFileTagForm(
    activeTagFilter: Ref<number | null>,
    reloadTags: () => Promise<void>,
    reload: () => Promise<void>,
) {
    const {t} = useI18n()

    const tagModalOpen = ref(false)
    const tagName = ref('')
    const tagColor = ref('#888888')
    const editingTag = ref<PageFileTag | null>(null)

    function openTagModal() {
        tagName.value = ''
        tagColor.value = '#888888'
        editingTag.value = null
        tagModalOpen.value = true
    }

    function openTagEdit(tag: PageFileTag) {
        tagName.value = tag.name
        tagColor.value = tag.color ?? '#888888'
        editingTag.value = tag
        tagModalOpen.value = true
    }

    async function saveTag() {
        if (!tagName.value.trim()) return
        if (editingTag.value) await updatePageTag(editingTag.value.id, tagName.value, tagColor.value)
        else await createPageTag(tagName.value, tagColor.value)
        tagModalOpen.value = false
        await reloadTags()
    }

    async function removeTag(tag: PageFileTag) {
        if (!confirm(t('stationPages.editor.tagDeletePrompt', {name: tag.name}))) return
        await deletePageTag(tag.id)
        if (activeTagFilter.value === tag.id) activeTagFilter.value = null
        await reload()
    }

    return {tagModalOpen, tagName, tagColor, editingTag, openTagModal, openTagEdit, saveTag, removeTag}
}
