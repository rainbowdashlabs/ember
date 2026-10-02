/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    PICTURE_NOT_HERE,
    STATION_NOT_HERE,
    FILE_NOT_HERE,
    UPLOAD_WITHOUT_FILE,
    UPLOAD_NOT_SAVED,
    UPLOAD_NOT_PROCESSED,
    FOLDER_NOT_HERE,
    TAG_NOT_HERE,
} from './shared'

/** What the refusals of the media library say in German, keyed by their `L-` code. */
export default {
    'L-018': 'Nur wer eine Datei hochgeladen hat, kann sie entfernen',
    'L-019': 'Ein Ordner braucht einen Namen, es wurde nichts gespeichert',
    'L-020': 'Ein Tag braucht einen Namen, es wurde nichts gespeichert',
    'L-021': FILE_NOT_HERE,
    'L-022': STATION_NOT_HERE,

    'L-001': FILE_NOT_HERE,
    'L-002': PICTURE_NOT_HERE,
    'L-004': UPLOAD_WITHOUT_FILE,
    'L-005': 'Diese Datei ist größer, als diese Instanz annimmt',
    'L-006': UPLOAD_NOT_SAVED,
    'L-007': UPLOAD_NOT_PROCESSED,
    'L-008': FILE_NOT_HERE,
    'L-009': FILE_NOT_HERE,
    'L-010': FILE_NOT_HERE,
    'L-011': FOLDER_NOT_HERE,
    'L-012': FOLDER_NOT_HERE,
    'L-013': TAG_NOT_HERE,
    'L-014': TAG_NOT_HERE,
    'L-015': TAG_NOT_HERE,
    'L-016': TAG_NOT_HERE,
    'L-023': 'Diese Datei hängt noch an Neuigkeiten oder Terminen und wurde nicht gelöscht. Entferne sie dort zuerst',
}
