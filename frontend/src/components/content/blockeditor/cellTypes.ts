/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * Untyped configuration bag used by page-editor cell editors. Each cell stores its own shape
 * inside this record; the renderer reads the keys it cares about and ignores the rest.
 */
export type CellConfig = Record<string, unknown>

/**
 * Base prop shape every cell editor receives - the config bag the editor mutates via
 * `update:config`. Used as `defineProps<CellEditorProps>()` in the simplest cells.
 */
export type CellEditorProps = {
    config: CellConfig
}

/**
 * Prop shape for cell editors that own a freeform text body alongside their config. Used by
 * editors that emit `update:content` as well as `update:config`.
 */
export type CellEditorContentProps = CellEditorProps & {
    content: string
}

/**
 * Prop shape for cell editors that need the owning station's UID to fetch station-scoped data
 * (members, partners, files, knowledge-base articles).
 */
export type CellEditorStationProps = CellEditorProps & {
    stationUid: string
}

/**
 * Prop shape for cell editors that combine a text body with the owning station UID.
 */
export type CellEditorContentStationProps = CellEditorContentProps & {
    stationUid: string
}

/**
 * Emit shape every cell editor uses to push config changes back to the parent.
 */
export type CellEditorEmits = {
    'update:config': [value: CellConfig]
}

/**
 * Emit shape for cell editors that emit both config and content updates.
 */
export type CellEditorContentEmits = CellEditorEmits & {
    'update:content': [value: string]
}
