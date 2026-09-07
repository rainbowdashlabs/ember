/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {CAMP_BOARD, TICKET_COMMENTS} from './pitchBoardData'
import {ATTENDANCE, ATTENDANCE_CHECK} from './pitchAttendanceData'
import {PROTOCOL_EVALUATION, PROTOCOL_GRADING} from './pitchProtocolData'
import {INVENTORY_CHECK, INVENTORY_RAPID, INVENTORY_STATS, INVENTORY_STORAGE} from './pitchInventoryData'
import {TRAINING_CATALOGS, TRAINING_SELECTION, WIKI_ITEMS} from './pitchWikiData'
import {PROCEDURE_INTAKE, PROCEDURE_TEMPLATE} from './pitchProcedureData'
import {NEWS_COMMENTS, NEWS_ITEMS, NEWS_SETTINGS} from './pitchNewsData'
import {FORM_ANALYTICS_DATA, FORM_FILL, FORMS} from './pitchFormsData'
import {WAITLIST} from './pitchWaitlistData'
import {PAGES} from './pitchPagesData'
import {NOTIFICATION_FEEDS, NOTIFICATION_SETTINGS} from './pitchNotificationData'
import type {PitchNews, PitchTrack} from './pitchTypes'

/**
 * One column per module: what it is, and below it the screens it actually offers. The previews are
 * drawn in the station shell with the application's own tables, badges and buttons, so a
 * demonstration can follow them one to one.
 *
 * How deep a column goes follows the weight of the module: the four screens behind the event
 * planning earn four slides, a lost-property list earns one.
 */
export const MODULE_TRACKS: PitchTrack[] = [
    {
        overview: {
            kind: 'split', id: 'events', accent: 'primary', chip: 'Dienstbetrieb',
            heading: 'Termine & Dienste',
            lead: 'Der Terminplan ist der Taktgeber. Anmeldung, Anwesenheit und Bericht hängen daran.',
            bullets: [
                'Wiederkehrende Übungen und einzelne Veranstaltungen in einem Plan',
                'An- und Abmeldung, auf Wunsch mit Bestätigung durch die Leitung',
                'Wer teilnehmen darf, entscheiden Rolle, Gruppe oder Tag',
                'Vorlagen tragen Kategorie, Zeiten und Fragen in jeden neuen Termin',
                'Kalender-Abo für Outlook, Google und Apple',
            ],
            screen: {
                title: 'Kommende Termine', subtitle: 'Was als Nächstes ansteht',
                toolbar: {create: 'Termin hinzufügen', views: ['Liste', 'Kalender']},
                filters: {search: 'Termin suchen…', select: {label: 'Kategorie', value: 'Alle Kategorien'},
                    toggle: 'Handlungsbedarf'},
                section: 'Heute',
                rows: [
                    {
                        name: 'Knotenkunde', meta: ['Dienstag, 18.06.', '18:00 – 19:30'],
                        badges: [{text: '14 zugesagt', tone: 'success'}, {text: '3 offen', tone: 'info'}],
                    },
                    {
                        name: 'Ausbildungsabend', locked: true, meta: ['Donnerstag, 20.06.', '18:00 – 20:00'],
                        fields: [{label: 'Ort', value: 'Halle 2'}],
                        badges: [{text: '9 zugesagt', tone: 'success'}],
                    },
                    {
                        name: 'Zeltlager', meta: ['12.07. – 14.07.', '(Anmeldung bis 20.06.)'],
                        badges: [{text: '17 angemeldet', tone: 'success'}, {text: '2 abgemeldet', tone: 'error'}],
                    },
                ],
                footer: 'Ein Schloss heißt: der Termin gilt nur für bestimmte Rollen oder Gruppen.',
            },
        },
        details: [
            {
                kind: 'showcase', id: 'events-registration', accent: 'primary', tone: 'accent',
                chip: 'Termine im Detail',
                heading: 'Anmeldung mit eigenen Fragen',
                lead: 'Größe, Begleitpersonen, Allergien: was ihr sonst per Nachricht einsammelt, fragt der Termin '
                    + 'selbst. Die Auswertung steht darüber, ohne dass jemand nachrechnet.',
                screens: [
                    {
                        title: 'Zeltlager', subtitle: '12.07. – 14.07. · Anmeldungen',
                        summary: [
                            {label: 'Shirtgröße', value: 'M ×7 · L ×6 · S ×4'},
                            {label: 'Begleitpersonen', value: '9'},
                            {label: 'Allergien angegeben', value: '3'},
                        ],
                        badges: [
                            {text: '17 zugesagt', tone: 'success'},
                            {text: '3 offen', tone: 'info'},
                            {text: '2 abgemeldet', tone: 'primary'},
                        ],
                        section: 'Offen',
                        table: {
                            columns: ['Mitglied', 'Punktzahl', 'Angenommen', 'Abgelehnt', 'Quote'],
                            rows: [
                                [{text: 'Anna Müller · Größe M, 1 Begleitperson', strong: true},
                                    {text: '82'}, {text: '11', badge: 'success'}, {text: '1', badge: 'error'},
                                    {text: '92 %'}],
                                [{text: 'Ben Krüger · Größe L, keine Begleitung', strong: true},
                                    {text: '64'}, {text: '7', badge: 'success'}, {text: '3', badge: 'error'},
                                    {text: '70 %'}],
                                [{text: 'Clara Weiß · Größe S, 2 Begleitpersonen', strong: true},
                                    {text: '95'}, {text: '14', badge: 'success'}, {text: '0', badge: 'error'},
                                    {text: '100 %'}],
                            ],
                        },
                        footer: 'Bei begrenzten Plätzen zeigt die Punktzahl, wer zuletzt zum Zug kam.',
                    },
                ],
                points: [
                    'Zahlenfragen werden summiert, Auswahlfragen je Option gezählt',
                    'Eine Frage nur für die Organisation sehen die Angemeldeten gar nicht',
                    'Die Antworten stehen unter dem Namen, nicht in einer zweiten Liste',
                    'Anmeldeschluss und Platzgrenze hängen am Termin, nicht an einer Absprache',
                ],
            },
            {
                kind: 'showcase', id: 'events-plan', accent: 'primary', tone: 'accent',
                chip: 'Termine im Detail',
                heading: 'Eine Vorlage, ein Jahr Dienstplan',
                lead: 'Eine Vorlage beschreibt einen Diensttyp. Daraus entstehen die Termine, mit allem, was daran hängt.',
                screens: [
                    {
                        title: 'Termin anlegen', subtitle: 'Aus Vorlage „Wöchentliche Übung"',
                        actions: ['Speichern'],
                        table: {
                            columns: ['Feld', 'Wert', 'Wirkung'],
                            rows: [
                                [{text: 'Wiederholung', strong: true}, {text: 'wöchentlich, dienstags'},
                                    {text: 'erzeugt Serie', badge: 'secondary'}],
                                [{text: 'Kategorie', strong: true}, {text: 'Übung'},
                                    {text: 'Farbe & Filter', badge: 'info'}],
                                [{text: 'Teilnahme', strong: true}, {text: 'Mitglieder, Gruppe 1 + 2'},
                                    {text: 'eingeschränkt', badge: 'error'}],
                                [{text: 'Anmeldung', strong: true}, {text: 'erforderlich, bis 2 Tage vorher'},
                                    {text: 'mit Erinnerung', badge: 'success'}],
                                [{text: 'Bestätigung', strong: true}, {text: 'durch die Leitung'},
                                    {text: 'Plätze begrenzt', badge: 'primary'}],
                            ],
                        },
                        footer: 'Die Vorlage bringt ihre Anmeldefragen mit, jeder Termin daraus fragt dasselbe.',
                    },
                ],
                points: [
                    'Einzelne Termine einer Serie lassen sich abweichen, ohne die Vorlage zu ändern',
                    'Ein eingeschränkter Termin ist für Unberechtigte gar nicht sichtbar',
                    'Erinnerungen gehen automatisch an alle, die zugesagt haben',
                ],
            },
            {
                kind: 'showcase', id: 'events-handover', accent: 'primary', tone: 'accent',
                chip: 'Termine im Detail',
                heading: 'Vom Termin in den Jahresbericht',
                lead: 'Wer zugesagt hat, steht am Abend schon in der Anwesenheitsliste. Von dort läuft es weiter.',
                screens: [
                    {
                        title: 'Anwesenheit', subtitle: 'Knotenkunde · 18.06.',
                        actions: ['Abschließen'],
                        badges: [
                            {text: '20 anwesend', tone: 'success'},
                            {text: '3 entschuldigt', tone: 'info'},
                            {text: '1 unentschuldigt', tone: 'error'},
                        ],
                        table: {
                            columns: ['Mitglied', 'Status', 'Von', 'Bis'],
                            rows: [
                                [{text: 'Anna Müller', strong: true}, {text: 'anwesend', badge: 'success'},
                                    {text: '18:02'}, {text: '19:45'}],
                                [{text: 'Ben Krüger', strong: true}, {text: 'entschuldigt', badge: 'info'},
                                    {text: '-', muted: true}, {text: '-', muted: true}],
                                [{text: 'Clara Weiß', strong: true}, {text: 'unentschuldigt', badge: 'error'},
                                    {text: '-', muted: true}, {text: '-', muted: true}],
                            ],
                        },
                        footer: 'Abmeldungen aus der Anmeldung stehen hier schon als entschuldigt.',
                    },
                ],
                points: [
                    'Der Jahresbericht entsteht daraus ohne weiteres Zutun',
                    'Ein Kalender-Abo hält Sorgeberechtigte auf dem Laufenden, ganz ohne Zugang',
                ],
            },
        ],
    },
    {
        overview: {
            kind: 'split', id: 'members', accent: 'secondary', chip: 'Wer dabei ist',
            heading: 'Mitglieder & Rollen',
            lead: 'Ein Ort für Stammdaten, Zugehörigkeit und alles, was daran hängt.',
            bullets: [
                'Gruppen tragen Berechtigungen, Tags sind Etiketten; beides ordnet die Mitgliedschaft',
                'Profilfelder legt ihr selbst fest: je Rolle, je Gruppe oder gar nicht',
                'Sorgeberechtigte verwalten das Profil ihres Kindes mit eigenem Zugang',
                'Änderungen an heiklen Feldern gehen erst nach Bestätigung durch',
                'Ausgetretene werden archiviert; die Historie der Wache bleibt lesbar',
            ],
            screen: {
                title: 'Mitglieder', subtitle: '34 aktiv · 6 ehemalig',
                tabs: ['Aktiv', 'Ehemalige', 'Änderungen'],
                filters: {search: 'Mitglied suchen…', select: {label: 'Spalten', value: 'Größe, Gruppe'}},
                table: {
                    actions: true,
                    columns: ['Name', 'Rolle', 'Gruppen', 'Tags', 'Größe'],
                    rows: [
                        [{text: 'Anna Müller', avatar: true, strong: true}, {text: 'Mitglied', badge: 'secondary'},
                            {text: 'Gruppe 2'}, {text: 'Abzeichen Stufe I', badge: 'info'}, {text: '152'}],
                        [{text: 'Ben Krüger', avatar: true, strong: true,
                            note: {text: 'unvollständig', tone: 'error'}}, {text: 'Mitglied', badge: 'secondary'},
                            {text: 'Gruppe 1'}, {text: 'Fahrdienst', badge: 'info'}, {text: '164'}],
                        [{text: 'Clara Weiß', avatar: true, strong: true}, {text: 'Probe', badge: 'info'},
                            {text: '-', muted: true}, {text: 'Neu dabei', badge: 'info'}, {text: '140'}],
                    ],
                },
                footer: 'Welche Profilfelder als Spalte erscheinen, wählt ihr euch zusammen.',
            },
        },
        details: [
            {
                kind: 'showcase', id: 'members-groups', accent: 'secondary', tone: 'accent',
                chip: 'Mitglieder im Detail',
                heading: 'Gruppen und Tags sind nicht dasselbe',
                lead: 'Beide fassen Mitglieder zusammen. Nur eines davon vergibt Rechte. Genau das '
                    + 'entscheidet, welches ihr nehmt.',
                screens: [
                    {
                        title: 'Gruppen', subtitle: 'Zugehörigkeit mit Berechtigungen',
                        toolbar: {create: 'Neue Gruppe'},
                        tabs: ['Mitglieder', 'Berechtigungen'],
                        table: {
                            columns: ['Gruppe', 'Mitglieder', 'Berechtigungen', 'Verwendet für'],
                            rows: [
                                [{text: 'Gruppe 1', strong: true}, {text: '12'},
                                    {text: 'keine', muted: true}, {text: 'Termine, Wissensordner'}],
                                [{text: 'Ausbilder', strong: true}, {text: '4'},
                                    {text: 'Ausbildung verwalten', badge: 'primary'}, {text: 'Prüfungsbögen, Kataloge'}],
                                [{text: 'Leitung', strong: true}, {text: '3'},
                                    {text: 'Mitglieder, Termine', badge: 'primary'}, {text: 'alles'}],
                            ],
                        },
                        footer: 'Eine Gruppe kann eigene Profilfelder haben, nur ihre Mitglieder füllen sie aus.',
                    },
                    {
                        title: 'Tags', subtitle: 'Etikett ohne Berechtigung',
                        toolbar: {create: 'Tag erstellen'},
                        table: {
                            columns: ['Tag', 'Mitglieder', 'Sichtbar', 'Verwendet für'],
                            rows: [
                                [{text: 'Abzeichen Stufe I', strong: true}, {text: '9'},
                                    {text: 'am Namen', badge: 'success'}, {text: 'filtern, einschränken'}],
                                [{text: 'Fahrdienst', strong: true}, {text: '5'},
                                    {text: 'am Namen', badge: 'success'}, {text: 'Termine einschränken'}],
                                [{text: 'Neu dabei', strong: true}, {text: '6'},
                                    {text: 'intern', badge: 'info'}, {text: 'Listen filtern'}],
                            ],
                        },
                        footer: 'Eine Gruppe lässt sich in ein Tag umwandeln, die Mitglieder bleiben, die Rechte fallen weg.',
                    },
                ],
                points: [
                    'Gruppe: Zugehörigkeit, Rechte, eigene Felder, Zielgruppe für Termine und Inhalte',
                    'Tag: Etikett neben dem Namen, zum Filtern und Einschränken, ohne jedes Recht',
                ],
            },
            {
                kind: 'showcase', id: 'members-fields', accent: 'secondary', tone: 'accent',
                chip: 'Mitglieder im Detail',
                heading: 'Welche Felder es gibt, entscheidet die Wache',
                lead: 'Kein festes Formular: ihr legt an, was ihr braucht, und bestimmt, wer es sehen und ändern darf.',
                screens: [
                    {
                        title: 'Mitglieder-Konfiguration', subtitle: 'Profilfelder',
                        tabs: ['Mitglieder', 'Sorgeberechtigte', 'Team', 'Leitung'], actions: ['Feld hinzufügen'],
                        table: {
                            columns: ['Feld', 'Typ', 'Pflicht', 'Verhalten'],
                            rows: [
                                [{text: 'Geburtsdatum', strong: true}, {text: 'Geburtsdatum', badge: 'primary'},
                                    {text: 'ja'}, {text: 'nur lesbar', badge: 'info'}],
                                [{text: 'Notfallkontakt', strong: true}, {text: 'Text'},
                                    {text: 'ja'}, {text: 'bei Änderung melden', badge: 'error'}],
                                [{text: 'Kleidergröße', strong: true}, {text: 'Auswahl'},
                                    {text: 'nein'}, {text: 'in Übersicht', badge: 'secondary'}],
                                [{text: 'Alter', strong: true}, {text: 'berechnet'},
                                    {text: '-', muted: true}, {text: 'aus Geburtsdatum', badge: 'success'}],
                            ],
                        },
                        footer: 'Den Feldtyp Geburtsdatum gibt es genau einmal je Wache, daran hängt das berechnete Alter.',
                    },
                ],
                points: [
                    'Felder je Rolle: Sorgeberechtigte sehen andere als das Team',
                    'Nur lesbare Felder ändert die Leitung, nicht das Mitglied',
                    'Was in der Übersicht erscheint, entscheidet ihr je Feld',
                ],
            },
            {
                kind: 'showcase', id: 'members-changes', accent: 'secondary', tone: 'accent',
                chip: 'Mitglieder im Detail',
                heading: 'Wer hat was geändert?',
                lead: 'Ein Mitglied pflegt seine Daten selbst. Was die Wache wissen muss, wird ihr vorgelegt.',
                screens: [
                    {
                        title: 'Mitglieder', subtitle: 'Offene Änderungen · 3',
                        tabs: ['Offen', 'Bestätigt'],
                        table: {
                            columns: ['Mitglied', 'Feld', 'Vorher', 'Nachher', ''],
                            rows: [
                                [{text: 'Anna Müller', strong: true}, {text: 'Telefonnummer'},
                                    {text: '0170 111…', muted: true}, {text: '0170 222…'},
                                    {text: 'zu bestätigen', badge: 'error'}],
                                [{text: 'Ben Krüger', strong: true}, {text: 'Notfallkontakt'},
                                    {text: 'Vater', muted: true}, {text: 'Mutter'},
                                    {text: 'zu bestätigen', badge: 'error'}],
                                [{text: 'Clara Weiß', strong: true}, {text: 'Kleidergröße'},
                                    {text: '140', muted: true}, {text: '146'},
                                    {text: 'bestätigt', badge: 'success'}],
                            ],
                        },
                        footer: 'Jede Änderung bleibt mit Bearbeiter und Zeitpunkt in der Historie.',
                    },
                ],
                points: [
                    'Nur Felder, die es verlangen, brauchen eine Bestätigung',
                    'Die Leitung wird benachrichtigt, sobald etwas ansteht',
                    'Datenauskunft je Mitglied liefert die Historie gleich mit',
                ],
            },
        ],
    },
    {
        overview: {
            kind: 'split', id: 'attendance', accent: 'success', chip: 'Dienstbetrieb',
            heading: 'Anwesenheit',
            lead: 'Eine Liste je Dienst, ein Tipp je Mitglied und am Jahresende ein Bericht, '
                + 'den niemand abtippen muss.',
            bullets: [
                'Vorlagen legen fest, welche Gruppen erfasst werden und welche Felder mitlaufen',
                'Aus einem Termin des Tages entsteht die Liste mit einem Klick',
                'Vier Zustände je Mitglied: offen, anwesend, abwesend, abgesagt',
                'Kommen und Gehen mit Uhrzeit, wo Stunden gebraucht werden',
                'Ein Prüfmodus geht die Liste Person für Person durch',
                'Berichte über Zeitraum, Gruppen und Mitgliedstypen, fertig als PDF',
            ],
            screen: {
                title: 'Vergangene Anwesenheiten', subtitle: 'Anwesenheiten einsehen und verwalten',
                rows: [
                    {
                        name: 'Dienstabend', plain: true, meta: ['18.06.2026', '18:00 – 19:30'],
                        badges: [{text: '18 Anwesend', tone: 'success'}, {text: '2 Abwesend', tone: 'error'},
                            {text: '1 Abgesagt', tone: 'info'}],
                    },
                    {
                        name: 'Übung Löschangriff', plain: true, meta: ['15.06.2026', '17:00 – 19:00'],
                        badges: [{text: '16 Anwesend', tone: 'success'}, {text: '4 Abwesend', tone: 'error'},
                            {text: '1 Abgesagt', tone: 'info'}],
                    },
                    {
                        name: 'Gerätekunde', plain: true, meta: ['11.06.2026', '18:00 – 19:30'],
                        badges: [{text: '15 Anwesend', tone: 'success'}, {text: '3 Abwesend', tone: 'error'},
                            {text: '2 Offen', tone: 'secondary'}],
                    },
                ],
                footer: 'Jede Liste bleibt offen für Nachträge, auch Tage später.',
            },
        },
        details: [
            {
                kind: 'showcase', id: 'attendance-session', accent: 'success', tone: 'accent',
                chip: 'Anwesenheit im Detail',
                heading: 'Aus dem Termin wird die Liste',
                lead: 'Wer heute einen Dienst hat, findet ihn beim Anlegen schon vor. Die Vorlage bringt Gruppen '
                    + 'und Felder mit, die Zusagen aus dem Termin kommen auf Knopfdruck dazu.',
                screens: [
                    {
                        title: 'Neue Anwesenheit', subtitle: 'Anwesenheit erfassen',
                        section: 'Heutige Termine',
                        rows: [
                            {
                                name: 'Dienstabend', plain: true, highlight: true, meta: ['18:00 – 19:30'],
                                description: 'Knoten und Stiche', fields: [{label: 'Vorlage', value: 'Dienstabend'}],
                            },
                            {
                                name: 'Ausbildung Atemschutz', plain: true, highlight: true, meta: ['19:45 – 21:00'],
                                fields: [{label: 'Vorlage', value: 'Ausbildung'}],
                            },
                        ],
                        footer: 'Ohne passenden Termin startet die Liste aus einer Vorlage: Dienstabend, '
                            + 'Ausbildung, Jugendraum.',
                    },
                    {
                        title: 'Anwesenheit', subtitle: 'Dienstabend · 18.06.2026 · 18:00 – 19:30',
                        actions: ['Anwesenheit prüfen (2)'],
                        attendance: ATTENDANCE,
                        footer: 'Über der Liste stehen die Felder der Vorlage: Thema, Ausbilder, Fahrdienst.',
                    },
                ],
                points: [
                    '„Synchronisieren“ im Aktionsmenü holt die An- und Abmeldungen des Termins in die Liste',
                    'Wer als abwesend gemeldet ist, wird dabei nicht versehentlich als anwesend geführt',
                    'Ein Feld vom Typ Mitgliederliste trägt seine Leute direkt als anwesend ein',
                ],
            },
            {
                kind: 'showcase', id: 'attendance-check', accent: 'success',
                chip: 'Anwesenheit im Detail',
                heading: 'Prüfen, einer nach dem anderen',
                lead: 'Am Dienstabend steht man mit dem Handy im Raum. Der Prüfmodus zeigt einen Namen, drei '
                    + 'große Knöpfe, und springt dann weiter.',
                screens: [
                    {
                        title: 'Anwesenheit', subtitle: 'Dienstabend · offene Einträge',
                        check: ATTENDANCE_CHECK,
                        footer: 'Am Ende steht „Alle Einträge geprüft!“ und der Weg zurück in die Liste.',
                    },
                ],
                points: [
                    'Nur offene Einträge landen im Prüfmodus; abgehakte werden nicht noch einmal gefragt',
                    'Überspringen ist erlaubt, der Eintrag kommt am Ende wieder',
                    'Die Knöpfe sind fürs Handy gebaut: eine Hand, ein Daumen, kein Zielen',
                ],
            },
            {
                kind: 'showcase', id: 'attendance-report', accent: 'success', tone: 'accent',
                chip: 'Anwesenheit im Detail',
                heading: 'Der Bericht am Jahresende',
                lead: 'Zeitraum, Gruppen und Mitgliedstypen wählen, Vorschau ansehen, PDF ziehen. Wiederkehrende '
                    + 'Auswertungen lassen sich als Filtervorlage speichern.',
                screens: [
                    {
                        title: 'Anwesenheitsbericht', subtitle: 'Zeiten auswerten und exportieren',
                        tabs: ['Jahresbericht Jugend', 'Aktive · Quartal'],
                        summary: [
                            {label: 'Zeitraum', value: 'Jahr 2026'},
                            {label: 'Gruppen', value: 'Löschgruppe, Anwärter'},
                            {label: 'Rundung', value: 'Gerundet (0.5h)'},
                        ],
                        actions: ['PDF exportieren'],
                        section: 'Gesamtübersicht – Jahr 2026',
                        table: {
                            columns: ['Name', 'Termine', 'Anwesend', 'Stunden'],
                            rows: [
                                [{text: 'Anna Müller'}, {text: '36'}, {text: '32'}, {text: '54.5'}],
                                [{text: 'Ben Krüger'}, {text: '36'}, {text: '21'}, {text: '35.0'}],
                                [{text: 'Clara Weiß'}, {text: '12'}, {text: '11'}, {text: '18.5'}],
                            ],
                        },
                        footer: 'Die Filter dieser Ansicht lassen sich als Vorlage sichern und oben wieder aufrufen.',
                    },
                    {
                        title: 'Anwesenheitsbericht', subtitle: 'Monatsbericht Juni 2026',
                        section: 'Dienstabend · 18.06.2026 · 18:00 – 19:30',
                        hint: 'Anwesend: 18 von 21 erwartet',
                        table: {
                            columns: ['Name', 'Von', 'Bis', 'Stunden'],
                            rows: [
                                [{text: 'Anna Müller'}, {text: '18:00'}, {text: '19:30'}, {text: '1.5'}],
                                [{text: 'Ben Krüger'}, {text: '18:15'}, {text: '19:30'}, {text: '1.5'}],
                                [{text: 'Clara Weiß'}, {text: '18:00'}, {text: '19:00'}, {text: '1.0'}],
                            ],
                        },
                        footer: 'Bei mehreren Monaten steht über jedem Block seine eigene Monatsübersicht.',
                    },
                ],
                points: [
                    'Die Rundung entscheidet, ob exakt, auf halbe oder auf volle Stunden gezählt wird',
                    'Jahr, Monat oder Kalenderwoche, der Bericht folgt dem gewählten Zeitraum',
                    'Das PDF geht so an den Träger, wie es auf dem Schirm steht',
                ],
            },
        ],
    },
    {
        overview: {
            kind: 'split', id: 'knowledge', accent: 'info', chip: 'Wissen',
            wide: true,
            heading: 'Das Wiki',
            lead: 'Ordner und Dateien wie auf einem Laufwerk, nur mit Rechten, Versionen und Suche.',
            bullets: [
                'Ordner, Markdown-Seiten, Dateien, Präsentationen, Links und Videos nebeneinander',
                'Präsentationen laufen im Vollbild direkt aus dem Wiki, ohne Zusatzsoftware',
                'Word, OpenDocument, RTF, HTML, EPUB und LaTeX werden beim Hochladen zu Markdown',
                'Jede Änderung wird versioniert; ältere Fassungen lassen sich zurückholen',
                'Lesen, Bearbeiten oder Vollzugriff je Eintrag, für Rollen, Gruppen und Tags',
                'Schlagwörter, Favoriten und eine Suche, die auch Partnerwachen erreicht',
                'Einzelne Ordner öffentlich stellen, dann liest sie jeder ohne Zugang',
            ],
            screen: {
                title: 'Wiki', subtitle: 'Dateien und Ordner durchsuchen',
                filters: {search: 'Suchen…', select: {label: 'Schlagwort', value: 'Alle Schlagwörter'},
                    toggle: 'Partnerwachen'},
                breadcrumb: ['Wiki', 'Ausbildung'],
                toolbar: {create: 'Neu', views: ['Kacheln', 'Liste']},
                tiles: WIKI_ITEMS,
                footer: 'Ein Stern markiert Favoriten, ein Schloss eine Zugriffsbeschränkung. '
                    + '„Nur lesen" steht nur dort, wo jemand sonst bearbeiten dürfte, sonst wäre es überall zu lesen.',
            },
        },
        details: [
            {
                kind: 'showcase', id: 'knowledge-entry', accent: 'info', tone: 'accent',
                chip: 'Wiki im Detail',
                heading: 'Jede Seite kennt ihre Geschichte',
                lead: 'Schreiben im Browser, Vorschau daneben, jede Fassung aufgehoben, dazu ein Rückweg, '
                    + 'wenn jemand zu viel gelöscht hat.',
                screens: [
                    {
                        title: 'Gerätekunde', subtitle: 'Wiki · Ausbildung',
                        tabs: ['Inhalt', 'Vorschau', 'Versionen', 'Eigenschaften'],
                        actions: ['Speichern'],
                        table: {
                            columns: ['Version', 'Geändert von', 'Wann', ''],
                            rows: [
                                [{text: 'v7', strong: true}, {text: 'Clara Weiß'}, {text: 'heute, 14:20'},
                                    {text: 'aktuell', badge: 'success'}],
                                [{text: 'v6', strong: true}, {text: 'Ben Krüger'}, {text: 'vorletzte Woche'},
                                    {text: 'zurücksetzen', badge: 'secondary'}],
                                [{text: 'v5', strong: true}, {text: 'Clara Weiß'}, {text: 'im Mai'},
                                    {text: 'zurücksetzen', badge: 'secondary'}],
                            ],
                        },
                        footer: 'Word-, OpenDocument-, RTF- und HTML-Dateien landen beim Import direkt als Markdown hier.',
                    },
                ],
                points: [
                    'Kommentare unter der Seite, für Rückfragen an die, die sie geschrieben haben',
                    'Als PDF mit Namen und Logo der Wache herunterladen',
                    'Videos und externe Verweise stehen als eigene Einträge neben den Dateien',
                ],
            },
            {
                kind: 'showcase', id: 'knowledge-rights', accent: 'info', tone: 'accent',
                chip: 'Wiki im Detail',
                heading: 'Rechte sieht man am Eintrag',
                lead: 'Die Oberfläche bietet nur an, was euer Recht hergibt. Und sie sagt, woher das Recht kommt.',
                screens: [
                    {
                        title: 'Prüfungsfragen', subtitle: 'Zugriffsbeschränkung',
                        actions: ['Speichern'],
                        table: {
                            columns: ['Wer', 'Stufe', 'Was das heißt'],
                            rows: [
                                [{text: 'Gruppe Ausbilder', strong: true},
                                    {text: 'Vollzugriff', badge: 'success'},
                                    {text: 'auch löschen und freigeben', muted: true}],
                                [{text: 'Rolle Team', strong: true},
                                    {text: 'Lesen und bearbeiten', badge: 'primary'},
                                    {text: 'Inhalte pflegen', muted: true}],
                                [{text: 'Tag Neu dabei', strong: true},
                                    {text: 'Nur lesen', badge: 'info'},
                                    {text: 'öffnen, herunterladen', muted: true}],
                                [{text: 'Rolle Mitglied', strong: true},
                                    {text: 'Wie bisher', badge: 'secondary'},
                                    {text: 'es gilt die stationsweite Berechtigung', muted: true}],
                            ],
                        },
                        footer: 'Wer hier gar nicht steht, sieht den Ordner nicht.',
                    },
                ],
                points: [
                    'Ohne eigenen Eintrag gilt weiter, was die stationsweite Berechtigung sagt',
                    'Eine Datei, die ihr nur lesen dürft, nennt den Ordner, der das entschieden hat',
                    'Ein öffentlich gestellter Ordner erscheint als Wiki auf der Webseite der Wache',
                ],
            },
            {
                kind: 'showcase', id: 'knowledge-present', accent: 'info', tone: 'accent',
                chip: 'Wiki im Detail',
                heading: 'Präsentieren direkt aus dem Wiki',
                lead: 'Die Folien für den Ausbildungsabend liegen im Wiki und laufen von dort im Vollbild '
                    + 'vorgeführt. Ohne Stick, ohne Laptop mit passendem Office.',
                screens: [
                    {
                        title: 'Grundlagen der Gerätekunde', subtitle: 'Wiki · Präsentation',
                        actions: ['Präsentieren'],
                        table: {
                            columns: ['Eigenschaft', 'Wert', ''],
                            rows: [
                                [{text: 'Typ', strong: true}, {text: 'Präsentation'},
                                    {text: 'PowerPoint', badge: 'secondary'}],
                                [{text: 'Vorführung', strong: true}, {text: 'Vollbild im Browser'},
                                    {text: 'ohne Zusatzsoftware', badge: 'success'}],
                                [{text: 'Steuerung', strong: true}, {text: 'Klicken, Wischen, Pfeiltasten'},
                                    {text: 'auch am Tablet', badge: 'info'}],
                                [{text: 'Zugriff', strong: true}, {text: 'wer den Ordner lesen darf'},
                                    {text: 'kein Versand nötig', badge: 'primary'}],
                            ],
                        },
                        footer: 'Die Bedienelemente blenden sich aus, sobald die Maus stillsteht, es bleibt die Folie.',
                    },
                ],
                points: [
                    'Hochgeladene Präsentationen werden auf dem Server umgewandelt und Folie für Folie gezeigt',
                    'Jeder Ausbilder greift auf dieselbe, aktuelle Fassung zu, samt Versionshistorie',
                ],
            },
            {
                kind: 'showcase', id: 'knowledge-shared', accent: 'info', tone: 'accent',
                chip: 'Wiki im Detail',
                heading: 'Einmal schreiben, im Verbund nutzen',
                lead: 'Was eine Wache erarbeitet hat, muss die Nachbarwache nicht noch einmal schreiben.',
                screens: [
                    {
                        title: 'Wiki', subtitle: 'Suche · „Schlauch"',
                        filters: {search: 'Schlauch', select: {label: 'Wache', value: 'Alle Partnerwachen'}},
                        rows: [
                            {
                                name: 'Schlauchpflege', meta: ['Ausbildung', 'Markdown'],
                                badges: [{text: 'eigene Wache', tone: 'secondary'}],
                            },
                            {
                                name: 'Knotentafel', meta: ['Grundlagen', 'PDF'],
                                badges: [{text: 'Talbach', tone: 'info'}],
                            },
                            {
                                name: 'Ablaufplan Übung', meta: ['Übungen', 'Markdown'],
                                badges: [{text: 'Kreis Musterland', tone: 'info'}],
                            },
                        ],
                        footer: 'Eine geteilte Datei öffnet wie eine eigene: Text und Markdown auch mit Kommentaren.',
                    },
                ],
                points: [
                    'Die Freigabe entscheidet die Wache, die den Eintrag besitzt',
                    'Fremde Formate lassen sich in die eigene Wache kopieren',
                    'Beide Seiten brauchen dafür eine zueinander passende Version',
                ],
            },
        ],
    },
    {
        overview: {
            kind: 'split', id: 'quiz', accent: 'success', chip: 'Ausbildung',
            heading: 'Quiz',
            lead: 'Fragen einmal sammeln, dann damit üben lassen oder prüfen.',
            bullets: [
                'Kataloge sammeln die Fragen, Tests stellen daraus einen Bogen zusammen',
                'Acht Fragetypen: Multiple Choice, Wahr/Falsch, Lückentext, Freitext, '
                    + 'Zuordnung, Reihenfolge, Aufzählung, Bild & Text',
                'Ein Katalog fürs Training freigegeben: jeder übt für sich, ohne Bewertung',
                'Fragen per CSV importieren oder von einer KI vorschlagen lassen',
                'Kataloge mit Partnerwachen teilen und übernehmen',
            ],
            screen: {
                title: 'Quiz', subtitle: 'Fragenkataloge',
                toolbar: {create: 'Neuer Katalog'},
                filters: {search: 'Katalog suchen…'},
                rows: [
                    {
                        name: 'Grundlagen', meta: ['48 Fragen'],
                        badges: [{text: 'Training aktiv', tone: 'success'}],
                    },
                    {
                        name: 'Gerätekunde', meta: ['31 Fragen'],
                        badges: [{text: 'Training aktiv', tone: 'success'}],
                    },
                    {
                        name: 'Prüfungsfragen', meta: ['24 Fragen'],
                        badges: [{text: 'Talbach', tone: 'info'}],
                    },
                ],
                footer: 'Kataloge lassen sich exportieren und importieren, auch zwischen Wachen.',
            },
        },
        details: [
            {
                kind: 'showcase', id: 'quiz-types', accent: 'success', tone: 'accent',
                chip: 'Quiz im Detail',
                heading: 'Acht Fragetypen, nicht nur Ankreuzen',
                lead: 'Jeder Typ bringt seinen eigenen Editor mit und weiß, wie er sich selbst auswertet.',
                screens: [
                    {
                        title: 'Grundlagen', subtitle: 'Katalog · 48 Fragen',
                        toolbar: {create: 'Frage anlegen'},
                        table: {
                            actions: true,
                            columns: ['Frage', 'Typ', 'Punkte', 'Auswertung'],
                            rows: [
                                [{text: 'Welche Aussagen treffen zu?', strong: true},
                                    {text: 'Multiple Choice', badge: 'secondary'}, {text: '2'},
                                    {text: 'automatisch', badge: 'success'}],
                                [{text: 'Ein C-Schlauch ist ___ m lang.', strong: true},
                                    {text: 'Lückentext', badge: 'secondary'}, {text: '1'},
                                    {text: 'automatisch', badge: 'success'}],
                                [{text: 'Gerät zu Einsatzzweck', strong: true},
                                    {text: 'Zuordnung', badge: 'secondary'}, {text: '3'},
                                    {text: 'automatisch', badge: 'success'}],
                                [{text: 'Schritte in die richtige Reihenfolge', strong: true},
                                    {text: 'Reihenfolge', badge: 'secondary'}, {text: '3'},
                                    {text: 'automatisch', badge: 'success'}],
                                [{text: 'Beschreibe den Ablauf.', strong: true},
                                    {text: 'Freitext', badge: 'secondary'}, {text: '5'},
                                    {text: 'von Hand', badge: 'info'}],
                            ],
                        },
                        footer: 'Dazu kommen Wahr/Falsch, Aufzählung und Bild & Text.',
                    },
                ],
                points: [
                    'Falsche Antworten kann eine KI vorschlagen, freigegeben wird von euch',
                    'Ganze Kataloge lassen sich als CSV einlesen',
                    'Freitext bewertet ein Mensch, alles andere rechnet die Anwendung',
                ],
            },
            {
                kind: 'showcase', id: 'quiz-test', accent: 'success', tone: 'accent',
                chip: 'Quiz im Detail',
                heading: 'Vom Katalog zum Test',
                lead: 'Ein Test zieht seine Fragen aus den Katalogen, läuft in einem Zeitfenster und '
                    + 'sammelt die Abgaben ein.',
                screens: [
                    {
                        title: 'Quiz', subtitle: 'Tests',
                        toolbar: {create: 'Test anlegen'},
                        rows: [
                            {
                                name: 'Grundlagen 2026', meta: ['Start 20.06. 18:00', 'Ende 20.06. 19:00'],
                                badges: [{text: 'Aktiv', tone: 'success'}, {text: '18 Teilnehmer', tone: 'secondary'}],
                            },
                            {
                                name: 'Gerätekunde Herbst', meta: ['noch kein Zeitfenster'],
                                badges: [{text: 'Entwurf', tone: 'secondary'}],
                            },
                            {
                                name: 'Grundlagen 2025', meta: ['abgelaufen am 12.11.'],
                                badges: [{text: 'Geschlossen', tone: 'error'}, {text: '22 Teilnehmer', tone: 'secondary'}],
                            },
                        ],
                        footer: 'Ein Test ist Entwurf, aktiv oder geschlossen, wer abgegeben hat, sieht das an seiner Zeile.',
                    },
                ],
                points: [
                    'Der Bogen wird aus Abschnitten zusammengestellt, jede Frage mit ihren Punkten',
                    'Start- und Endzeit steuern, wann überhaupt teilgenommen werden kann',
                    'Wer abgegeben hat, kann nicht noch einmal antreten',
                ],
            },
            {
                kind: 'showcase', id: 'quiz-evaluate', accent: 'success', tone: 'accent',
                chip: 'Quiz im Detail',
                heading: 'Wo ein Mensch bewerten muss',
                lead: 'Was sich rechnen lässt, ist schon gerechnet. Übrig bleiben die Freitexte, mit '
                    + 'Musterlösung daneben.',
                screens: [
                    {
                        title: 'Bewertung', subtitle: 'Grundlagen 2026 · Anna Müller',
                        summary: [
                            {label: 'Gesamt', value: '17 von 20 Punkten'},
                            {label: 'Offen zu bewerten', value: '1 Frage'},
                        ],
                        actions: ['Bewertung abschließen'],
                        table: {
                            columns: ['Frage', 'Antwort des Teilnehmers', 'Punkte', 'Status'],
                            rows: [
                                [{text: 'Welche Aussagen treffen zu?', strong: true},
                                    {text: 'A, C'}, {text: '2 / 2'}, {text: 'richtig', badge: 'success'}],
                                [{text: 'Ein C-Schlauch ist ___ m lang.', strong: true},
                                    {text: '15'}, {text: '0 / 1'}, {text: 'falsch', badge: 'error'}],
                                [{text: 'Beschreibe den Ablauf.', strong: true},
                                    {text: 'Erst absichern, dann …'}, {text: '- / 5'},
                                    {text: 'zu bewerten', badge: 'info'}],
                            ],
                        },
                        footer: 'Bei Freitext steht die Beispielantwort daneben, damit alle gleich bewerten.',
                    },
                ],
                points: [
                    'Wer bewertet hat, bleibt am Ergebnis vermerkt',
                    'Punkte lassen sich je Frage von Hand setzen',
                    'Erst nach dem Abschließen sieht die geprüfte Person ihr Ergebnis',
                ],
            },
            {
                kind: 'showcase', id: 'quiz-training', accent: 'success', tone: 'accent',
                chip: 'Quiz im Detail',
                heading: 'Üben ohne Note',
                lead: 'Wer üben will, sucht sich Kataloge aus und arbeitet sie durch. Es gibt kein Zeitfenster, '
                    + 'keine Abgabe und kein Ergebnis, das irgendwo landet.',
                screens: [
                    {
                        title: 'Training', subtitle: 'Fragen zum Üben',
                        hint: 'Wähle Kataloge zum Üben',
                        catalogs: {items: TRAINING_CATALOGS, selected: TRAINING_SELECTION},
                        actions: ['Training starten'],
                        footer: 'Angeboten wird nur, was die Wache für das Training freigegeben hat.',
                    },
                    {
                        title: 'Training', subtitle: 'Laufende Übung',
                        progress: {label: 'Frage 7 von 79', value: '5 von 6 richtig', percent: 9},
                        table: {
                            columns: ['Auf der Karte', 'Inhalt'],
                            rows: [
                                [{text: 'Frage', strong: true}, {text: 'Welche Aussagen treffen zu?'}],
                                [{text: 'Antwortfeld', strong: true},
                                    {text: 'passend zum Fragetyp, ankreuzen, sortieren, zuordnen, tippen'}],
                                [{text: 'Antwort zeigen', strong: true},
                                    {text: 'richtige Lösung, bei Freitext die Beispielantwort'}],
                            ],
                        },
                        footer: 'Am Ende: „Alle Fragen durchgearbeitet!" und ein Knopf zum erneuten Starten.',
                    },
                ],
                points: [
                    'Die Wache entscheidet je Katalog, ob er fürs Training offensteht',
                    'Auch Zuordnung und Reihenfolge zeigen ihre richtige Lösung',
                    'Geübt wird ohne Spur: Ergebnisse entstehen nur in einem Test',
                ],
            },
        ],
    },
    {
        overview: {
            kind: 'split', id: 'protocol', accent: 'info', chip: 'Ausbildung',
            heading: 'Prüfungsbögen',
            lead: 'Eine Abnahme läuft am Bogen: Abschnitte, Punkte je Prüfpunkt, mehrere Prüfer '
                + 'gleichzeitig, am Ende eine Tabelle über den ganzen Jahrgang.',
            bullets: [
                'Bögen mit Abschnitten, Unterabschnitten und Punkten je Prüfpunkt',
                'Bestehensgrenze in Punkten, an der die Auswertung gemessen wird',
                'Ein Prüfungslauf wählt die Prüflinge über Rolle, Gruppe, Tag oder namentlich',
                'Mehrere Prüfer arbeiten gleichzeitig, jeder an seinem Prüfling',
                'Bögen von Partnerwachen übernehmen statt sie neu zu tippen',
            ],
            screen: {
                title: 'Prüfungsbögen', subtitle: 'Prüfungsbögen verwalten',
                actions: ['Neuer Prüfungsbogen'],
                filters: {search: 'Prüfungsbögen durchsuchen…',
                    select: {label: 'Partnerwache', value: 'Alle Partnerwachen'}},
                tabs: ['Geteilt'],
                rows: [
                    {
                        name: 'Jugendflamme Stufe 1', plain: true,
                        description: 'Abnahme in vier Abschnitten', trailing: 'Grenze: 24P',
                    },
                    {
                        name: 'Jugendflamme Stufe 2', plain: true,
                        description: 'Abnahme in fünf Abschnitten', trailing: 'Grenze: 34P',
                    },
                    {
                        name: 'Grundausbildung Abschluss', plain: true, station: 'Talbach',
                        description: 'Von der Partnerwache geteilt - mit einem Klick übernehmen',
                    },
                ],
                footer: 'Ein Bogen wird einmal gebaut und für jeden Jahrgang neu gestartet.',
            },
        },
        details: [
            {
                kind: 'showcase', id: 'protocol-run', accent: 'info', tone: 'accent',
                chip: 'Prüfungsbögen im Detail',
                heading: 'Ein Lauf für den ganzen Jahrgang',
                lead: 'Der Prüfungslauf hält Datum, Prüflinge und Stand zusammen. Wer prüft, sieht auf einen Blick, '
                    + 'wer noch fehlt.',
                screens: [
                    {
                        title: 'Prüfungen', subtitle: 'Absolvierte Prüfungen',
                        actions: ['Neuer Prüfungslauf'],
                        rows: [
                            {
                                name: 'Jugendflamme 1 - Frühjahr', plain: true,
                                description: 'Jugendflamme Stufe 1 - 18.04.2026',
                                badges: [{text: 'Offen', tone: 'primary'}],
                            },
                            {
                                name: 'Jugendflamme 1 - Herbst', plain: true,
                                description: 'Jugendflamme Stufe 1 - 27.09.2025',
                                badges: [{text: 'Abgeschlossen', tone: 'success'}],
                            },
                            {
                                name: 'Grundausbildung 2025', plain: true,
                                description: 'Grundausbildung Abschluss - 14.06.2025',
                                badges: [{text: 'Abgeschlossen', tone: 'success'}],
                            },
                        ],
                        footer: 'Beim Anlegen wählt man Bogen, Datum und Prüflinge, über Rolle, Gruppe und Tag '
                            + 'oder namentlich.',
                    },
                    {
                        title: 'Prüfung', subtitle: 'Jugendflamme 1 - Frühjahr · 18.04.2026',
                        actions: ['Prüfung abschließen', 'Auswertung'],
                        badges: [{text: 'Offen', tone: 'primary'}],
                        filters: {toggle: 'Nur unvollständige'},
                        rows: [
                            {
                                name: 'Anna Müller', plain: true, description: '4/4 Abschnitte',
                                score: '29P', badges: [{text: 'Abgeschlossen', tone: 'success'}],
                            },
                            {
                                name: 'Ben Krüger', plain: true, locked: true,
                                description: '2/4 Abschnitte · in Prüfung bei Clara Weiß',
                                score: '15P', badges: [{text: 'Gesperrt', tone: 'error'}],
                            },
                            {
                                name: 'Jonas Behr', plain: true, description: '0/4 Abschnitte',
                                score: '0P', badges: [{text: 'Ausstehend', tone: 'secondary'}],
                                action: 'Prüfen',
                            },
                        ],
                        footer: 'Solange ein Prüfer einen Bogen offen hat, ist der Prüfling für die anderen gesperrt.',
                    },
                ],
                points: [
                    'Zwei Prüfer an zwei Stationen arbeiten im selben Lauf, ohne sich in die Quere zu kommen',
                    'Der Filter „Nur unvollständige“ zeigt, wer noch fehlt',
                    'Ein abgeschlossener Lauf bleibt als Nachweis erhalten',
                    'Der Bogen selbst bleibt unangetastet; der nächste Jahrgang startet ihn erneut',
                ],
            },
            {
                kind: 'showcase', id: 'protocol-grading', accent: 'info',
                chip: 'Prüfungsbögen im Detail',
                heading: 'Abnahme, Abschnitt für Abschnitt',
                lead: 'Der Prüfer hat das Gerät in der Hand und hakt ab, was er sieht. Punkte und Zwischenstand '
                    + 'stehen sofort, ohne Zettel und ohne Nachrechnen.',
                screens: [
                    {
                        title: 'Bewertung', subtitle: 'Anna Müller · Jugendflamme Stufe 1',
                        tabs: [
                            {label: 'Knoten und Stiche', score: '8/8', done: true},
                            {label: 'Erste Hilfe', score: '6/8', done: true},
                            {label: 'Gerätekunde', score: '5/10', selected: true},
                            {label: 'Fragen zur Wache', score: '0/6'},
                        ],
                        total: {label: 'Gesamtpunktzahl', value: '19 / 32P'},
                        grading: PROTOCOL_GRADING,
                        actions: ['Speichern & Beenden', 'Geprüft & Weiter'],
                    },
                ],
                points: [
                    'Jeder Prüfpunkt trägt seine Punkte, die Summe entsteht beim Abhaken',
                    'Unterabschnitte gliedern lange Bögen, ohne die Punktzahl zu zerreißen',
                    'Ein geprüfter Abschnitt bekommt den Haken; unterbrechen ist jederzeit erlaubt',
                ],
            },
            {
                kind: 'showcase', id: 'protocol-evaluation', accent: 'info', tone: 'accent',
                chip: 'Prüfungsbögen im Detail',
                heading: 'Die Auswertung des Laufs',
                lead: 'Abschnitte in den Zeilen, Prüflinge in den Spalten. Wo es hakt, sieht man an der Farbe, '
                    + 'nicht am Nachrechnen.',
                screens: [
                    {
                        title: 'Auswertung', subtitle: 'Jugendflamme Stufe 1 - 18.04.2026 - Grenze: 24P',
                        actions: ['Tabelle als PDF', 'Alles als ZIP'],
                        evaluation: PROTOCOL_EVALUATION,
                        footer: 'Das PDF eines Prüflings kommt aus seiner Spalte, der ganze Lauf als ZIP.',
                    },
                ],
                points: [
                    'Die Farbskala läuft von rot bis grün und macht Lücken im Jahrgang sofort sichtbar',
                    'Die Themenspalte bleibt beim Scrollen stehen, auch bei dreißig Prüflingen',
                    'Die Bestehensgrenze steht über der Tabelle, zwei liegen darunter, das sieht man ohne Rechnen',
                ],
            },
        ],
    },
    {
        overview: {
            kind: 'split', id: 'inventory', accent: 'secondary', chip: 'Material',
            heading: 'Inventar',
            lead: 'Von der Jacke am Mitglied bis zur Kiste im Regal: was da ist, wo es liegt und wer es hat.',
            bullets: [
                'Mehrere Inventare nebeneinander: Kleidung mit Größen, Geräte ohne',
                'Eigene Felder je Inventar: Prüfdatum, Hersteller, interne Nummer',
                'Ausgabe und Rücknahme per Scan, auch mit der Handykamera',
                'Ein Lager aus verschachtelten Behältern: Raum, Schrank, Schublade, Kiste',
                'Prüfungen für Mitglieder und für Behälter, mit Ergebnis und Verlauf',
                'Tausch, Beschaffung und Verlust laufen als eigene Vorgänge',
                'Ausleihe zwischen Wachen mit Nachrichtenverlauf und Sperrzeiten',
            ],
            screen: {
                title: 'Inventar verwalten', subtitle: 'Inventare anlegen und bearbeiten',
                actions: ['Inventar erstellen'],
                rows: [
                    {
                        name: 'Einsatzkleidung', plain: true, meta: ['Intern', 'mit Größen'],
                        description: '128 Gegenstände · 3 verloren · 2 in Beschaffung',
                    },
                    {
                        name: 'Helme', plain: true, meta: ['Intern', 'mit Größen'],
                        description: '46 Gegenstände',
                    },
                    {
                        name: 'Funkgeräte', plain: true, meta: ['Gemischt'],
                        description: '24 Gegenstände · 4 verliehen',
                    },
                ],
                footer: 'Ein Inventar entscheidet selbst, ob es Größen führt und welche Felder seine Stücke tragen.',
            },
        },
        details: [
            {
                kind: 'showcase', id: 'inventory-items', accent: 'secondary', tone: 'accent',
                chip: 'Inventar im Detail',
                heading: 'Jedes Stück hat seine Geschichte',
                lead: 'Ein Inventar zeigt seinen Bestand als Tabelle mit den Feldern, die es selbst definiert hat. '
                    + 'Am Mitglied steht dieselbe Ausstattung als Karten.',
                screens: [
                    {
                        title: 'Einsatzkleidung', subtitle: 'Inventar-Details',
                        stats: INVENTORY_STATS,
                        table: {
                            columns: ['Name', 'Interne ID', 'Größe', 'Zugewiesen an', 'Prüfdatum'],
                            actions: true,
                            rows: [
                                [{text: 'Einsatzjacke', strong: true}, {text: 'EK-0142'}, {text: '152'},
                                    {text: 'Anna Müller', avatar: true}, {text: '04.2026'}],
                                [{text: 'Einsatzjacke', strong: true}, {text: 'EK-0143'}, {text: '164'},
                                    {text: '– Lager, Schrank 2', muted: true}, {text: '04.2026'}],
                                [{text: 'Überjacke', strong: true}, {text: 'EK-0088'}, {text: '48'},
                                    {text: 'Ben Krüger', avatar: true, note: {text: 'verloren', tone: 'error'}},
                                    {text: '11.2025'}],
                            ],
                        },
                        footer: 'Spalten und Filter richten sich nach den Feldern, die dieses Inventar führt.',
                    },
                    {
                        title: 'Mitglieder-Inventar', subtitle: 'Zugewiesene Ausrüstung eines Mitglieds',
                        hint: 'Code eingeben oder scannen, der Gegenstand wird direkt zugewiesen',
                        section: 'Einsatzkleidung',
                        rows: [
                            {
                                name: 'Einsatzjacke', plain: true, description: 'EK-0142',
                                badges: [{text: '152', tone: 'secondary'}],
                            },
                            {
                                name: 'Einsatzhose', plain: true, description: 'EK-0311',
                                badges: [{text: '152', tone: 'secondary'}, {text: 'Tausch angekündigt', tone: 'info'}],
                            },
                            {
                                name: 'Stiefel', plain: true, description: 'EK-0755',
                                badges: [{text: '38', tone: 'secondary'}],
                            },
                        ],
                        footer: 'Dieselbe Ausstattung sieht das Mitglied unter „Mein Inventar“.',
                    },
                ],
                points: [
                    'Ein unbekannter Code beim Scannen bietet an, den Gegenstand gleich anzulegen',
                    'Jedes Stück führt seinen Verlauf: ausgegeben, zurückgenommen, verloren, wiedergefunden',
                    'Verliehene Stücke tragen die Wache, bei der sie gerade liegen',
                ],
            },
            {
                kind: 'showcase', id: 'inventory-storage', accent: 'secondary',
                chip: 'Inventar im Detail',
                heading: 'Das Lager ist ein Baum',
                lead: 'Räume, Schränke, Schubladen und Kisten stecken ineinander. Jeder Gegenstand hat damit einen '
                    + 'Ort, den man vorlesen kann. Die Suche findet ihn darüber.',
                screens: [
                    {
                        title: 'Lager', subtitle: 'Räume, Schränke, Schubladen und Kisten',
                        filters: {search: 'Behälter suchen…'},
                        actions: ['Neuer Behälter'],
                        section: 'Behälter',
                        storage: INVENTORY_STORAGE,
                        footer: 'Die Suche findet Behälter und Gegenstände und zeigt zu jedem Gegenstand seinen Weg.',
                    },
                    {
                        title: 'Behälter-Prüfung', subtitle: 'Gerätehaus / Schrank 2 / Kiste Schläuche',
                        hint: 'Code eingeben oder scannen, jeder Treffer wird sofort bestätigt',
                        badges: [{text: 'Bestätigt: 9', tone: 'success'}, {text: 'Offen: 3', tone: 'info'},
                            {text: 'Fehlend: 1', tone: 'error'}, {text: 'Zusätzlich gefunden: 2', tone: 'info'}],
                        summary: [{label: 'Position', value: 'Behälter 3 von 12'}],
                        rows: [
                            {
                                name: 'C-Schlauch 15 m', plain: true, description: 'Zuletzt geprüft 03.2026 · Bestätigt',
                                badges: [{text: 'Bestätigt', tone: 'success'}],
                            },
                            {
                                name: 'Verteiler', plain: true, description: 'Noch nie geprüft',
                                badges: [{text: 'Offen', tone: 'info'}],
                            },
                            {
                                name: 'Standrohr', plain: true, description: 'Zuletzt geprüft 03.2026 · Bestätigt',
                                badges: [{text: 'Fehlend', tone: 'error'}],
                            },
                        ],
                        footer: 'Ein Stück aus einem anderen Behälter wird beim Scannen erkannt und sagt, wohin es gehört.',
                    },
                ],
                points: [
                    'Die Prüfung läuft den Baum ab: ein Behälter nach dem anderen, mit Vor und Zurück',
                    'Auf Wunsch werden Unter-Behälter gleich mitgeprüft',
                    'Was gefunden wird, ohne dort eingetragen zu sein, landet unter „Zusätzlich gefunden“',
                    'Ein Behälter steckt im nächsten, so tief, wie das Lager es hergibt',
                ],
            },
            {
                kind: 'showcase', id: 'inventory-requirements', accent: 'secondary', tone: 'accent',
                chip: 'Inventar im Detail',
                heading: 'Was jemand haben muss',
                lead: 'Je Rolle und je Gruppe steht fest, welche Ausstattung dazugehört. Daraus weiß die Prüfung, '
                    + 'was fehlt, und nicht nur, was da ist.',
                screens: [
                    {
                        title: 'Benötigt', subtitle: 'Benötigten Bestand je Rolle und Gruppe festlegen',
                        section: 'Benutzertyp · Aktive',
                        table: {
                            columns: ['Inventar', 'Anzahl'],
                            actions: true,
                            rows: [
                                [{text: 'Einsatzkleidung', strong: true}, {text: '1'}],
                                [{text: 'Helme', strong: true}, {text: '1'}],
                                [{text: 'Handschuhe', strong: true}, {text: '2'}],
                            ],
                        },
                        footer: 'Dieselbe Liste gibt es je Gruppe: Atemschutz bekommt mehr als der Rest.',
                    },
                    {
                        title: 'Inventarprüfung', subtitle: 'Wer wurde wann geprüft',
                        tabs: ['Team', 'Mitglieder'],
                        table: {
                            columns: ['Mitglied', 'Zuletzt geprüft', 'Geprüft von', 'Status'],
                            actions: true,
                            rows: [
                                [{text: 'Anna Müller', avatar: true}, {text: '12.03.2026'}, {text: 'C. Weiß'},
                                    {text: '-', muted: true}],
                                [{text: 'Ben Krüger', avatar: true}, {text: '-', muted: true}, {text: '-', muted: true},
                                    {text: 'Noch nie geprüft', badge: 'secondary'}],
                                [{text: 'Jonas Behr', avatar: true}, {text: '02.02.2026'}, {text: 'C. Weiß'},
                                    {text: 'Wird geprüft von C. Weiß', badge: 'error'}],
                            ],
                        },
                        footer: 'Wer eine Prüfung offen hat, sperrt sie für die anderen. Doppelte Arbeit fällt weg.',
                    },
                ],
                points: [
                    'Die Reihenfolge der Anforderungen bestimmt, wie die Prüfung sie abfragt',
                    'Sortieren nach Name oder nach letzter Prüfung zeigt, wer zu lange nicht dran war',
                ],
            },
            {
                kind: 'showcase', id: 'inventory-check', accent: 'secondary',
                chip: 'Inventar im Detail',
                heading: 'Die Prüfung am Mitglied',
                lead: 'Stück für Stück: vorhanden, verloren oder gar nicht erst ausgegeben. Für die Kleiderkammer '
                    + 'gibt es dieselbe Prüfung als Schnelldurchlauf mit Scanner.',
                screens: [
                    {
                        title: 'Inventarprüfung', subtitle: 'Anna Müller · Einsatzkleidung',
                        actions: ['Alle bestätigen', 'Prüfung abschließen'],
                        inventoryCheck: INVENTORY_CHECK,
                        footer: 'Ein verlorenes Stück legt auf Knopfdruck gleich eine Beschaffung an.',
                    },
                    {
                        title: 'Inventarprüfung', subtitle: 'Schnellprüfung',
                        rapidCheck: INVENTORY_RAPID,
                        footer: 'Der Scan bestätigt den passenden Gegenstand und springt weiter, ohne Tippen.',
                    },
                ],
                points: [
                    'Ein leerer Platz lässt sich sofort füllen: vorhandenes Stück zuweisen oder neues anlegen',
                    '„Nicht im Besitz“ trennt das ehrliche Fehlen vom Verlust',
                    'Jede Prüfung bleibt als Ergebnis erhalten, mit Notizen und Prüfer',
                ],
            },
            {
                kind: 'showcase', id: 'inventory-flow', accent: 'secondary', tone: 'accent',
                chip: 'Inventar im Detail',
                heading: 'Tausch und Beschaffung',
                lead: 'Die Hose ist zu kurz, der Helm ist weg: beides wird ein Vorgang mit Status, Verlauf und '
                    + 'einem Ende, statt einer Nachricht, die untergeht.',
                screens: [
                    {
                        title: 'Ausrüstungstausch', subtitle: 'Tausch-Anfragen verwalten',
                        actions: ['Neue Anfrage'],
                        table: {
                            columns: ['Mitglied', 'Inventar', 'Alt', 'Neu', 'Status', 'Grund'],
                            actions: true,
                            rows: [
                                [{text: 'Anna Müller', avatar: true}, {text: 'Einsatzkleidung'}, {text: '152'},
                                    {text: '164'}, {text: 'Angekündigt', badge: 'info'}, {text: 'zu kurz geworden'}],
                                [{text: 'Ben Krüger', avatar: true}, {text: 'Helme'}, {text: '54'}, {text: '56'},
                                    {text: 'Versendet', badge: 'primary'}, {text: 'drückt'}],
                                [{text: 'Clara Weiß', avatar: true}, {text: 'Einsatzkleidung'}, {text: '164'},
                                    {text: '158'}, {text: 'Erledigt', badge: 'success'}, {text: 'zu weit'}],
                            ],
                        },
                        footer: 'Jeder Statuswechsel landet im Verlauf, mit Notiz und Zeitpunkt.',
                    },
                    {
                        title: 'Beschaffung', subtitle: 'Ausrüstungsbeschaffung verwalten',
                        rows: [
                            {
                                name: 'Einsatzkleidung', plain: true, badges: [{text: '164', tone: 'secondary'},
                                    {text: 'Offen', tone: 'error'}],
                                description: 'Anna Müller - 14.06.2026',
                            },
                            {
                                name: 'Stiefel', plain: true, badges: [{text: '38', tone: 'secondary'},
                                    {text: 'Offen', tone: 'error'}],
                                description: 'Aus der Prüfung von Anna Müller - 18.06.2026',
                            },
                            {
                                name: 'Handschuhe', plain: true, badges: [{text: 'S', tone: 'secondary'},
                                    {text: 'Erledigt', tone: 'success'}],
                                description: 'Jonas Behr - 02.05.2026',
                            },
                        ],
                        footer: 'Die Liste ist die Einkaufsliste. Abgehakt wird, was angekommen ist.',
                    },
                ],
                points: [
                    'Ein Tausch entsteht auch aus der Sicht des Mitglieds heraus, nicht nur aus der Verwaltung',
                    'Ausgewählte Tauschvorgänge lassen sich gesammelt exportieren',
                    'Aus einem Verlust in der Prüfung wird mit einem Klick eine Beschaffung',
                ],
            },
            {
                kind: 'showcase', id: 'inventory-lending', accent: 'secondary',
                chip: 'Inventar im Detail',
                heading: 'Ausleihe zwischen Wachen',
                lead: 'Was die eine Wache nicht hat, hat oft die andere. Eine Anfrage trägt Zeitraum, Stücke und '
                    + 'den ganzen Schriftwechsel an einem Ort.',
                screens: [
                    {
                        title: 'Ausleihe', subtitle: 'Ausrüstungsausleihe zwischen Wachen',
                        actions: ['Anfrage erstellen'],
                        rows: [
                            {
                                name: 'Talbach', plain: true, meta: ['12.07.2026 - 14.07.2026'],
                                description: '2 × Zelt, 1 × Feldküche',
                                badges: [{text: 'Angefragt', tone: 'info'}],
                            },
                            {
                                name: 'Bergheim', plain: true, meta: ['01.06.2026 - 03.06.2026'],
                                description: '4 × Funkgerät',
                                badges: [{text: 'Ausgeliehen', tone: 'success'}, {text: 'Überfällig', tone: 'error'}],
                            },
                            {
                                name: 'Musterstadt', plain: true, meta: ['20.05.2026'],
                                description: '1 × Anhänger', badges: [{text: 'Abgeschlossen', tone: 'secondary'}],
                            },
                        ],
                        footer: 'Anfragen der eigenen Wache und Anfragen an die eigene Wache stehen in derselben Liste.',
                    },
                    {
                        title: 'Ausleih-Anfrage', subtitle: 'Talbach · 12.07. – 14.07.2026',
                        badges: [{text: 'Angefragt', tone: 'info'}],
                        section: 'Gegenstände',
                        table: {
                            columns: ['Inventar', 'Anzahl'],
                            rows: [
                                [{text: 'Zelte', strong: true}, {text: '2'}],
                                [{text: 'Feldküche', strong: true}, {text: '1'}],
                            ],
                        },
                        footer: 'Darunter läuft der Nachrichtenverlauf beider Wachen: Statuswechsel schreiben '
                            + 'sich selbst hinein.',
                    },
                ],
                points: [
                    'Sperrzeiten halten Zeiträume frei, in denen die eigene Wache nichts verleiht',
                    'Beim Bewilligen werden die konkreten Stücke zugeordnet, nicht nur die Menge',
                    'Überfällige Rückgaben sind an der Liste zu sehen, ohne nachzurechnen',
                ],
            },
        ],
    },
    {
        overview: {
            kind: 'split', id: 'boards', accent: 'info', chip: 'Organisation',
            heading: 'Boards',
            lead: 'Vorhaben, die länger laufen als ein Dienstabend: Zeltlager, Großübung, Beschaffung. '
                + 'Sie liegen auf einem Board statt in einem Nachrichtenverlauf.',
            bullets: [
                'Frei benannte Spalten mit eigener Farbe, Karten per Ziehen dazwischen',
                'Jedes Ticket trägt Kürzel und Nummer, auf die man sich berufen kann',
                'Priorität, Fälligkeit, Zuweisung, Labels und eigene Felder',
                'Checklisten, verknüpfte Tickets, Weblinks, Anhänge und Wiki-Seiten am Ticket',
                'Kommentare und ein Verlauf, der jede Änderung nennt',
                'Backlog und Archiv halten das Board frei von dem, was gerade nicht dran ist',
                'Ein Board mit einer Partnerwache teilen, lesend oder mitarbeitend',
            ],
            screen: {
                title: 'Planer', subtitle: 'Deine Boards',
                rows: [
                    {
                        prefix: 'ZL', name: 'Zeltlager 2026', plain: true,
                        description: 'Alles zur Freizeit im Juli - Material, Anmeldung, Fahrt',
                        trailing: '24 Tickets',
                    },
                    {
                        prefix: 'GH', name: 'Gerätehaus-Umbau', plain: true,
                        description: 'Bauabschnitte, Angebote, Termine mit dem Träger',
                        trailing: '61 Tickets',
                    },
                    {
                        prefix: 'ORG', name: 'Organisation', plain: true,
                        description: 'Laufende Aufgaben der Leitung', trailing: '9 Tickets',
                    },
                ],
                footer: 'Das Kürzel wird zur Ticketnummer: ZL-41 meint für alle dasselbe.',
            },
        },
        details: [
            {
                kind: 'showcase', id: 'boards-board', accent: 'info', tone: 'accent',
                chip: 'Boards im Detail',
                heading: 'Das Board zeigt den Stand',
                lead: 'Spalten, wie das Vorhaben sie braucht. Eine Karte zieht man weiter, wenn sie weiter ist, '
                    + 'und sieht an den Punkten, wie lange sie schon liegt.',
                screens: [
                    {
                        title: 'Zeltlager 2026', subtitle: 'ZL',
                        actions: ['Ticket erstellen'],
                        board: CAMP_BOARD,
                        footer: 'Die Punkte zählen die Tage in der Spalte: grün frisch, orange zäh, rot liegengeblieben.',
                    },
                    {
                        title: 'Zeltlager 2026 - Backlog', subtitle: 'Was noch nicht auf dem Board liegt',
                        table: {
                            columns: ['ID', 'Titel', 'Priorität', 'Zugewiesen', 'Fällig am'],
                            rows: [
                                [{text: 'ZL-52', strong: true}, {text: 'Nachtwache einteilen'}, {text: 'Mittel'},
                                    {text: '-', muted: true}, {text: '-', muted: true}],
                                [{text: 'ZL-49', strong: true}, {text: 'Spieleabend vorbereiten'}, {text: 'Niedrig'},
                                    {text: 'Anna Müller', avatar: true}, {text: '05.07.2026'}],
                                [{text: 'ZL-47', strong: true}, {text: 'Anhänger-Führerschein klären'},
                                    {text: 'Hoch'}, {text: '-', muted: true}, {text: '-', muted: true}],
                            ],
                        },
                        footer: 'Erledigtes wandert nach einer eingestellten Frist ins Archiv. Der Blick bleibt frei.',
                    },
                ],
                points: [
                    'Filter nach Bearbeiter und Labels blenden aus, was gerade nicht die eigene Sache ist',
                    'Die Suche über dem Board findet Tickets nach Nummer und Titel',
                ],
            },
            {
                kind: 'showcase', id: 'boards-ticket', accent: 'info',
                chip: 'Boards im Detail',
                heading: 'Ein Ticket von innen',
                lead: 'Alles zu einer Aufgabe an einem Ort, daneben die Frage, wer wann was daran geändert hat.',
                screens: [
                    {
                        title: 'ZL-41 · Zelte auf Schäden prüfen', subtitle: 'In Arbeit',
                        badges: [{text: 'Material', tone: 'info'}, {text: 'Hoch', tone: 'error'}],
                        summary: [
                            {label: 'Zugewiesen', value: 'Ben Krüger'},
                            {label: 'Fällig am', value: '20.06.2026'},
                            {label: 'Anhänge', value: '2'},
                        ],
                        progress: {label: 'Checkliste', value: '3/5', percent: 60},
                        section: 'Verknüpfte Tickets',
                        table: {
                            columns: ['Art', 'Ticket', 'Spalte'],
                            rows: [
                                [{text: 'Blockiert', strong: true}, {text: 'ZL-38 · Essen planen'}, {text: 'In Arbeit'}],
                                [{text: 'Bezieht sich auf', strong: true}, {text: 'ZL-35 · Bus buchen'},
                                    {text: 'Erledigt'}],
                            ],
                        },
                        footer: 'Darunter: Weblinks, Anhänge als Kacheln mit Vorschau und verknüpfte Wiki-Seiten.',
                    },
                    {
                        title: 'ZL-41 · Zelte auf Schäden prüfen', subtitle: 'Kommentare und Verlauf',
                        tabs: ['Kommentare', 'Verlauf', 'Alle'],
                        comments: TICKET_COMMENTS,
                        footer: 'Der Reiter „Verlauf“ zeigt daneben jede Änderung: verschoben von Offen nach '
                            + 'In Arbeit, Priorität Mittel → Hoch, Label hinzugefügt.',
                    },
                ],
                points: [
                    'Eine Checkliste zerlegt die Aufgabe, ohne dafür eigene Tickets zu brauchen',
                    'Verknüpfungen sagen, was blockiert und was wovon kommt',
                    'Eine verknüpfte Wiki-Seite ersetzt die halbe Beschreibung',
                    'Anhänge lassen sich im Vollbild durchblättern, Bilder wie PDFs',
                ],
            },
            {
                kind: 'showcase', id: 'boards-access', accent: 'info', tone: 'accent',
                chip: 'Boards im Detail',
                heading: 'Wer liest, wer schreibt, wer mitarbeitet',
                lead: 'Ein Board beantwortet zwei Fragen für die eigene Wache und dieselben zwei noch einmal '
                    + 'für jede Partnerwache, mit der es geteilt wird.',
                screens: [
                    {
                        title: 'Board-Einstellungen', subtitle: 'Zeltlager 2026 · Zugriff',
                        summary: [
                            {label: 'Lesezugriff', value: 'Alle Mitglieder'},
                            {label: 'Bearbeitungszugriff', value: 'Team · Gruppe Lagerleitung'},
                        ],
                        hint: 'Leer heißt: sichtbar für alle. Eingeschränkt wird über Rolle, Gruppe und Tag.',
                        section: 'Föderation',
                        table: {
                            columns: ['Partnerwache', 'Zugriff', 'Sichtbar ab'],
                            actions: true,
                            rows: [
                                [{text: 'Talbach', strong: true}, {text: 'Lesen & Bearbeiten', badge: 'success'},
                                    {text: 'Team & Verwaltung'}],
                                [{text: 'Bergheim', strong: true}, {text: 'Nur Lesen', badge: 'info'},
                                    {text: 'Alle Mitglieder'}],
                            ],
                        },
                        footer: 'Wird ein Board zum Bearbeiten geteilt, lässt sich zusätzlich wählen, welche '
                            + 'Mitgliedstypen der Partnerwache schreiben dürfen.',
                    },
                    {
                        title: 'Föderierte Boards', subtitle: 'Boards, die Partnerwachen mit dir teilen',
                        hint: 'Boards mit Lesezeichen erscheinen direkt in der Seitenleiste.',
                        section: 'Talbach',
                        rows: [
                            {
                                name: 'Kreisjugendtag 2026', prefix: 'KRS', plain: true, favourite: true,
                                description: 'Gemeinsame Planung aller beteiligten Wachen',
                                badges: [{text: 'Vollzugriff', tone: 'success'}],
                            },
                            {
                                name: 'Beschaffung Atemschutz', prefix: 'BER', plain: true, favourite: false,
                                description: 'Sammelbestellung mehrerer Wachen',
                                badges: [{text: 'Nur Lesen', tone: 'info'}],
                            },
                        ],
                        footer: 'Geteilte Boards stehen nach Partnerwache gruppiert; hier die von Talbach.',
                    },
                ],
                points: [
                    'Teilen darf nur, wer die Berechtigung dafür hat, sonst zeigt der Abschnitt nur den Stand',
                    'Ein nur lesend geteiltes Board bietet gar keine Knöpfe zum Schreiben an',
                    'Die eigene Leitung kann den Zugriff auf ein fremdes Board weiter einschränken, nie erweitern',
                ],
            },
        ],
    },
    {
        overview: {
            kind: 'split', id: 'procedures', accent: 'success', chip: 'Organisation',
            heading: 'Abläufe',
            lead: 'Aufnahme, Austritt, Veranstaltung: was immer gleich läuft, läuft als Checkliste mit '
                + 'Fortschritt, statt in einem Kopf.',
            bullets: [
                'Vorlagen mit beliebig vielen Schritten, aus denen neue Abläufe entstehen',
                'Schritte hängen voneinander ab, was noch nicht dran ist, bleibt gesperrt',
                'Ein Schritt ist intern oder für die Zugewiesenen sichtbar',
                'Manche Schritte hakt die betroffene Person selbst ab, andere nur die Betreuung',
                'Fälligkeit, Fortschritt und der Filter „Meine“ zeigen, wo es klemmt',
                'Eine Notiz am Schritt hält fest, was dabei aufgefallen ist',
            ],
            screen: {
                title: 'Abläufe', subtitle: 'Offene und abgeschlossene Abläufe',
                actions: ['Neuer Ablauf'],
                filters: {search: 'Abläufe durchsuchen…'},
                tabs: ['Offen', 'Abgeschlossen', 'Meine'],
                rows: [
                    {
                        name: 'Aufnahme · Lena Sommer', plain: true,
                        badges: [{text: 'Offen', tone: 'primary'}],
                        description: 'Vom Aufnahmeantrag bis zur Vorstellung in der Gruppe',
                        trailing: 'Fällig 30.06.2026',
                    },
                    {
                        name: 'Aufnahme · Timo Reich', plain: true,
                        badges: [{text: 'Abgeschlossen', tone: 'success'}],
                        description: 'Vom Aufnahmeantrag bis zur Vorstellung in der Gruppe',
                    },
                    {
                        name: 'Zeltlager · Vorbereitung', plain: true,
                        badges: [{text: 'Offen', tone: 'primary'}, {text: 'Überfällig', tone: 'error'}],
                        description: 'Anmeldungen, Fahrt, Material und Elternbrief',
                        trailing: 'Fällig 10.06.2026',
                    },
                ],
                footer: 'Ein Ablauf entsteht aus einer Vorlage oder von Hand, mit Fälligkeit und Zugewiesenen.',
            },
        },
        details: [
            {
                kind: 'showcase', id: 'procedures-run', accent: 'success', tone: 'accent',
                chip: 'Abläufe im Detail',
                heading: 'Eine Aufnahme, Schritt für Schritt',
                lead: 'Ein neues Mitglied kommt dazu. Der Ablauf sagt allen Beteiligten, was erledigt ist, '
                    + 'was ansteht und was noch warten muss.',
                screens: [
                    {
                        title: 'Ablauf-Details', subtitle: 'Aufnahme · Lena Sommer',
                        badges: [{text: 'Offen', tone: 'primary'}],
                        actions: ['Bearbeiten', 'Abschließen'],
                        procedure: PROCEDURE_INTAKE,
                        footer: 'Ein gesperrter Schritt nennt den Schritt, auf den er wartet.',
                    },
                ],
                points: [
                    'Erledigte Schritte tragen ihren Zeitpunkt und sind durchgestrichen',
                    'Die Notiz am offenen Schritt hält fest, warum er noch offen ist',
                ],
            },
            {
                kind: 'showcase', id: 'procedures-template', accent: 'success',
                chip: 'Abläufe im Detail',
                heading: 'Einmal beschreiben, immer gleich ablaufen',
                lead: 'Die Vorlage hält fest, was zu einer Aufnahme gehört, mitsamt der Reihenfolge und der '
                    + 'Frage, wer welchen Schritt abhaken darf.',
                screens: [
                    {
                        title: 'Vorlage bearbeiten', subtitle: 'Aufnahme neues Mitglied',
                        actions: ['Schritt hinzufügen'],
                        procedureTemplate: PROCEDURE_TEMPLATE,
                        footer: 'Aus dieser Vorlage entsteht jede Aufnahme. Die Schritte lassen sich vorher '
                            + 'noch anpassen.',
                    },
                ],
                points: [
                    '„Intern“ heißt: die betroffene Person sieht den Schritt gar nicht',
                    '„Nutzer zugewiesen“ heißt: sie darf ihn selbst abhaken',
                    'Abhängigkeiten sorgen dafür, dass niemand an Schritt vier anfängt',
                ],
            },
        ],
    },
    {
        overview: {
            kind: 'split', id: 'news', accent: 'error', chip: 'Kommunikation',
            heading: 'Neuigkeiten',
            lead: 'Was in der Wache passiert, steht da, wo alle es finden, mit den Rückfragen darunter '
                + 'und der Gewissheit, wer es gelesen hat.',
            bullets: [
                'Beiträge in Markdown, mit Bildern und Vorschau vor dem Veröffentlichen',
                'Sichtbarkeit über Rolle, Gruppe und Tag einschränken',
                'Kommentare mit Antworten und Erwähnungen direkt unter dem Beitrag',
                'Ein Auge zeigt, wer den Beitrag gesehen hat, und wer noch nicht',
                'Als Blog-Beitrag auf die öffentliche Seite stellen',
                'Mit Partnerwachen teilen; ihre Beiträge stehen in derselben Liste',
                'Der öffentliche Blog läuft zusätzlich als RSS- und Atom-Feed',
            ],
            screen: {
                title: 'Neuigkeiten', subtitle: 'Was es Neues gibt',
                actions: ['Neuigkeit erstellen'],
                news: NEWS_ITEMS,
                footer: 'Ein Schloss heißt eingeschränkte Sichtbarkeit, „Blog“ heißt: steht auch öffentlich.',
            },
        },
        details: [
            {
                kind: 'showcase', id: 'news-reach', accent: 'error', tone: 'accent',
                chip: 'Neuigkeiten im Detail',
                heading: 'Wer bekommt den Beitrag zu sehen?',
                lead: 'Wer darf es lesen, steht es auch öffentlich, und bekommen die Partnerwachen es auch? '
                    + 'Alle drei Antworten stehen beim Schreiben nebeneinander.',
                screens: [
                    {
                        title: 'Neuigkeit bearbeiten', subtitle: 'Zeltlager 2026 - Anmeldung offen',
                        newsSettings: NEWS_SETTINGS,
                        footer: 'Ohne Auswahl liest die Neuigkeit jedes Mitglied: Einschränken ist die Ausnahme.',
                    },
                ],
                points: [
                    'Beim Teilen entscheidet zusätzlich, ab welcher Rolle die Partnerwache mitliest',
                    'Ein Blog-Beitrag erscheint auf der öffentlichen Seite und im Feed',
                    'Föderieren darf nur, wer die Berechtigung dafür hat',
                ],
            },
            {
                kind: 'showcase', id: 'news-read', accent: 'error',
                chip: 'Neuigkeiten im Detail',
                heading: 'Angekommen, und zwar nachweislich',
                lead: 'Eine Nachricht in einer Gruppe verschwindet nach oben. Ein Beitrag bleibt stehen, '
                    + 'sammelt seine Rückfragen und zählt mit, wer ihn gesehen hat.',
                screens: [
                    {
                        title: 'Neuigkeiten', subtitle: 'Beitrag mit Rückfragen',
                        news: [NEWS_ITEMS[0] as PitchNews],
                        comments: NEWS_COMMENTS,
                        footer: 'Am Auge neben dem Titel steht die Zahl der Aufrufe; dahinter liegen die Namen.',
                    },
                ],
                points: [
                    'Der Aufruf-Dialog trennt „Gesehen“ von „Noch nicht gesehen“, mit Zeitpunkt',
                    'Kommentare erlauben Antworten und Erwähnungen mit @',
                ],
            },
        ],
    },
    {
        overview: {
            kind: 'split', id: 'forms', accent: 'secondary', chip: 'Kommunikation',
            wide: true,
            heading: 'Formulare',
            lead: 'Eigene Fragen stellen, an die Wache, an die Eltern oder an die Öffentlichkeit. '
                + 'Die Auswertung entsteht dabei von selbst.',
            bullets: [
                'Sechs Fragetypen: Auswahl, Text, Bewertung, Datum, Rangfolge und Likert-Skala',
                'Entwurf, offen, geschlossen, veröffentlicht wird bewusst',
                'Wer antworten darf, entscheiden Rolle, Gruppe und Tag',
                'Antworten auf Wunsch später noch änderbar',
                'Diagramme je Frage, dazu die Einzelantworten und ein CSV-Export',
                'Ausstehende Antworten zeigen namentlich, wer noch fehlt',
                'Kontaktformulare stehen öffentlich auf der Website',
            ],
            screen: {
                title: 'Formulare', subtitle: 'Umfragen und Formulare',
                forms: FORMS,
                footer: 'Beim Veröffentlichen werden alle berechtigten Mitglieder benachrichtigt. '
                    + 'Kontaktformulare stehen daneben öffentlich auf der Website.',
            },
        },
        details: [
            {
                kind: 'showcase', id: 'forms-fill', accent: 'secondary', tone: 'accent',
                chip: 'Formulare im Detail',
                heading: 'Jede Frage bringt ihre Eingabe mit',
                lead: 'Eine Rangfolge fragt man nicht mit einem Textfeld ab. Jeder Fragetyp bringt seine '
                    + 'eigene Eingabe mit, auf dem Handy genauso bedienbar wie am Rechner.',
                screens: [
                    {
                        title: 'Formular ausfüllen', subtitle: 'Zeltlager 2026 - Rückmeldung',
                        form: FORM_FILL,
                        actions: ['Absenden'],
                        footer: 'Ein Stern markiert Pflichtfragen; Optionen lassen sich auf Wunsch mischen.',
                    },
                ],
                points: [
                    'Bei einer Auswahl ist Mehrfachauswahl und ein Feld „Sonstiges“ möglich',
                    'Die Rangfolge wird sortiert, nicht getippt',
                    'Eine Likert-Skala fragt mehrere Aussagen in einem Raster ab',
                ],
            },
            {
                kind: 'showcase', id: 'forms-analytics', accent: 'secondary',
                chip: 'Formulare im Detail',
                heading: 'Die Auswertung entsteht beim Antworten',
                lead: 'Kein Sammeln, kein Nachzählen: jede Frage bringt ihr Diagramm mit, und daneben steht, '
                    + 'wer noch nicht geantwortet hat.',
                screens: [
                    {
                        title: 'Auswertung', subtitle: 'Zeltlager 2026 - Rückmeldung · 23 Antworten',
                        tabs: ['Diagramme', 'Einzelantworten'],
                        actions: ['Exportieren'],
                        formAnalytics: FORM_ANALYTICS_DATA,
                        footer: 'Der Export nimmt ausgewählte Fragen und auf Wunsch Profilfelder mit und liefert eine CSV-Datei.',
                    },
                ],
                points: [
                    'Auswahlfragen als Ring, Bewertungen und Rangfolgen als Balken',
                    'Freitext steht als Liste darunter, ohne Diagramm',
                    'Die ausstehenden Antworten sind der Grund, warum niemand mehr erinnern muss',
                ],
            },
        ],
    },
    {
        overview: {
            kind: 'split', id: 'waitlist', accent: 'primary', chip: 'Kommunikation',
            wide: true,
            heading: 'Wartelisten',
            lead: 'Wer dazukommen will, trägt sich selbst ein. Die Liste führt ihn dann bis zur Aufnahme, '
                + 'ohne dass jemand eine Tabelle pflegt.',
            bullets: [
                'Öffentliches Anmeldeformular mit euren eigenen Feldern und den Erziehungsberechtigten',
                'Jeder Eintrag bekommt einen persönlichen Statuslink und bestätigt darüber sein Interesse',
                'Eine Punkteformel bestimmt die Reihenfolge: Wartezeit, Alter, eigene Felder',
                'Vom Ausstehend über Wartend, Eingeladen und Probezeit bis Beigetreten',
                'Mehrere Listen nebeneinander, öffentlich oder nur intern',
            ],
            screen: {
                title: 'Wartelisten', subtitle: 'Anmeldungen verwalten',
                waitlist: {data: WAITLIST, section: 'lists'},
                footer: 'Eine öffentliche Liste nimmt Anmeldungen von der Website an; eine interne nur von der Leitung.',
            },
        },
        details: [
            {
                kind: 'showcase', id: 'waitlist-intake', accent: 'primary', tone: 'accent',
                chip: 'Wartelisten im Detail',
                heading: 'Die Anfrage kommt herein',
                lead: 'Das Formular steht öffentlich, die Angaben landen als Anfrage. Erst wenn jemand sie '
                    + 'freigibt, wird daraus ein Platz in der Warteliste.',
                screens: [
                    {
                        title: 'Warteliste 2026', subtitle: 'Ausstehende Anfragen',
                        waitlist: {data: WAITLIST, section: 'pending'},
                        footer: 'Aufgeklappt stehen Notiz, Erziehungsberechtigte und alle ausgefüllten Felder.',
                    },
                ],
                points: [
                    'Die E-Mail-Adresse wird bestätigt, bevor die Anfrage überhaupt erscheint',
                    'Ablehnen ist genauso ein Klick wie Freigeben',
                    'Über Einladungslinks lassen sich Anmeldungen auch gezielt öffnen',
                    'Notizen zur Anfrage bleiben intern und stehen nur der Leitung',
                ],
            },
            {
                kind: 'showcase', id: 'waitlist-order', accent: 'primary',
                chip: 'Wartelisten im Detail',
                heading: 'Die Reihenfolge rechnet sich selbst',
                lead: 'Statt einer handgepflegten Sortierung steht eine Formel dahinter: Wartezeit, Alter und '
                    + 'eure eigenen Felder ergeben die Punktzahl, nach der die Liste sortiert.',
                screens: [
                    {
                        title: 'Warteliste 2026', subtitle: 'Wartend & Eingeladen',
                        waitlist: {data: WAITLIST, section: 'waiting'},
                        footer: 'Welche eigenen Felder als Spalte erscheinen, wählt jede Wache selbst.',
                    },
                ],
                points: [
                    'Die Punktzahl steht in der Liste, nicht in einer Nebenrechnung',
                    'Ein Klick lädt ein, der Statuslink zeigt den Stand ohne Konto',
                    'Die Position ist ein Anhaltspunkt, keine Zusage; das sagt die Seite auch so',
                ],
            },
            {
                kind: 'showcase', id: 'waitlist-settings', accent: 'primary', tone: 'accent',
                chip: 'Wartelisten im Detail',
                heading: 'Eine Formel statt einer Meinung',
                lead: 'Wie die Punkte entstehen, steht in den Einstellungen der Liste, nachvollziehbar für '
                    + 'alle, die es wissen wollen.',
                screens: [
                    {
                        title: 'Warteliste 2026', subtitle: 'Einstellungen der Liste',
                        waitlist: {data: WAITLIST, section: 'settings'},
                        footer: 'Die Formel kennt [wartezeit_monate], age([Geburtsdatum]) und jedes eigene Feld.',
                    },
                ],
                points: [
                    'Das Bestätigungsintervall hält die Liste sauber: wer nicht bestätigt, fällt heraus',
                    'Probegruppe und Beitrittsgruppe sagen, wo jemand landet',
                    'Die Anwesenheitsschwelle legt fest, wie viel Schnuppern zur Aufnahme gehört',
                ],
            },
            {
                kind: 'showcase', id: 'waitlist-testing', accent: 'primary', tone: 'accent',
                chip: 'Wartelisten im Detail',
                heading: 'Probezeit, und dann dabei',
                lead: 'Wer eingeladen wurde, schnuppert erst mit. Die Anwesenheit zählt dabei automatisch mit, '
                    + 'bis die vereinbarte Schwelle erreicht ist.',
                screens: [
                    {
                        title: 'Warteliste 2026', subtitle: 'Im Probezeitraum',
                        waitlist: {data: WAITLIST, section: 'testing'},
                        footer: 'Erreicht jemand die Schwelle, hebt die Karte sich hervor. Aufgenommen wird '
                            + 'trotzdem per Klick.',
                    },
                ],
                points: [
                    'Die Probezeit läuft in einer eigenen Gruppe, die Aufnahme legt die Zielgruppe fest',
                    'Anwesenheiten kommen aus dem Anwesenheitsmodul, nicht aus einer zweiten Liste',
                    'Ausscheiden ist jederzeit möglich und bleibt als Stand erhalten',
                ],
            },
        ],
    },
    {
        overview: {
            kind: 'split', id: 'pages', accent: 'error', chip: 'Außenauftritt',
            heading: 'Öffentliche Seiten',
            lead: 'Die Webseite der Wache entsteht in derselben Anwendung. Kein zweites System und '
                + 'kein zweiter Login.',
            bullets: [
                'Seiten in Zeilen und Spalten, Blöcke per Ziehen sortiert',
                'Über dreißig Bausteine: Hero, Zahlen, Galerie, Karte, Countdown, Zitat, Akkordeon',
                'Termine, Neuigkeiten, Wiki-Artikel und Umfragen binden sich selbst ein',
                'Unterseiten, Entwurf und Veröffentlichung, eine Startseite als Stern',
                'Farben und Logo kommen aus dem Erscheinungsbild der Wache',
                'Dateien und Formulare der Seite liegen gleich daneben',
            ],
            screen: {
                title: 'Seiten', subtitle: 'Die Seiten der Wache',
                actions: ['Seite anlegen'],
                pages: {data: PAGES, section: 'tree'},
                footer: 'Der Stern markiert die Startseite; Unterseiten hängen eingerückt darunter.',
            },
        },
        details: [
            {
                kind: 'showcase', id: 'pages-public', accent: 'error', tone: 'accent',
                chip: 'Öffentliche Seiten im Detail',
                heading: 'So sieht es von außen aus',
                lead: 'Dieselben Blöcke, die in der Verwaltung zusammengeschoben werden, stehen für Besucher '
                    + 'als fertige Seite da, im Erscheinungsbild der Wache.',
                screens: [
                    {
                        title: 'Startseite', subtitle: 'musterstadt.example / start',
                        station: 'Öffentlich',
                        pages: {data: PAGES, section: 'page'},
                        footer: 'Zahlen, Countdown und Adresse sind Bausteine, keine handgeschriebene Seite.',
                    },
                ],
                points: [
                    'Ein Block kann die halbe Zeile einnehmen, die Breite ist frei einstellbar',
                    'Auf dem Handy stapeln sich die Spalten von selbst untereinander',
                    'Zugriffszahlen je Seite entstehen ohne Cookies und ohne IP-Adressen',
                ],
            },
        ],
    },
    {
        overview: {
            kind: 'split', id: 'notifications', accent: 'primary', chip: 'Kommunikation',
            heading: 'Benachrichtigungen',
            lead: 'Jeder stellt selbst ein, was ihn erreicht und auf welchem Weg.',
            bullets: [
                'Neun Arten, drei Kanäle: App, E-Mail und Feed einzeln schaltbar',
                'Ohne eingerichteten Mailversand bleibt die E-Mail-Spalte aus',
                'Der Mailversand nennt den Anbieter samt Datenschutzerklärung',
                'Kalender als iCal-Abo, Mitteilungen als Atom-Feed',
            ],
            screen: {
                title: 'Benachrichtigungen', subtitle: 'Was dich erreicht und wie',
                notifications: {settings: NOTIFICATION_SETTINGS},
                footer: 'Ein abgeschalteter Kanal gilt nur für dich. Andere bekommen ihre Mitteilung trotzdem.',
            },
        },
        details: [
            {
                kind: 'showcase', id: 'notifications-feeds', accent: 'primary', tone: 'accent',
                chip: 'Benachrichtigungen im Detail',
                heading: 'Der Dienstplan im eigenen Kalender',
                lead: 'Wer die Anwendung nicht dauernd offen hat, holt sich beides dahin, wo er ohnehin '
                    + 'hinschaut: die Termine in den Kalender, die Mitteilungen in den Reader.',
                screens: [
                    {
                        title: 'Benachrichtigungen', subtitle: 'RSS / iCal-Feeds',
                        notifications: {feeds: NOTIFICATION_FEEDS},
                        footer: 'Drei Ausführlichkeiten stehen zur Wahl: ausführlich, kompakt, minimal.',
                    },
                ],
                points: [
                    'Der Kalender aktualisiert sich von selbst, geänderte Termine wandern mit',
                    'Der Feed zeigt nur, was die eigenen Berechtigungen hergeben',
                    'Der Zugang hängt an einem persönlichen Schlüssel, der sich neu erzeugen lässt',
                ],
            },
        ],
    },
]
