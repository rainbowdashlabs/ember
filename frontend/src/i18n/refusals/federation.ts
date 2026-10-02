/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    CHANGE_SAVED_BUT_NOT_READ_BACK,
    FEDERATION_ADDRESS_NOT_PUBLIC,
    ITEM_NOT_HERE,
    ITEM_KIND_NOT_HERE,
    INVENTORY_NOT_HERE,
    STATION_NOT_HERE,
    DAY_NOT_A_DATE,
} from './shared'

const PAIR_REQUEST_NOT_HERE = 'Diese Kopplungsanfrage gibt es nicht mehr'
const FEDERATION_SHARE_NOT_HERE = 'Diese Freigabe gibt es nicht mehr, es wurde nichts geändert'

/** What the refusals of federation say in German, keyed by their `X-` code. */
export default {
    'X-001': 'Die Anfrage zum Verbinden kam unvollständig an',
    'X-002': 'Diese Signatur zum Verbinden wurde nicht angenommen',
    'X-003': 'Gib die Adresse an, unter der die Partnerwache zurückgerufen wird',
    'X-004': FEDERATION_ADDRESS_NOT_PUBLIC,
    'X-005': 'Sag, ab welchem Zeitpunkt die Änderungen gewünscht sind',
    'X-006': 'Der Zeitpunkt, ab dem die Änderungen angefragt wurden, ist kein Zeitpunkt',
    'X-007': 'Gib die Adresse an, unter der die Wache jetzt erreichbar ist',
    'X-008': FEDERATION_ADDRESS_NOT_PUBLIC,
    'X-009': STATION_NOT_HERE,
    'X-010': 'Gib den Kopplungscode ein, es wurde nichts gespeichert',
    'X-011': PAIR_REQUEST_NOT_HERE,
    'X-012': PAIR_REQUEST_NOT_HERE,
    'X-013': 'Diese Partnerwache gibt es nicht mehr',
    'X-014': FEDERATION_SHARE_NOT_HERE,
    'X-015': FEDERATION_SHARE_NOT_HERE,
    'X-016': FEDERATION_SHARE_NOT_HERE,
    'X-017': 'Eine Wache kann nicht bei sich selbst ausleihen, es wurde nichts gespeichert',
    'X-018': 'Gib den ersten Tag an, für den die Ausrüstung gebraucht wird, es wurde nichts gespeichert',
    'X-019': 'Diese Ausleihe gibt es nicht mehr, oder du darfst sie nicht öffnen',
    'X-020': 'Ausrüstung lässt sich nur einer zugesagten Ausleihe zuordnen',
    'X-021': 'Das kann nur die Wache tun, der die Ausrüstung gehört',
    'X-022': 'Eine Nachricht braucht einen Text, es wurde nichts gesendet',
    'X-023': 'Gib den ersten und den letzten Tag an, an denen die Ausrüstung gesperrt ist, '
        + 'es wurde nichts gespeichert',
    'X-024': 'Sag, ob die Ausrüstung angeboten oder zurückgehalten wird, es wurde nichts gespeichert',
    'X-025': 'Sag, ob das Angebot alle Partnerwachen erreicht oder nur die genannten, '
        + 'es wurde nichts gespeichert',
    'X-026': CHANGE_SAVED_BUT_NOT_READ_BACK,
    'X-027': CHANGE_SAVED_BUT_NOT_READ_BACK,
    'X-028': CHANGE_SAVED_BUT_NOT_READ_BACK,
    'X-029': CHANGE_SAVED_BUT_NOT_READ_BACK,
    'X-030': CHANGE_SAVED_BUT_NOT_READ_BACK,
    'X-031': CHANGE_SAVED_BUT_NOT_READ_BACK,
    'X-032': CHANGE_SAVED_BUT_NOT_READ_BACK,
    'X-033': 'Die Partnerwache führt diese Partnerschaft nicht mehr als aktiv',
    'X-034': 'Die Partnerwache hat nicht geantwortet oder hatte nichts dazu',
    'X-035': 'Ein angefragter Tag ist kein gültiger Tag',
    'X-036': 'Eine Zeile der Anfrage nennt Ausrüstung, die nicht dieser Wache gehört, es wurde nichts gespeichert',
    'X-037': DAY_NOT_A_DATE,
    'X-038': 'Dieser Punkt der Checkliste gehört nicht zu diesem Ticket',
    'X-039': 'Diese Spalte gehört nicht zu diesem Board',
    'X-040': 'Dieses Label gehört nicht zu diesem Board',
    'X-041': 'Diese Anfrage wurde von keiner Partnerinstanz signiert, die diese Instanz kennt',
    'X-042': 'Diese Partnerwache ist gerade nicht aktiv',
    'X-043': 'Diese Verbindung trägt die Inhalte des Verbunds und lässt sich nicht pausieren, es wurde nichts geändert',
    'X-044': 'Diese Verbindung gehört zum Verbund und endet erst, wenn die Wache ihn verlässt, es wurde nichts geändert',
    'X-045': INVENTORY_NOT_HERE,
    'X-046': 'Dieses Inventar gehört dem Verband über der Wache, die Wache kann daraus nichts verleihen, es wurde nichts gespeichert',
    'X-047': ITEM_KIND_NOT_HERE,
    'X-048': ITEM_NOT_HERE,
    'X-049': 'Diese Wache verleiht keine Ausrüstung an deine',
    'X-050': 'Diese Ausrüstung darf diese Wache nicht verleihen',
}
