/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, type Ref} from 'vue'
import type {TestProtocolSection} from '@/api/generated/schema'
import type {ProtocolTree} from '../protocolTree'

/**
 * Cutting a section out of the protocol and pasting it somewhere else, with everything under it.
 *
 * <p>A cut section can go under any section that is not itself or below itself, or to the top level
 * unless it already stands there. Pasting is the move; until then nothing has changed.
 *
 * @param tree the protocol as it stands
 * @param move puts the section under the parent, or at the top level for {@code null}, and reads back
 */
export function useSectionCut(
    tree: Readonly<Ref<ProtocolTree>>,
    move: (sectionId: number, parentId: number | null) => Promise<void>,
) {
    const cut = ref<TestProtocolSection | null>(null)

    function start(section: TestProtocolSection) {
        cut.value = section
    }

    function cancel() {
        cut.value = null
    }

    function canPasteInto(parentId: number | null): boolean {
        const section = cut.value
        if (!section) return false
        if (parentId === null) return section.parentId != null
        return parentId !== section.parentId && !tree.value.subtreeOf(section.id).has(parentId)
    }

    async function pasteInto(parentId: number | null) {
        const section = cut.value
        if (!section || !canPasteInto(parentId)) return
        cut.value = null
        await move(section.id, parentId)
    }

    return {cut: computed(() => cut.value), start, cancel, canPasteInto, pasteInto}
}
