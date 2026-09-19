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

/**
 * A list of mails as a table, its column choices remembered under the given id.
 *
 * @param perStation whether the choices follow the selected station, which the station's own mailing
 *                   page wants and the whole instance's mail log does not
 */
export function useMailRecordTable(id: string, rows: MaybeRefOrGetter<readonly MailRecord[]>, perStation: boolean) {
    const {t} = useI18n()
    return useDataTable<MailRecord>({
        id,
        perStation,
        rows,
        columns: computed(() => mailRecordColumns(t)),
        rowKey: mail => mail.id,
    })
}
