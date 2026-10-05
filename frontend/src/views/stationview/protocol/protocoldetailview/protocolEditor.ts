/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {inject, type InjectionKey, type Ref} from 'vue'
import type {TestProtocolItem, TestProtocolSection} from '@/api/generated/schema'
import type {ProtocolTree} from '../protocolTree'

/**
 * What every section of the protocol editor reaches, at whatever depth it sits: the tree it reads and
 * the actions it offers. The editor page provides it once, so a section ten levels down asks the same
 * object as one at the top instead of every level passing a dozen events up.
 */
export interface ProtocolEditor {
    tree: Readonly<Ref<ProtocolTree>>
    canEdit: Readonly<Ref<boolean>>
    /** The section waiting to be pasted somewhere else, if one was cut. */
    cut: Readonly<Ref<TestProtocolSection | null>>
    addItem(sectionId: number): void
    addSubsection(parentId: number): void
    editSection(section: TestProtocolSection): void
    deleteSection(sectionId: number): void
    editItem(item: TestProtocolItem): void
    deleteItem(itemId: number): void
    reorderItems(sectionId: number, fromIndex: number, toIndex: number): void
    reorderSections(parentId: number | null, fromIndex: number, toIndex: number): void
    cutSection(section: TestProtocolSection): void
    /** Whether the cut section may be pasted under this parent, which is never itself or below itself. */
    canPasteInto(parentId: number | null): boolean
    pasteInto(parentId: number | null): void
}

export const protocolEditorKey: InjectionKey<ProtocolEditor> = Symbol('protocolEditor')

/** The editor the page provides. A section outside an editor page is a mistake in the page, not a state. */
export function useProtocolEditor(): ProtocolEditor {
    const editor = inject(protocolEditorKey)
    if (!editor) throw new Error('A protocol section needs the editor its page provides')
    return editor
}
