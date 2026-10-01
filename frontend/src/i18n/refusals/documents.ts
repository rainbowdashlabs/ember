/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    DOCUMENT_NOT_HERE,
    PICTURE_NOT_HERE,
    UPLOAD_WITHOUT_FILE,
} from './shared'

const DOCUMENT_NOT_YOURS = 'Dieses Dokument gehört zu jemandem, für den du nicht zuständig bist'
const DOCUMENT_NOT_YOURS_TO_ADD = 'Du darfst für dieses Mitglied kein Dokument ablegen'

/** What the refusals of documents say in German, keyed by their `D-` code. */
export default {
    'D-001': 'Diese Wache führt keine Dokumente',
    'D-002': DOCUMENT_NOT_YOURS,
    'D-003': 'Du darfst ein Dokument nicht als verborgen kennzeichnen',
    'D-005': DOCUMENT_NOT_YOURS_TO_ADD,
    'D-006': DOCUMENT_NOT_HERE,
    'D-007': PICTURE_NOT_HERE,
    'D-008': DOCUMENT_NOT_HERE,
    'D-009': DOCUMENT_NOT_YOURS,
    'D-010': 'Du darfst dieses Dokument nicht ändern',
    'D-011': DOCUMENT_NOT_YOURS_TO_ADD,
    'D-012': DOCUMENT_NOT_YOURS_TO_ADD,
    'D-013': UPLOAD_WITHOUT_FILE,
    'D-014': 'Diese Datei ist größer, als diese Instanz annimmt',
    'D-015': 'Dokumente über dieses Mitglied werden aufbewahrt und nennen niemanden sonst, das Mitglied wurde nicht gelöscht. Archiviere es stattdessen oder entferne zuerst die Dokumente',
    'D-016': 'Eine Wache bewahrt Dokumente über dieses Konto auf, das Konto wurde nicht gelöscht. Die Wache muss die Mitgliedschaft zuerst archivieren oder die Dokumente entfernen',
}
