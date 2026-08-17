/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {MODULE_TRACKS} from './pitchModules'
import {TRUST_GDPR, TRUST_SESSIONS} from './pitchTrustData'
import type {PitchTrack} from './pitchTypes'

const OPENING: PitchTrack[] = [
    {
        overview: {
            kind: 'cover', id: 'cover', accent: 'primary',
            heading: 'Eure Gruppe.',
            headingAccent: 'Ein Werkzeug.',
            lead: 'Termine, Mitglieder, Anwesenheit, Ausbildung, Newsletter und Webseite — '
                + 'kostenlos, quelloffen, selbst gehostet. Allein oder gemeinsam mit den Nachbarwachen.',
            pills: ['Open Source', 'Selbst gehostet', 'DSGVO-konform', 'Eine Instanz für alle Wachen'],
        },
        details: [
            {
                kind: 'showcase', id: 'cover-detail', accent: 'primary', tone: 'accent',
                chip: 'Wie diese Präsentation funktioniert',
                heading: 'Links und rechts durch die Themen,',
                headingAccent: 'runter in die Tiefe.',
                lead: 'Jede Spalte ist ein Thema. Unter jedem Thema liegt, wie es in der Anwendung wirklich aussieht.',
                screens: [
                    {
                        title: 'Steuerung', subtitle: 'Tastatur und Adresse',
                        table: {
                            columns: ['Taste', 'Wirkung', 'Ebene'],
                            rows: [
                                [{text: '← →', strong: true}, {text: 'Thema wechseln'},
                                    {text: 'Überblick', badge: 'primary'}],
                                [{text: '↑ ↓', strong: true}, {text: 'in die Tiefe und zurück'},
                                    {text: 'Deep Dive', badge: 'secondary'}],
                                [{text: 'F', strong: true}, {text: 'Vollbild'},
                                    {text: 'Präsentation', badge: 'info'}],
                            ],
                        },
                        footer: 'Die Adresse folgt der Position: /pitch/3/2 ist Thema 3, Detailfolie 2.',
                    },
                ],
                points: [
                    'Jede Folie ist verlinkbar — für Rückfragen per Nachricht',
                    'Die Präsentation folgt eurem hellen oder dunklen Erscheinungsbild',
                    'Gebaut aus denselben Bausteinen wie die Anwendung selbst',
                ],
            },
        ],
    },
    {
        overview: {
            kind: 'cards', id: 'problem', accent: 'error', tone: 'accent', chip: 'Warum Ember',
            heading: 'Verwaltung kostet Zeit, die ihr lieber in',
            headingAccent: 'Ausbildung und Gemeinschaft steckt.',
            columns: 3,
            cards: [
                {
                    icon: '📋', title: 'Tabellen-Chaos', accent: 'error',
                    body: 'Anwesenheitslisten in zehn Tabellen, jede mit eigenem Format. Niemand findet die aktuelle Fassung.',
                },
                {
                    icon: '💬', title: 'Verstreute Kommunikation', accent: 'error',
                    body: 'Termine im Messenger, News im sozialen Netz, Aushänge im Gerätehaus. Nichts ist zentral, nichts ist sicher.',
                },
                {
                    icon: '💸', title: 'Teure Software', accent: 'error',
                    body: 'Kommerzielle Lösungen kosten oft hunderte Euro im Jahr — Geld, das eurer Jugend fehlt.',
                },
            ],
            note: 'Die Arbeit dahinter ist Ehrenamt. Verwaltung sollte das nicht auch noch sein.',
        },
        details: [],
    },
    {
        overview: {
            kind: 'cards', id: 'solution', accent: 'primary', chip: 'Die Lösung',
            heading: 'Eine Plattform.',
            headingAccent: 'Alles drin.',
            lead: 'Ember bündelt, was eine Gruppe für die digitale Organisation braucht — '
                + 'von der Dienstplanung über die Mitgliederverwaltung bis zur öffentlichen Webseite.',
            columns: 2,
            cards: [
                {icon: '✓', title: 'Keine Lizenzkosten', body: 'Keine Nutzergebühren, keine Staffelpreise, keine Testphase, die ausläuft.', accent: 'success'},
                {icon: '✓', title: 'Daten bleiben bei euch', body: 'Auf eurem Server, in eurer Hand — kein zentraler Dienst dazwischen.', accent: 'success'},
                {icon: '✓', title: 'Quelloffen', body: 'Der Quellcode ist einsehbar. Keine versteckte Logik, keine Abhängigkeit von einem Anbieter.', accent: 'success'},
                {icon: '✓', title: 'Mehrere Wachen unter einem Dach', body: 'Eine Installation trägt beliebig viele Wachen, streng voneinander getrennt.', accent: 'success'},
            ],
        },
        details: [
            {
                kind: 'showcase', id: 'solution-day', accent: 'primary', tone: 'accent',
                chip: 'Der Einstieg',
                heading: 'Nach dem Anmelden:',
                headingAccent: 'was gerade ansteht.',
                lead: 'Die Übersicht ist die Startseite jedes Mitglieds — Kacheln für das, was es betrifft, '
                    + 'und nur für die Module, die die Wache nutzt.',
                screens: [
                    {
                        title: 'Übersicht', subtitle: 'Was heute ansteht',
                        panels: [
                            {
                                icon: 'bell', title: 'Benachrichtigungen', count: 3, action: 'Alle gelesen',
                                entries: [
                                    {title: 'Neue Anmeldung für Zeltlager', meta: 'Anna Müller · vor 2 Stunden'},
                                    {title: 'Profilfeld geändert', meta: 'Ben Krüger · Telefonnummer · gestern'},
                                ],
                            },
                            {
                                icon: 'calendar-plus', title: 'Nächste Termine', action: 'Alle anzeigen',
                                entries: [
                                    {title: 'Knotenkunde', meta: 'Dienstag, 18.06. · 18:00 – 19:30'},
                                    {title: 'Schlauchübung', meta: 'Samstag, 22.06. · 09:30 – 12:00'},
                                ],
                            },
                            {
                                icon: 'calendar-days', title: 'Meine Anmeldungen', action: 'Alle anzeigen',
                                entries: [
                                    {title: 'Zeltlager', meta: 'zugesagt · 12.–14.07.'},
                                    {title: 'Kreisentscheid', meta: 'Anmeldung bis 20.06.'},
                                ],
                            },
                            {
                                icon: 'rotate', title: 'Tausch-Anfragen', count: 1, action: 'Alle anzeigen',
                                entries: [
                                    {title: 'Jacke Größe 152 → 164', meta: 'Anna Müller · offen'},
                                ],
                            },
                        ],
                        footer: 'Kacheln erscheinen nur, wenn das Modul dahinter freigeschaltet ist.',
                    },
                ],
                points: [
                    'Jede Kachel führt in ihren Bereich — nichts ist eine Sackgasse',
                    'Sorgeberechtigte sehen die Anmeldungen ihrer Kinder',
                    'Wer sein Profil noch nicht vollständig hat, wird oben daran erinnert',
                ],
            },
        ],
    },
    {
        overview: {
            kind: 'cards', id: 'modules', accent: 'secondary', tone: 'accent', chip: 'Was drin ist',
            heading: 'Module, die',
            headingAccent: 'zusammenarbeiten.',
            columns: 4,
            cards: [
                {icon: '📅', title: 'Termine', body: 'Serien, Anmeldung mit eigenen Fragen, Kalender-Abo.'},
                {icon: '📊', title: 'Anwesenheit', body: 'Listen je Dienst, Prüfmodus, Bericht als PDF.', accent: 'success'},
                {icon: '👥', title: 'Mitglieder', body: 'Profile, Rollen, Gruppen, Tags, Sorgeberechtigte.', accent: 'secondary'},
                {icon: '📰', title: 'Neuigkeiten', body: 'Beiträge mit Kommentaren, Blog und Feeds.', accent: 'error'},
                {icon: '📖', title: 'Wiki', body: 'Ordner, Seiten, Dateien — mit Rechten je Eintrag.', accent: 'info'},
                {icon: '❓', title: 'Quiz', body: 'Acht Fragetypen, Training ohne Note, Tests mit Bewertung.', accent: 'success'},
                {icon: '📋', title: 'Prüfungsbögen', body: 'Abnahmen mit mehreren Prüfern und Auswertung.', accent: 'info'},
                {icon: '📦', title: 'Inventar', body: 'Ausgabe, Lager, Prüfung, Tausch, Beschaffung, Ausleihe.', accent: 'secondary'},
                {icon: '🧤', title: 'Fundbüro', body: 'Liegengebliebenes findet zurück.', accent: 'info'},
                {icon: '📝', title: 'Formulare', body: 'Umfragen mit sechs Fragetypen und Diagrammen.', accent: 'secondary'},
                {icon: '⏳', title: 'Wartelisten', body: 'Anmeldung von außen, Punkteformel, Probezeit.'},
                {icon: '🗂️', title: 'Boards', body: 'Kanban mit Tickets, Checklisten und Verlauf.', accent: 'info'},
                {icon: '✅', title: 'Abläufe', body: 'Vorlagen mit Schritten und Abhängigkeiten.', accent: 'success'},
                {icon: '🌐', title: 'Öffentliche Seiten', body: 'Die Website aus Bausteinen, ohne zweites System.', accent: 'error'},
                {icon: '🔔', title: 'Benachrichtigungen', body: 'App, E-Mail und Feed — je Art einzeln.'},
                {icon: '🔗', title: 'Föderation', body: 'Teilen mit Partnerwachen, Umziehen mit einem Code.', accent: 'primary'},
            ],
            note: 'Jede Wache schaltet frei, was sie braucht — abgeschaltete Module tauchen nirgends auf. '
                + 'Die nächsten Folien gehen sie der Reihe nach durch, mit Pfeil nach unten in die Tiefe.',
        },
        details: [],
    },
]

/**
 * What sets Ember apart from a folder on a shared drive: stations stay their own, and yet
 * they can work together — and leave again. This stands before the modules on purpose.
 */
const FEDERATION: PitchTrack[] = [
    {
        overview: {
            kind: 'cards', id: 'federation', accent: 'info', tone: 'accent', chip: 'Macht mit',
            heading: 'Eine Instanz —',
            headingAccent: 'für uns alle.',
            lead: 'Statt dass jede Wache ihre eigene Installation betreibt: eine gemeinsame Instanz für '
                + 'Nachbarwachen, Kreis und Dachverband. Geteilte Infrastruktur, geteilte Kosten — '
                + 'keine geteilten Daten.',
            columns: 2,
            cards: [
                {icon: '🏠', title: 'Autonom', body: 'Jede Wache verwaltet sich selbst — eigene Rollen, eigene Farben, eigene Webseite.', accent: 'success'},
                {icon: '🔗', title: 'Verbunden', body: 'Termine, Neuigkeiten, Wiki und Prüfungsbögen selektiv teilen — oder privat halten.', accent: 'success'},
                {icon: '📈', title: 'Überblick', body: 'Der Kreis sieht die Zahlen über alle Wachen, ohne in ihre Daten zu greifen.', accent: 'success'},
                {icon: '🛠️', title: 'Ein Betrieb', body: 'Ein Server, ein Update, ein Ansprechpartner — statt zwanzig Tabelleninseln.', accent: 'success'},
            ],
        },
        details: [
            {
                kind: 'showcase', id: 'federation-detail', accent: 'info',
                chip: 'So sieht es aus',
                heading: 'Jede Freigabe',
                headingAccent: 'einzeln — und in beide Richtungen.',
                lead: 'Zwei Wachen verbinden sich über einen Code. Danach entscheidet jede für sich, was sie '
                    + 'empfängt und was sie sendet — je Funktion, nicht pauschal.',
                screens: [
                    {
                        title: 'Föderation', subtitle: 'Partnerwachen',
                        actions: ['Partner hinzufügen'],
                        section: 'Verbunden',
                        rows: [
                            {
                                name: 'Talbach', plain: true, description: 'Vertragsversion v3 · seit 14.03.2025',
                                badges: [{text: 'Aktiv', tone: 'success'}], action: 'Verwalten',
                                actionIcon: 'sliders',
                            },
                            {
                                name: 'Bergheim', plain: true, description: 'Vertragsversion v3 · seit 02.11.2025',
                                badges: [{text: 'Aktiv', tone: 'success'}], action: 'Verwalten',
                                actionIcon: 'sliders',
                            },
                            {
                                name: 'Kreis Musterland', plain: true, description: 'Anfrage vom 12.06.2026',
                                badges: [{text: 'Ausstehend', tone: 'secondary'}], action: 'Verwalten',
                                actionIcon: 'sliders',
                            },
                        ],
                        footer: 'Verbunden wird über einen Einladungscode — eine Seite erzeugt ihn, die andere '
                            + 'trägt ihn ein.',
                    },
                    {
                        title: 'Talbach', subtitle: 'Was wir teilen',
                        badges: [{text: 'Aktiv', tone: 'success'}],
                        federation: {
                            capabilities: [
                                {label: 'Wissensbasis', receive: true, send: true},
                                {label: 'Quiz-Kataloge', receive: true, send: false},
                                {label: 'Prüfungsbögen', receive: true, send: true},
                                {label: 'Termine', receive: true, send: true},
                                {label: 'Boards', receive: false, send: true},
                                {label: 'Neuigkeiten', receive: true, send: false},
                                {label: 'Material verleihen', receive: true, send: true},
                            ],
                        },
                        footer: 'Aussetzen und beenden geht jederzeit — von jeder Seite aus.',
                    },
                ],
                points: [
                    'Mitgliederdaten wandern nicht mit; geteilt werden Inhalte, nicht Personen',
                    'Beide Seiten müssen dieselbe Vertragsversion sprechen — sonst sagt es die Anzeige',
                    'Auch ohne gemeinsame Instanz: zwei eigene Instanzen genügen',
                ],
            },
            {
                kind: 'showcase', id: 'federation-transfer', accent: 'info', tone: 'accent',
                chip: 'Kein Einbahnstraßen-Vertrag',
                heading: 'Umziehen',
                headingAccent: 'mit einem Code.',
                lead: 'Wer die Instanz wechseln will — vom Verband zum eigenen Server oder umgekehrt — nimmt '
                    + 'seine Wache mit. Ein Code auf der einen Seite, derselbe Code auf der anderen.',
                screens: [
                    {
                        title: 'Wache übertragen', subtitle: 'Transfer und Import',
                        transfer: {},
                        footer: 'Der Transfer-Code enthält die Quelladresse und gilt 24 Stunden.',
                    },
                    {
                        title: 'Wache übertragen', subtitle: 'Import läuft',
                        transfer: {
                            progress: {
                                stationName: 'Musterstadt',
                                status: 'IN_PROGRESS',
                                phases: ['station', 'member_group', 'station_member', 'event',
                                    'attendance_session', 'inventory_item', 'kb_file', 'storage_backend',
                                    'files_kb', 'account_avatars'],
                                completedPhases: 6,
                                currentPhase: 'kb_file',
                                subTotal: 240,
                                subCompleted: 118,
                                error: null,
                            },
                        },
                        footer: 'Die Liste steht von Anfang an; jede Station hakt sich ab, während sie läuft.',
                    },
                ],
                points: [
                    'Mitglieder, Termine, Inventar, Wiki und Dateien ziehen zusammen um',
                    'Kein Export in eine Tabelle, kein Nachtippen, kein Dienstleister dazwischen',
                    'Wer gehen will, kann gehen — das ist der Unterschied zu einem Vertrag mit Bindung',
                ],
            },
        ],
    },
]

const CLOSING: PitchTrack[] = [
    {
        overview: {
            kind: 'cards', id: 'trust', accent: 'success', chip: 'Vertrauen & Kontrolle',
            heading: 'Eure Daten. Euer Server.',
            headingAccent: 'Euer Code.',
            columns: 3,
            cards: [
                {icon: '🔓', title: '100 % Open Source', body: 'Quellcode öffentlich einsehbar. Prüfung jederzeit möglich, keine Tracker.', accent: 'success'},
                {icon: '🏠', title: 'Selbst gehostet', body: 'Läuft auf eurem eigenen Server, im Rathaus oder günstig in der EU-Cloud.', accent: 'secondary'},
                {icon: '🛡️', title: 'DSGVO ab Werk', body: 'Datenauskunft je Mitglied, feine Rollen, Änderungsprotokolle, Löschroutinen.', accent: 'primary'},
            ],
            metrics: [
                {value: '0 €', label: 'Lizenzkosten — für immer', accent: 'success'},
                {value: '∞', label: 'Wachen pro Instanz', accent: 'primary'},
                {value: 'DE', label: 'Komplett auf Deutsch', accent: 'secondary'},
                {value: 'EU', label: 'Daten bleiben in der EU', accent: 'error'},
            ],
        },
        details: [
            {
                kind: 'showcase', id: 'trust-security', accent: 'error', tone: 'accent',
                chip: 'Sicherheit',
                heading: 'Ernst gemeint,',
                headingAccent: 'nicht nur behauptet.',
                lead: 'Was eine Verwaltung für Minderjährige können muss, ist eingebaut: jeder sieht seine '
                    + 'eigenen Anmeldungen, und jeder kommt jederzeit an seine Daten.',
                screens: [
                    {
                        title: 'Aktive Sitzungen', subtitle: 'Wo dieses Konto angemeldet ist',
                        trust: TRUST_SESSIONS,
                        footer: 'Jede fremde Sitzung lässt sich einzeln beenden — oder alle auf einmal.',
                    },
                    {
                        title: 'Daten & Konto', subtitle: 'Auskunft und Löschung',
                        trust: TRUST_GDPR,
                        footer: 'Wer die Profile seiner Kinder verwaltet, exportiert deren Daten gleich mit.',
                    },
                ],
                points: [
                    'Zwei-Faktor per App oder Sicherheitsschlüssel, auf Wunsch für die ganze Wache erzwungen',
                    'Neue Passwörter werden gegen bekannte Datenlecks geprüft',
                    'Ändert sich ein Rechtstext, wird erneut zugestimmt — mit Protokoll',
                ],
            },
        ],
    },
    {
        overview: {
            kind: 'columns', id: 'audiences', accent: 'primary', tone: 'accent', chip: 'Für das Ehrenamt gebaut',
            heading: 'Bedienbar,',
            headingAccent: 'ohne IT-Studium.',
            groups: [
                {
                    title: 'Für die Leitung',
                    items: [
                        'Mitglieder, Sorgeberechtigte und Kontakte an einem Ort',
                        'Dienste, Anwesenheit und Auswertung ohne Nebenrechnung',
                        'Mitteilung, sobald sich jemand an- oder abmeldet',
                        'Druckfertige Berichte für den Träger',
                    ],
                },
                {
                    title: 'Für Mitglieder & Eltern',
                    items: [
                        'Termine im eigenen Kalender als Abo',
                        'Zu- und Absagen mit zwei Klicks',
                        'Neuigkeiten und Erinnerungen auf dem gewählten Kanal',
                        'Sorgeberechtigte verwalten das Profil ihres Kindes mit',
                    ],
                },
                {
                    title: 'Für Dachverbände',
                    items: [
                        'Eine Installation für beliebig viele Wachen',
                        'Gemeinsames Wiki, Quiz und Prüfungsbögen',
                        'Verbundtermine einmal anlegen, überall sichtbar',
                        'Jede Wache bleibt Herrin ihrer Daten',
                    ],
                },
                {
                    title: 'Für Admins',
                    items: [
                        'Ein Docker-Compose, fertig',
                        'PostgreSQL als einzige Abhängigkeit',
                        'Versioniertes Schema mit sauberen Migrationen',
                        'Speicher-Kontingente und Überwachung eingebaut',
                    ],
                },
            ],
        },
        details: [
            {
                kind: 'showcase', id: 'audiences-ops', accent: 'secondary',
                chip: 'Für den Betrieb',
                heading: 'Was ein Admin',
                headingAccent: 'wirklich zu tun hat.',
                lead: 'Aufsetzen, laufen lassen, gelegentlich hinsehen.',
                screens: [
                    {
                        title: 'Speicher-Übersicht', subtitle: 'Nutzung und Kontingente aller Wachen',
                        station: 'Instanz-Verwaltung',
                        table: {
                            columns: ['Wache', 'Belegt', 'Kontingent', 'Status'],
                            rows: [
                                [{text: 'Musterstadt', strong: true}, {text: '1,9 GB'}, {text: '5 GB'},
                                    {text: '38 %', badge: 'success'}],
                                [{text: 'Talbach', strong: true}, {text: '4,4 GB'}, {text: '5 GB'},
                                    {text: '88 %', badge: 'error'}],
                                [{text: 'Oberberg', strong: true}, {text: '0,7 GB'}, {text: '5 GB'},
                                    {text: '14 %', badge: 'success'}],
                            ],
                        },
                        footer: 'Ein erschöpftes Kontingent lehnt weitere Uploads ab — angekündigt, nicht überraschend.',
                    },
                    {
                        title: 'Überwachung', subtitle: 'API-Status',
                        station: 'Instanz-Verwaltung',
                        table: {
                            columns: ['Bereich', 'Anfragen', 'Fehler', 'Antwortzeit'],
                            rows: [
                                [{text: 'Termine', strong: true}, {text: '12 400'}, {text: '0', badge: 'success'},
                                    {text: '38 ms'}],
                                [{text: 'Mitglieder', strong: true}, {text: '8 100'}, {text: '2', badge: 'info'},
                                    {text: '44 ms'}],
                                [{text: 'Feeds', strong: true}, {text: '3 200'}, {text: '0', badge: 'success'},
                                    {text: '12 ms'}],
                            ],
                        },
                    },
                ],
                points: [
                    'Updates ziehen die Datenbank selbst nach',
                    'Sicherung: Datenbank plus zwei Verzeichnisse',
                    'Speicher wahlweise lokal, per S3 oder Netzfreigabe',
                ],
            },
        ],
    },
    {
        overview: {
            kind: 'roadmap', id: 'roadmap', accent: 'secondary', chip: 'Wohin die Reise geht',
            heading: 'Schon da. Und es kommt',
            headingAccent: 'noch mehr.',
            columns: [
                {
                    label: '✓ Heute live', accent: 'success',
                    items: [
                        'Termine, Anmeldungen & Anwesenheit',
                        'Mitglieder, Rollen & Profilfelder',
                        'Page Editor & öffentliche Webseite',
                        'Neuigkeiten, Wiki, Quiz & Prüfungsbögen',
                        'Inventar, Inventur & Fundsachen',
                        'Boards, Abläufe & Checklisten',
                        'Föderation zwischen Wachen und Instanzen',
                        'Umzug einer ganzen Wache mit einem Code',
                        'Zwei-Faktor, Sitzungen & Rechtstexte',
                    ],
                },
                {
                    label: '◐ In Arbeit', accent: 'info',
                    items: [
                        'Geteiltes Wiki erweitern',
                        'Erweiterte Statistiken',
                        'Geolokalisierung',
                    ],
                },
                {
                    label: '○ Nächstes großes Ziel', accent: 'primary',
                    items: [],
                    note: 'Cluster-Verwaltung — mehrere Wachen unter einem Dach bequem verwalten, '
                        + 'gedacht für Kreise und Dachverbände mit einer geteilten Instanz.',
                },
            ],
            note: 'Eure Wünsche bestimmen die Reihenfolge — Open Source heißt: ihr redet mit.',
        },
        details: [],
    },
    {
        overview: {
            kind: 'cover', id: 'cta', accent: 'primary', tone: 'accent',
            heading: 'Eine Instanz. Alle Wachen.',
            headingAccent: 'Gemeinsam.',
            lead: 'Wir betreiben Ember bereits. Schließt euch an — Nachbarwachen, Kreis- und '
                + 'Dachverband, Verwaltung. Geteilte Infrastruktur, autonome Wachen.',
            pills: [],
            cards: [
                {icon: '01', title: 'Nachbarwachen', body: 'Eigene Wache anlegen — wir kümmern uns um Server und Updates.', accent: 'primary'},
                {icon: '02', title: 'Dachverband', body: 'Zahlen über alle Wachen, gemeinsames Wiki, ein Ansprechpartner.', accent: 'error'},
                {icon: '03', title: 'Verwaltung', body: 'Keine Lizenzkosten, DSGVO-konform, Daten bleiben in der Region.', accent: 'secondary'},
            ],
        },
        details: [],
    },
]

/**
 * The deck as a grid: the horizontal arrows walk the tracks, the vertical ones go into the detail
 * of the track they are on.
 */
export const PITCH_TRACKS: PitchTrack[] = [
    ...OPENING,
    ...FEDERATION,
    ...MODULE_TRACKS.map((track, index) => ({
        ...track,
        overview: track.overview.kind === 'split'
            ? {...track.overview, counter: `Modul ${index + 1} / ${MODULE_TRACKS.length}`}
            : track.overview,
    })),
    ...CLOSING,
]

/** The slides of one track, overview first. */
export function trackSlides(track: PitchTrack) {
    return [track.overview, ...track.details]
}
