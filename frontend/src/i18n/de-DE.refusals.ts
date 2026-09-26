/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * What each refusal code says in German, keyed by the code the backend sends.
 *
 * <p>The backend already writes a sentence for every refusal and sends it along, so a reader is
 * never left with nothing. This is that sentence in German: `describeFailure` looks up
 * `refusal.<code>` and shows the server's own English only where no key is written here. That is
 * what makes the file safe to fill in one code at a time.
 *
 * <p>The keys are quoted because a code carries a hyphen. It is an ordinary character to the
 * message resolver, which treats only a dot and a bracket as structure, so `refusal.F-001` reaches
 * this object's `'F-001'` as one segment. Written unquoted it is not valid as an object key at all.
 *
 * <p>Several codes say the same thing: one lookup that missed and the ownership check behind it,
 * or the same object refused from eleven routes. Those name a shared constant rather than repeating
 * the sentence, so the wording has one home here exactly as it does in the registry it mirrors.
 *
 * <p>No sentence ends in a full stop, which is how the registry writes them. A screen that puts one
 * of these beside a sentence no one has translated yet would otherwise show two conventions at once.
 */
const INSTANCE_UNREACHABLE = 'Diese Instanz war nicht erreichbar'
const NOTHING_SAVED_UNUSABLE = 'Etwas am Gesendeten war nicht verwendbar, es wurde nichts gespeichert'
const DOCUMENT_NOT_YOURS = 'Dieses Dokument gehört zu jemandem, für den du nicht zuständig bist'
const CHOOSE_A_STATION = 'Wähle zuerst eine Wache aus'
const DOCUMENT_NOT_YOURS_TO_ADD = 'Du darfst für dieses Mitglied kein Dokument ablegen'
const DOCUMENT_NOT_HERE = 'Dieses Dokument gibt es nicht mehr'
const PICTURE_NOT_HERE = 'Dieses Bild gibt es nicht'
const REGISTRATION_NOT_HERE = 'Diese Anmeldung gibt es nicht mehr'
const STATION_NOT_HERE = 'Diese Wache gibt es nicht'
const APPOINTMENT_NOT_HERE = 'Diesen Termin gibt es nicht mehr'
const FORM_NOT_HERE = 'Dieses Formular gibt es nicht mehr, es wurde nichts geändert'
const NOT_HERE_OR_NOT_YOURS = 'Das gibt es nicht mehr, oder du darfst es nicht öffnen'
const FORM_NOT_FROM_OUTSIDE = 'Dieses Formular lässt sich nicht von außerhalb der Wache beantworten'
const UNEXPECTED_FAULT = 'In Ember ist etwas schiefgegangen und die Anfrage wurde nicht ausgeführt, '
    + 'es wurde nichts gespeichert. Ein erneuter Versuch kann klappen; wenn es weiter passiert, melde es bitte'
const FILE_NOT_HERE = 'Diese Datei gibt es nicht mehr'
const UPLOAD_WITHOUT_FILE = 'Der Upload kam ohne Datei an'
const UPLOAD_NOT_SAVED = 'Der Upload konnte nicht gespeichert werden'
const UPLOAD_NOT_PROCESSED = 'Der Upload konnte nicht verarbeitet werden, es wurde nichts gespeichert. '
    + 'Ein erneuter Versuch kann klappen'
const FOLDER_NOT_HERE = 'Diesen Ordner gibt es nicht mehr'
const TAG_NOT_HERE = 'Dieses Tag gibt es nicht mehr'
const SESSION_WITHOUT_ACCOUNT = 'Diese Sitzung gehört zu keinem Konto'
const MEMBER_NOT_HERE = 'Dieses Mitglied gibt es nicht mehr'
const ACCOUNT_NOT_HERE = 'Dieses Konto gibt es nicht mehr'
const PROFILE_FIELD_NOT_HERE = 'Dieses Profilfeld gibt es nicht mehr'
const TOO_MANY_ATTEMPTS = 'Zu viele Versuche von hier aus. Versuche es gleich noch einmal'
const PAGE_NOT_HERE = 'Diese Seite gibt es nicht mehr'
const ATTEMPT_NOT_HERE = 'Diesen Versuch gibt es nicht mehr'
const CATALOG_NOT_HERE = 'Diesen Katalog gibt es nicht mehr'
const CATEGORY_NOT_HERE = 'Diese Kategorie gibt es nicht mehr'
const TEST_NOT_HERE = 'Diesen Test gibt es nicht mehr'
const PROCEDURE_NOT_YOURS = 'Dieser Vorgang wurde dir nicht übertragen'
const APPLICATION_NOT_HERE = 'Diese Bewerbung gibt es nicht mehr'
const QUESTION_NOT_ON_LIST = 'Diese Frage steht nicht mehr auf dieser Warteliste'
const NO_LIST_FOR_INVITE = 'Diese Einladung führt zu keiner Warteliste'
const ENTRY_NOT_HERE = 'Diesen Eintrag gibt es nicht mehr'
const WAITING_LIST_NOT_HERE = 'Diese Warteliste gibt es nicht mehr'

export default {
    'A-001': INSTANCE_UNREACHABLE,
    'A-002': INSTANCE_UNREACHABLE,

    'B-001': 'Die Anfrage war kein gültiges JSON, es wurde nichts gespeichert',
    'B-002': 'Die Anfrage enthält ein Feld, das diese Schnittstelle nicht annimmt',
    'B-003': 'Die Anfrage hat nicht die Form, die diese Schnittstelle erwartet',
    'B-004': 'Ein Wert in der Anfrage ist keiner, den diese Schnittstelle annimmt',
    'B-005': NOTHING_SAVED_UNUSABLE,

    'D-001': 'Diese Wache führt keine Dokumente',
    'D-002': DOCUMENT_NOT_YOURS,
    'D-003': 'Du darfst ein Dokument nicht als verborgen kennzeichnen',
    'D-004': CHOOSE_A_STATION,
    'D-005': DOCUMENT_NOT_YOURS_TO_ADD,
    'D-006': DOCUMENT_NOT_HERE,
    'D-007': PICTURE_NOT_HERE,
    'D-008': DOCUMENT_NOT_HERE,
    'D-009': DOCUMENT_NOT_YOURS,
    'D-010': 'Du darfst dieses Dokument nicht ändern',
    'D-011': DOCUMENT_NOT_YOURS_TO_ADD,
    'D-012': DOCUMENT_NOT_YOURS_TO_ADD,

    'E-001': REGISTRATION_NOT_HERE,
    'E-002': STATION_NOT_HERE,
    'E-003': STATION_NOT_HERE,
    'E-004': STATION_NOT_HERE,
    'E-005': REGISTRATION_NOT_HERE,
    'E-006': REGISTRATION_NOT_HERE,
    'E-007': REGISTRATION_NOT_HERE,
    'E-008': REGISTRATION_NOT_HERE,
    'E-009': REGISTRATION_NOT_HERE,
    'E-010': REGISTRATION_NOT_HERE,
    'E-011': REGISTRATION_NOT_HERE,
    'E-012': REGISTRATION_NOT_HERE,
    'E-013': APPOINTMENT_NOT_HERE,
    'E-014': APPOINTMENT_NOT_HERE,
    'E-015': APPOINTMENT_NOT_HERE,

    'F-001': FORM_NOT_HERE,
    'F-002': FORM_NOT_HERE,
    'F-003': FORM_NOT_HERE,
    'F-004': FORM_NOT_HERE,
    'F-005': FORM_NOT_HERE,
    'F-006': FORM_NOT_HERE,
    'F-007': NOT_HERE_OR_NOT_YOURS,
    'F-008': STATION_NOT_HERE,
    'F-009': 'Dieser Link führt zu keinem Formular',
    'F-010': 'Dieses Formular nimmt keine Antworten an',
    'F-011': 'Dieses Formular wurde von hier aus zu oft beantwortet. Versuche es gleich noch einmal',
    'F-012': 'Du hast dieses Formular bereits beantwortet',
    'F-013': 'Die Antworten konnten nicht gespeichert werden',
    'F-014': 'Die Antworten waren nicht lesbar, es wurde nichts gespeichert. '
        + 'Fülle das Formular noch einmal aus und sende es erneut',
    'F-015': STATION_NOT_HERE,
    'F-016': FORM_NOT_HERE,
    'F-017': FORM_NOT_FROM_OUTSIDE,
    'F-018': FORM_NOT_FROM_OUTSIDE,
    'F-019': 'Das Formular, das dieser Abschnitt der Seite zeigt, gibt es nicht mehr',
    'F-020': 'Diese Antwort gibt es nicht mehr',

    'G-001': UNEXPECTED_FAULT,
    'G-002': UNEXPECTED_FAULT,
    'G-003': 'Etwas mit denselben Angaben gibt es bereits, es wurde nichts gespeichert',
    'G-004': 'Das verweist auf etwas, das es nicht mehr gibt, oder etwas anderes hängt noch daran, '
        + 'es wurde nichts gespeichert',
    'G-005': 'Etwas am Gesendeten passt nicht zu dem, was sich hier speichern lässt, '
        + 'es wurde nichts gespeichert',
    'G-006': 'Jemand hat dasselbe im selben Moment geändert, deshalb wurde diese Änderung verworfen. '
        + 'Ein erneuter Versuch klappt meistens',
    'G-007': 'Das hat zu lange gedauert und wurde abgebrochen, bevor etwas gespeichert wurde. '
        + 'Ein erneuter Versuch kann klappen',
    'G-008': 'Ember konnte seinen Datenspeicher nicht erreichen, es wurde nichts gespeichert. '
        + 'Ein Versuch in einem Moment klappt meistens',
    'G-009': 'Diese Adresse führt zu nichts',
    'G-010': NOT_HERE_OR_NOT_YOURS,
    'G-011': NOT_HERE_OR_NOT_YOURS,

    'L-001': FILE_NOT_HERE,
    'L-002': PICTURE_NOT_HERE,
    'L-003': CHOOSE_A_STATION,
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

    'M-001': PICTURE_NOT_HERE,
    'M-002': UPLOAD_WITHOUT_FILE,
    'M-003': 'Diese Datei ließ sich nicht als Bild verwenden',
    'M-004': UPLOAD_NOT_SAVED,
    'M-005': UPLOAD_NOT_PROCESSED,
    'M-006': SESSION_WITHOUT_ACCOUNT,
    'M-007': SESSION_WITHOUT_ACCOUNT,
    'M-008': MEMBER_NOT_HERE,
    'M-009': ACCOUNT_NOT_HERE,
    'M-010': ACCOUNT_NOT_HERE,
    'M-011': ACCOUNT_NOT_HERE,
    'M-012': MEMBER_NOT_HERE,
    'M-013': ACCOUNT_NOT_HERE,
    'M-014': ACCOUNT_NOT_HERE,
    'M-015': CHOOSE_A_STATION,
    'M-016': PROFILE_FIELD_NOT_HERE,
    'M-017': PROFILE_FIELD_NOT_HERE,

    'N-001': TOO_MANY_ATTEMPTS,

    'P-001': PAGE_NOT_HERE,
    'P-002': 'Die Seite konnte nicht gespeichert werden',
    'P-003': PAGE_NOT_HERE,
    'P-004': PAGE_NOT_HERE,
    'P-005': PAGE_NOT_HERE,
    'P-006': PAGE_NOT_HERE,
    'P-007': PAGE_NOT_HERE,
    'P-008': PAGE_NOT_HERE,
    'P-009': PAGE_NOT_HERE,
    'P-010': STATION_NOT_HERE,
    'P-011': 'Dieser Link führt zu keiner Seite',

    'Q-001': 'Diese Arbeit wurde bereits abgegeben, die Antwort wurde nicht gespeichert. '
        + 'Lade die Seite neu, um den aktuellen Stand zu sehen',
    'Q-002': 'Diese Arbeit konnte nicht abgegeben werden: sie ist bereits abgegeben, '
        + 'oder die Zeit dafür ist abgelaufen. Lade die Seite neu, um den aktuellen Stand zu sehen',
    'Q-003': ATTEMPT_NOT_HERE,
    'Q-004': NOT_HERE_OR_NOT_YOURS,
    'Q-005': ATTEMPT_NOT_HERE,
    'Q-006': ATTEMPT_NOT_HERE,
    'Q-007': 'Dieser Versuch gehört jemand anderem',
    'Q-008': CATALOG_NOT_HERE,
    'Q-009': CATALOG_NOT_HERE,
    'Q-010': CATALOG_NOT_HERE,
    'Q-011': CATALOG_NOT_HERE,
    'Q-012': CATEGORY_NOT_HERE,
    'Q-013': CATEGORY_NOT_HERE,
    'Q-014': 'In diesem Format gibt es keine Beispieldatei',
    'Q-015': TEST_NOT_HERE,
    'Q-016': TEST_NOT_HERE,
    'Q-017': TEST_NOT_HERE,
    'Q-018': TEST_NOT_HERE,
    'Q-019': TEST_NOT_HERE,
    'Q-020': TEST_NOT_HERE,

    'R-001': PROCEDURE_NOT_YOURS,
    'R-002': PROCEDURE_NOT_YOURS,

    'S-001': 'Dieser Bestätigungslink gilt nicht mehr',
    'S-002': APPLICATION_NOT_HERE,
    'S-003': APPLICATION_NOT_HERE,
    'S-004': APPLICATION_NOT_HERE,
    'S-005': 'Unter dieser Adresse ist bereits eine andere Wache erreichbar',

    'T-001': 'Diesen Abschnitt gibt es nicht mehr',
    'T-002': 'Diesen Punkt gibt es nicht mehr',

    'W-001': QUESTION_NOT_ON_LIST,
    'W-002': NO_LIST_FOR_INVITE,
    'W-003': ENTRY_NOT_HERE,
    'W-004': NO_LIST_FOR_INVITE,
    'W-005': WAITING_LIST_NOT_HERE,
    'W-006': ENTRY_NOT_HERE,
    'W-007': WAITING_LIST_NOT_HERE,
    'W-008': STATION_NOT_HERE,
    'W-009': ENTRY_NOT_HERE,
    'W-010': TOO_MANY_ATTEMPTS,
    'W-011': WAITING_LIST_NOT_HERE,
    'W-012': WAITING_LIST_NOT_HERE,
    'W-013': WAITING_LIST_NOT_HERE,
    'W-014': QUESTION_NOT_ON_LIST,
    'W-015': WAITING_LIST_NOT_HERE,
    'W-016': ENTRY_NOT_HERE,
    'W-017': ENTRY_NOT_HERE,
    'W-018': STATION_NOT_HERE,
    'W-019': WAITING_LIST_NOT_HERE,
    'W-020': WAITING_LIST_NOT_HERE,
    'W-021': WAITING_LIST_NOT_HERE,
}
