/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    FIELD_NOT_HERE,
    COMMENT_NOT_HERE,
    COMMENT_NEEDS_TEXT,
    FILE_NOT_HERE,
    UPLOAD_WITHOUT_FILE,
    UPLOAD_NOT_SAVED,
    ATTACHMENT_NOT_HERE,
    UPLOAD_TOO_LARGE,
    PARTNER_STATION_NOT_HERE,
} from './shared'

const BOARD_NOT_HERE = 'Dieses Board gibt es nicht mehr'
const BOARD_TICKET_NOT_HERE = 'Dieses Ticket gibt es nicht mehr'
const BOARD_LABEL_NOT_HERE = 'Dieses Label gibt es nicht mehr'
const FEDERATED_BOARD_NOT_HERE = 'Dieses geteilte Board gibt es nicht mehr'
const FEDERATION_PARTNER_NOT_HERE_FOR_BOARDS = 'Diese Wache kennt diese Partnerinstanz nicht'

/** What the refusals of boards and tickets say in German, keyed by their `BO-` code. */
export default {
    'BO-001': 'Dieses Board gibt es nicht, oder du darfst es nicht öffnen',
    'BO-002': BOARD_TICKET_NOT_HERE,
    'BO-004': 'Dieses Board darfst du nicht ändern',
    'BO-006': 'Ein Board braucht einen Namen, es wurde nichts gespeichert',
    'BO-007': 'Ein Board braucht einen eigenen kurzen Schlüssel, es wurde nichts gespeichert',
    'BO-008': 'Dieses Board darfst du nicht öffnen',
    'BO-009': BOARD_NOT_HERE,
    'BO-010': BOARD_NOT_HERE,
    'BO-011': 'Ein Label braucht einen Namen, es wurde nichts gespeichert',
    'BO-012': BOARD_LABEL_NOT_HERE,
    'BO-013': BOARD_LABEL_NOT_HERE,
    'BO-014': 'Ein Ticket braucht einen Titel, es wurde nichts gespeichert',
    'BO-015': BOARD_TICKET_NOT_HERE,
    'BO-016': BOARD_TICKET_NOT_HERE,
    'BO-017': BOARD_TICKET_NOT_HERE,
    'BO-018': COMMENT_NEEDS_TEXT,
    'BO-019': 'Ein Eintrag auf der Checkliste braucht einen Titel, es wurde nichts gespeichert',
    'BO-020': FIELD_NOT_HERE,
    'BO-021': 'Dieser Wert passt nicht zu diesem Feld, es wurde nichts gespeichert',
    'BO-022': COMMENT_NOT_HERE,
    'BO-023': UPLOAD_WITHOUT_FILE,
    'BO-024': UPLOAD_TOO_LARGE,
    'BO-025': UPLOAD_NOT_SAVED,
    'BO-026': FILE_NOT_HERE,
    'BO-027': 'Dieser Anhang konnte nicht ausgeliefert werden. Ein erneuter Versuch kann helfen',
    'BO-028': ATTACHMENT_NOT_HERE,
    'BO-029': ATTACHMENT_NOT_HERE,
    'BO-030': BOARD_TICKET_NOT_HERE,
    'BO-031': BOARD_NOT_HERE,
    'BO-032': FILE_NOT_HERE,
    'BO-033': 'Ein Link braucht eine Adresse, es wurde nichts gespeichert',
    'BO-034': 'Diesen Link gibt es nicht mehr',
    'BO-035': FEDERATION_PARTNER_NOT_HERE_FOR_BOARDS,
    'BO-036': 'Dieses geteilte Board darfst du nicht öffnen',
    'BO-037': 'Dieses geteilte Board darfst du nicht ändern',
    'BO-038': FEDERATION_PARTNER_NOT_HERE_FOR_BOARDS,
    'BO-039': FEDERATED_BOARD_NOT_HERE,
    'BO-040': FEDERATED_BOARD_NOT_HERE,
    'BO-042': BOARD_NOT_HERE,
    'BO-043': BOARD_TICKET_NOT_HERE,
    'BO-044': 'Dieses Board ist nicht mit dieser Instanz geteilt',
    'BO-045': 'Dieses Board ist mit dieser Instanz nur zum Lesen geteilt',
    'BO-046': BOARD_NOT_HERE,
    'BO-047': BOARD_NOT_HERE,
    'BO-048': BOARD_TICKET_NOT_HERE,
    'BO-051': BOARD_TICKET_NOT_HERE,
    'BO-052': BOARD_TICKET_NOT_HERE,
    'BO-053': BOARD_TICKET_NOT_HERE,
    'BO-054': COMMENT_NOT_HERE,
    'BO-055': BOARD_TICKET_NOT_HERE,
    'BO-056': 'Boards bieten diesen Feldtyp nicht an, es wurde nichts gespeichert',
    'BO-057': 'Dieses Feld muss ausgefüllt sein, deshalb wurde es nicht geleert',
    'BO-058': PARTNER_STATION_NOT_HERE,
    'BO-059': 'Wem das Ticket übergeben wird, ist kein Mitglied der Wache dieses Boards, es wurde nichts gespeichert',
    'BO-060': 'Wem das Ticket übergeben wird, darf auf diesem Board nicht arbeiten, es wurde nichts gespeichert',
}
