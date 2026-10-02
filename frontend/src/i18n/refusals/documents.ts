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
    'D-017': 'Diese Datei konnte nicht gelesen werden, es wurde nichts abgelegt',
    'D-018': 'Die Wache hat keinen Platz mehr für diese Datei, es wurde nichts abgelegt',
    'D-019': 'Diese Datei gibt sich als eine Art Datei aus und ist eine andere, es wurde nichts abgelegt',
    'D-020': 'Du darfst ein Dokument nicht mit Tags versehen oder über die Mitgliedschaft hinaus behalten',
    'D-021': 'Die Mitglieder wurden nicht als Nummern angegeben',
}
