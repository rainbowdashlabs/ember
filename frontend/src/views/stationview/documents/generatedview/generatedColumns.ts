/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {GeneratedDocumentEntry} from '@/api/generated/schema'
import {ColumnTypes, type TableColumn} from '@/components/table/tableColumn'
import type {Translate} from '@/util/failure'

/**
 * The columns of the list of generated documents: whom a document is about, which template and which
 * version of it, whether the template is the association's, who generated it and when, whether it was
 * created through self service and whether it is still in the member's documents. A person who is gone
 * is named as such.
 *
 * @param t translates the labels
 */
export function generatedColumns(t: Translate): TableColumn<GeneratedDocumentEntry>[] {
    const gone = t('generatedDocuments.gone')
    return [
        {key: 'member', label: t('generatedDocuments.member'), type: ColumnTypes.TEXT, value: entry => entry.memberName ?? gone, pinned: true},
        {key: 'template', label: t('generatedDocuments.template'), type: ColumnTypes.TEXT, value: entry => entry.templateName},
        {key: 'version', label: t('documentTemplates.versionColumn'), type: ColumnTypes.NUMBER, value: entry => entry.templateVersion},
        {key: 'ofAssociation', label: t('documentTemplates.ofAssociation'), type: ColumnTypes.BOOLEAN, value: entry => entry.ofAssociation},
        {key: 'generatedBy', label: t('generatedDocuments.generatedBy'), type: ColumnTypes.TEXT, value: entry => entry.generatedByName ?? gone},
        {key: 'generatedAt', label: t('generatedDocuments.generatedAt'), type: ColumnTypes.DATE_TIME, value: entry => entry.generatedAt},
        {key: 'selfService', label: t('generatedDocuments.selfService'), type: ColumnTypes.BOOLEAN, value: entry => entry.selfService},
        {key: 'filed', label: t('generatedDocuments.filed'), type: ColumnTypes.BOOLEAN, value: entry => entry.documentId !== null},
    ]
}
