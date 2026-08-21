/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {useConfirmAction} from '@/composables/useConfirmAction'
import {createMediaTag, deleteMediaTag, updateMediaTag, type StationFileTag} from '@/api/media'

/**
 * Create / rename / delete flow for file tags, including the modal state the tag dialog binds to.
 *
 * <p>Deletion is a request-then-confirm pair rather than a single call: `removeTag` opens the
 * confirmation and `confirmRemoveTag` performs it, so the view can render the styled modal.
 */
export function useMediaTagForm(
    activeTagFilter: Ref<number | null>,
    reloadTags: () => Promise<void>,
    reload: () => Promise<void>,
) {
    const tagModalOpen = ref(false)
    const tagName = ref('')
    const tagColor = ref('#888888')
    const editingTag = ref<StationFileTag | null>(null)

    function openTagModal() {
        tagName.value = ''
        tagColor.value = '#888888'
        editingTag.value = null
        tagModalOpen.value = true
    }

    function openTagEdit(tag: StationFileTag) {
        tagName.value = tag.name
        tagColor.value = tag.color ?? '#888888'
        editingTag.value = tag
        tagModalOpen.value = true
    }

    async function saveTag() {
        if (!tagName.value.trim()) return
        if (editingTag.value) await updateMediaTag(editingTag.value.id, tagName.value, tagColor.value)
        else await createMediaTag(tagName.value, tagColor.value)
        tagModalOpen.value = false
        await reloadTags()
    }

    const deleteTag = useConfirmAction<StationFileTag>({
        onConfirm: async tag => {
            await deleteMediaTag(tag.id)
            if (activeTagFilter.value === tag.id) activeTagFilter.value = null
        },
        onSuccess: () => reload(),
    })

    return {
        tagModalOpen,
        tagName,
        tagColor,
        editingTag,
        openTagModal,
        openTagEdit,
        saveTag,
        removeTag: deleteTag.request,
        showDeleteTag: deleteTag.show,
        deleteTagTarget: deleteTag.target,
        confirmRemoveTag: deleteTag.confirm,
    }
}
