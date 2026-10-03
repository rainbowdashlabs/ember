/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {DocumentTemplateKind, type DocumentTemplateSummary} from '@/api/generated/schema'
import {ColumnTypes, enumOptions, type TableColumn} from '@/components/table/tableColumn'
import type {Translate} from '@/util/failure'

/**
 * The columns of the template list: its name, whether it is a letter or a PDF, whether it is legal or
 * for self service, its version and when it changed.
 */
export function templateColumns(t: Translate): TableColumn<DocumentTemplateSummary>[] {
    return [
        {key: 'name', label: t('documentTemplates.name'), type: ColumnTypes.TEXT, value: template => template.name, pinned: true},
        {
            key: 'kind', label: t('documentTemplates.kindColumn'), type: ColumnTypes.ENUM, value: template => template.kind,
            options: enumOptions(Object.values(DocumentTemplateKind), value => t(`documentTemplates.kind.${value}`)),
        },
        {key: 'legal', label: t('documentTemplates.legal'), type: ColumnTypes.BOOLEAN, value: template => template.legal},
        {key: 'selfService', label: t('documentTemplates.selfService'), type: ColumnTypes.BOOLEAN, value: template => template.selfService},
        {key: 'version', label: t('documentTemplates.versionColumn'), type: ColumnTypes.NUMBER, value: template => template.version},
        {key: 'updatedAt', label: t('documentTemplates.updatedAt'), type: ColumnTypes.DATE_TIME, value: template => template.updatedAt},
    ]
}
