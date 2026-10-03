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
    'D-022': 'Diese Dokumentvorlage gibt es nicht',
    'D-023': 'Eine Dokumentvorlage braucht einen Namen',
    'D-024': 'Eine andere Dokumentvorlage trägt schon diesen Namen',
    'D-025': 'Diese Dokumentvorlage ist archiviert und erstellt keine Dokumente mehr',
    'D-026': 'Die Vorlage verwendet einen Platzhalter, den es nicht gibt',
    'D-027': 'Ein rechtliches Dokument verwendet nur amtliche Namen, nicht den Rufnamen',
    'D-028': 'Kopf- und Fußzeile haben höchstens drei Felder',
    'D-029': 'Ein Bild des Briefkopfs ist kein Bild aus der Mediathek',
    'D-030': 'Ränder liegen zwischen 5 und 80 Millimetern, die Schriftgröße zwischen 8 und 16 Punkt',
    'D-031': 'Pronomen können nur einem Auswahlfeld dieser Wache folgen',
    'D-032': 'Die Wartezeit zwischen zwei Dokumenten kann nicht negativ sein',
    'D-033': 'Ein Text der Vorlage ist zu lang',
    'D-034': 'Das Dokument konnte nicht erstellt werden',
    'D-035': DOCUMENT_NOT_YOURS_TO_ADD,
    'D-036': 'Dieses Dokument kann für dieses Mitglied nicht selbst erstellt werden',
    'D-037': 'Für dieses Dokument fehlen Angaben im Profil',
    'D-038': 'Dieses Dokument wurde gerade erst erstellt und kann wieder erstellt werden ab',
    'D-039': 'Dokumente lassen sich nur für dich selbst und die Mitglieder in deiner Obhut erstellen',
    'D-040': 'Nur Word- (.docx) und OpenDocument-Texte (.odt) lassen sich importieren',
    'D-041': 'Das Dokument konnte nicht gelesen werden, es wurde nichts importiert',
    'D-042': 'Für eine PDF-Vorlage lässt sich nur ein PDF hochladen',
    'D-043': 'Das PDF konnte nicht gelesen werden, es wurde nicht als Vorlage übernommen',
    'D-044': 'Das PDF öffnet sich nur mit einem Passwort und kann deshalb keine Vorlage sein',
    'D-045': 'Das PDF ist auf eine Weise geschützt, die sich nicht entfernen lässt, und kann deshalb keine Vorlage sein',
    'D-046': 'Diese Vorlage ist ein Brief, keine PDF-Vorlage',
    'D-047': 'Für diese Vorlage wurde noch kein PDF hochgeladen',
    'D-048': 'Ein Feld liegt außerhalb der Seiten des PDFs',
    'D-049': 'Ein Text- oder Ankreuzfeld braucht seinen Text, ein Unterschriftsfeld seine unterschreibende Person',
    'D-050': 'Die Schriftgröße eines Felds liegt zwischen 4 und 72 Punkt',
    'D-051': 'Jede unterschreibende Person hat höchstens ein Unterschriftsfeld',
    'D-052': 'Die Vorlage füllt ein Formularfeld, das das PDF nicht hat oder das sich nicht füllen lässt',
    'D-053': 'Eine Vorlage hat höchstens 200 Felder',
    'D-054': 'Eine Unterschrift lässt sich nur als Unterschriftsfeld setzen, nicht in einem Text',
}
