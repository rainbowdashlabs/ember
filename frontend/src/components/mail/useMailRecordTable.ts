/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type MaybeRefOrGetter} from 'vue'
import {useI18n} from 'vue-i18n'
import type {MailRecord} from '@/api/mailProviders'
import {useDataTable} from '@/composables/useDataTable'
import {mailRecordColumns} from './mailRecordColumns'

/** A list of mails as a table, its column choices remembered under the given id. */
export function useMailRecordTable(id: string, rows: MaybeRefOrGetter<readonly MailRecord[]>) {
    const {t} = useI18n()
    return useDataTable<MailRecord>({
        id,
        rows,
        columns: computed(() => mailRecordColumns(t)),
        rowKey: mail => mail.id,
    })
}
