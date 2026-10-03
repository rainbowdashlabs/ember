/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {DocumentTemplateKind, type DocumentTemplateSummary} from '@/api/generated/schema'
import {ColumnTypes, enumOptions, type TableColumn} from '@/components/table/tableColumn'
import type {Translate} from '@/util/failure'

/** The column the list opens sorted by, newest first: the template used last on top, those never used at the end. */
export const LAST_USED_COLUMN = 'lastUsedAt'

/**
 * The columns of the template list: its name, whether it is a letter or a PDF, whether it is legal or
 * for self service, its version, when it was created and changed, and when a document was last
 * generated from it. A station's list also says which templates its association keeps.
 *
 * @param t                translates the labels
 * @param withAssociations whether the list holds templates of the association next to the owner's own
 */
export function templateColumns(t: Translate, withAssociations: boolean): TableColumn<DocumentTemplateSummary>[] {
    return [
        {key: 'name', label: t('documentTemplates.name'), type: ColumnTypes.TEXT, value: template => template.name, pinned: true},
        {
            key: 'kind', label: t('documentTemplates.kindColumn'), type: ColumnTypes.ENUM, value: template => template.kind,
            options: enumOptions(Object.values(DocumentTemplateKind), value => t(`documentTemplates.kind.${value}`)),
        },
        ...(withAssociations
            ? [{
                key: 'ofAssociation', label: t('documentTemplates.ofAssociation'), type: ColumnTypes.BOOLEAN,
                value: (template: DocumentTemplateSummary) => template.ofAssociation,
            }]
            : []),
        {key: 'legal', label: t('documentTemplates.legal'), type: ColumnTypes.BOOLEAN, value: template => template.legal},
        {key: 'selfService', label: t('documentTemplates.selfService'), type: ColumnTypes.BOOLEAN, value: template => template.selfService},
        {key: 'version', label: t('documentTemplates.versionColumn'), type: ColumnTypes.NUMBER, value: template => template.version},
        {key: 'createdAt', label: t('documentTemplates.createdAt'), type: ColumnTypes.DATE_TIME, value: template => template.createdAt},
        {key: 'updatedAt', label: t('documentTemplates.updatedAt'), type: ColumnTypes.DATE_TIME, value: template => template.updatedAt},
        {
            key: LAST_USED_COLUMN, label: t('documentTemplates.lastUsedAt'), type: ColumnTypes.DATE_TIME,
            value: template => template.lastUsedAt ?? null,
        },
    ]
}
