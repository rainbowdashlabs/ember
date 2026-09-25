# Änderungsprotokoll

## v26.19.0

### Neue Funktionen

- **Eine Umfrage oder ein Kontaktformular lässt sich per Link verschicken.** Jede Umfrage und jedes Kontaktformular hat einen eigenen Link, der sie auf einer eigenen Seite öffnet, mit nichts darum herum außer dem Namen der Wache. Wer den Link hat, kann antworten; er steht in keinem Menü und wird von Suchmaschinen nicht erfasst. Ein neuer Link lässt sich jederzeit erzeugen und beendet jede Kopie des bisherigen.
- **Ein öffentliches Formular kann nur noch über seinen Link erreichbar sein.** Eine Umfrage und ein Kontaktformular sind standardmäßig öffentlich erreichbar, was sie brauchen, um auf einer öffentlichen Seite zu stehen. Abgeschaltet antworten sie nur noch über ihren Link: Die eigene Adresse funktioniert nicht mehr, sodass ein neuer Link tatsächlich jeden bisher vergebenen Weg hinein beendet. Die Einstellung steht beim Bearbeiten bei den übrigen Einstellungen des Formulars.
- **Eine Seite kann nur über ihren Link erreichbar sein.** Neben Entwurf und Veröffentlicht lässt sich eine Seite jetzt so stellen, dass sie für alle offen ist, die ihren Link haben. Eine solche Seite fehlt im Menü der Wache und in ihrer Sitemap, und sie steht für sich: ohne Seite darüber und ohne Seiten darunter. Der Link lässt sich ebenso ersetzen.
- **Ein Mitglieder-Feld am Termin kann pro Datum gefüllt werden.** Ein Termin-Feld, das Mitglieder benennt, kann für jeden Tag einer Terminreihe einen eigenen Eintrag halten, sodass darin stehen kann, wer diese Woche fährt und wer nächste. Ohne die Einstellung gilt ein Eintrag wie bisher für die ganze Reihe.

### Verbesserungen

- **Wer in einem Mitglieder-Feld steht, steht auf der Anmeldeliste des Termins.** Wer in einem Termin-Feld eingetragen ist, das Mitglieder benennt, nimmt teil: Die Person steht auf der Anmeldeliste, wird mitgezählt, und der Termin erreicht ihren Kalender und ihr Kalender-Abonnement. Der Platz ist sofort bestätigt und wird zurückgegeben, indem der Name aus dem Feld genommen wird, nicht durch Abmelden.
- **Das Kalender-Abonnement reicht ein Jahr zurück.** Bisher blieb nur die letzte Woche darin, sodass sich nicht nachschlagen ließ, was im vergangenen Herbst war. Es umfasst jetzt ein Jahr in beide Richtungen.
- **Wird eine Umfrage auf ihren Link beschränkt, steht dabei, wo sie noch eingebunden ist.** Eine Umfrage, die auf einer Seite steht, lässt sich dort nicht mehr beantworten, sobald sie nur noch über ihren Link erreichbar ist. Beim Umstellen werden jetzt die Seiten genannt, die sie noch zeigen, und diese Seiten weisen darauf hin, bis jemand sie dort entfernt.
- **Bewertung, Rangfolge und Skala lassen sich in einer öffentlichen Umfrage beantworten.** Eine öffentliche Umfrage bot diese drei Fragearten beim Erstellen an, zeigte beim Ausfüllen aber nichts dazu, sodass sich nur Text-, Datums- und Auswahlfragen beantworten ließen. Alle sechs Arten funktionieren jetzt überall, wo eine Umfrage beantwortet wird.

### Fehlerbehebungen

- **Eine öffentliche Umfrage konnte einen Absenden-Knopf anbieten, der nie funktionieren konnte.** Eine Umfrage, die vor ihrer Freigabe auf eine öffentliche Seite gestellt oder nach dem Schließen dort gelassen wurde, zeigte alle Fragen und einen Absenden-Knopf, und das Absenden endete mit der Bitte, es noch einmal zu versuchen. Eine solche Umfrage sagt jetzt klar, dass sie noch nicht offen oder bereits geschlossen ist, und bietet nichts zum Ausfüllen an.
- **Ein Link auf eine andere Seite führte ins Leere.** Eine Karte, die auf eine andere Seite der Wache zeigte, verlor ihr Ziel beim Speichern, sodass jede von ihnen einen Platzhaltertitel zeigte und beim Klicken nirgendwohin führte. Karten behalten ihr Ziel jetzt und folgen ihm, wenn eine Seite umbenannt oder verschoben wird.
- **Öffentliche Seiten wurden auch nach dem Abschalten weiter ausgeliefert.** Wurden die öffentlichen Seiten einer Wache abgeschaltet, verschwanden sie aus dem Menü und aus der Sitemap, wurden aber weiter an jeden ausgeliefert, der die Adresse noch hatte. Die Einstellung wird jetzt bei jedem Abruf einer Seite berücksichtigt, über ihre eigene Adresse ebenso wie über einen verschickten Link.
- **Ein wiederkehrender Termin zeigte nur seinen nächsten Tag.** Die Terminübersicht behielt je Termin nur einen Eintrag und verwarf die übrigen, sodass ein wöchentlicher Dienst eine einzige Zeile blieb, egal wie weit man blätterte, und Nachladen nichts ergänzte. Die Liste läuft jetzt Tag für Tag, zehn auf einmal.
- **Formulare für die Öffentlichkeit standen in der internen Liste.** Unter `/station/forms` standen auch Umfragen und Kontaktformulare, die für eine öffentliche Seite geschrieben wurden, und ließen sich von dort per Link verschicken. Dort stehen jetzt nur noch die internen Umfragen der Wache.
- **Bei öffentlichen Formularen wurden Einstellungen angeboten, die nichts bewirken konnten.** Bei einer Umfrage oder einem Kontaktformular, das ohne Anmeldung beantwortet wird, ließen sich weiterhin "Antworten änderbar" und "Antwort erwartet" setzen, was ohne bekannte Person beides nicht funktioniert. Beides wird jetzt nur noch dort angeboten, wo man sich zum Antworten anmeldet.
- **Der Weg zurück aus den Ergebnissen einer öffentlichen Umfrage führte zur falschen Liste.** Aus den Ergebnissen einer Umfrage von einer öffentlichen Seite ging es zurück zur internen Umfrageliste, in der sie gar nicht steht. Es geht jetzt zurück zu der Liste, aus der sie geöffnet wurde.
- **Eine Seite unter einer unveröffentlichten Seite war öffentlich.** Eine veröffentlichte Seite unterhalb einer nicht veröffentlichten ließ sich weiterhin über eine Adresse öffnen, die den Namen der unveröffentlichten Seite enthielt, und stand in der Sitemap. Eine Seite ist jetzt nur öffentlich, wenn alles darüber es auch ist.
- **Alle Mitglieder wurden über Formulare für die Öffentlichkeit benachrichtigt.** Wurde ein Kontaktformular oder eine öffentliche Umfrage geöffnet, ging an die ganze Wache eine Benachrichtigung über ein neues Formular, die auf eine Seite führte, die sie dann abwies. Nur noch interne Umfragen werden angekündigt.

## v26.18.7

### Verbesserungen

- **Termin und Anwesenheitsliste führen zueinander.** Das Menü am Termin öffnet die Liste für den angezeigten Tag oder legt eine an, wenn es noch keine gibt, und das Menü an der Liste führt zurück zum Termin an genau diesem Tag.
- **Die Anmeldung über ein anderes Gerät fragt nach einer Zahl.** Das Gerät, das hereinmöchte, zeigt eine zweistellige Zahl, und das freischaltende Gerät wählt sie aus sechs aus. Wer nur ein Bild des Codes geschickt bekommen hat, sieht diese Zahl nicht, sodass ein weitergeleiteter Code allein nicht mehr genügt.
- **Das freischaltende Gerät muss nur noch scannen.** Der QR-Code enthält jetzt den Anmeldecode, sodass das Handy direkt das öffnet, was es freischalten soll, statt nach acht Zeichen zu fragen. Der Code steht weiterhin da, falls jemand nicht scannen kann.
- **Neue Einstellungen für gemeinsam genutzte Internetzugänge.** Eine Installation, deren Mitglieder über einen Anschluss ins Internet gehen, kann die Grenzen pro Adresse für die Anmeldung über ein anderes Gerät unter `auth.deviceHandshake` erweitern.

### Sicherheit

- **Ein Anmeldecode gehört jetzt zu genau einem Konto.** Bisher konnte jedes angemeldete Mitglied jeden offenen Code freischalten, sodass ein in einer Gruppe herumgereichter Code das Konto desjenigen preisgab, der antwortete. Du gibst deine Adresse oder deinen Benutzernamen an, bevor der Code entsteht, und nur dieses Konto kann ihn freischalten.

### Fehlerbehebungen

- **Die Anwesenheit ließ sich nur am Tag selbst erfassen.** Der Eintrag dafür erschien am Termin nur, solange der angezeigte Tag der heutige war, sodass eine am Morgen danach nachgetragene oder am Abend vorher vorbereitete Liste gar nicht anzulegen war. Angeboten wird jetzt der Tag, den die Seite zeigt, und die Liste gehört zu diesem Tag.
- **Seltenere Termine tauchten in der Terminübersicht nie auf.** Die Liste sah vier Wochen voraus, sodass ein Termin, der einmal im Quartal oder einmal im Jahr stattfindet, darin vollständig fehlte, obwohl der Kalender daneben denselben Termin zeigte, sobald man vorblätterte. Die Liste sieht jetzt so weit voraus, wie sie für eine volle Seite braucht.
- **Die Anwesenheit eines wiederkehrenden Termins wurde auf das falsche Datum gebucht.** Beim Erfassen der Anwesenheit eines wöchentlichen Termins bekam die Liste das Datum des ersten Termins der Reihe, und an jedem weiteren Tag öffnete sich wieder dieselbe Liste. Jeder Tag bekommt jetzt seine eigene Liste mit dem Datum, das er abdeckt.
- **Bei gemeinsamer Adresse konnte sich immer nur ein Gerät anmelden.** In einem Büro oder Gerätehaus, wo alle über denselben Anschluss ins Internet gehen, wurde das zweite wartende Gerät abgewiesen und wartete dann endlos, ohne zu sagen warum. Jedes Gerät und jedes Konto hat jetzt sein eigenes Kontingent.
- **Eine Bitte, langsamer zu machen, sah aus wie ein allgemeiner Fehler.** Ein Bildschirm, dem gesagt wurde, dass er es zu oft versucht, zeigte dieselbe Meldung wie bei jedem anderen Fehler und riet dazu, einen neuen Code anzufordern, was einen weiteren Versuch verbrauchte. Jetzt steht dort, dass zu oft versucht wurde, und es wird gewartet statt sofort erneut gefragt.
- **Die Anmeldung über ein anderes Gerät war in manchen Browsern versteckt.** Der Link auf der Anmeldeseite erschien nur, wenn der Browser einen Passkey speichern konnte, obwohl diese Art der Anmeldung nie einen brauchte. Er wird jetzt allen angezeigt.

## v26.18.6

### Neue Funktionen

- **Umfrageergebnisse nach Mitgliedern auswerten.** Die Ergebnisse einer internen Umfrage lassen sich nach Mitgliedsart, Gruppen, Tags, Alter und Profilangaben filtern und aufteilen, etwa um die Jugend mit der Einsatzabteilung zu vergleichen oder nur die Antworten der über 40-Jährigen anzusehen. Die Gruppen stehen in eigenen Farben nebeneinander, und die Ansicht lässt sich als Lesezeichen speichern.

### Verbesserungen

- **Umfrage-Exporte zeigen, wer geantwortet hat.** Die Tabelle oder der Ausdruck der Antworten einer internen Umfrage enthält jetzt neben dem Namen auch Mitgliedsart, Gruppen und Alter.
- **Bilder öffnen sich per Klick groß.** Ein Bild oder Galeriebild auf einer Seite oder in einem Artikel aus dem Seiten-Editor und das Foto einer Fundsache öffnen sich über den ganzen Bildschirm, ungeschnitten und mit ihrer Bildunterschrift. Escape oder ein Klick daneben schließt es wieder.
- **Hinweisboxen, Zitate und Bildunterschriften heben sich im PDF aus dem Wiki ab.** Hinweisboxen und Zitate stehen in einem hinterlegten Kasten mit farbigem Balken an der Seite, und die Zeile unter einem Bild steht klein und mittig darunter als seine Bildunterschrift.

### Änderungen

- **Markdown-Dateien im Wiki heißen jetzt Artikel.** Der Eintrag im Menü Neu und der Typ auf einer Kachel lauten „Artikel“ statt „Markdown-Datei“.

### Fehlerbehebungen

- **Bilder fehlten in PDFs aus dem Wiki.** Beim Speichern eines Artikels als PDF fiel jedes Bild weg, und es blieb höchstens sein Alternativtext stehen. Die eigenen Bilder der Wache werden jetzt mitgedruckt, passend zur Seite verkleinert oder in der Breite, die sie bekommen haben.
- **Formatierungen gingen in PDFs aus dem Wiki verloren.** Farbiger Text, Markierungen und Unterstreichungen wurden als einfacher Text gedruckt, und eine Markierung behielt ihre Gleichheitszeichen. Jetzt erscheinen sie im Druck so wie im Artikel.
- **Bildern in Artikeln und Neuigkeiten aus dem Seiten-Editor fehlte ihre hinterlegte Beschreibung.** Sagte der Bildblock selbst nichts, erschienen Alternativtext und Beschreibung, die in der Mediathek am Bild gespeichert sind, weder im Artikel oder in der Neuigkeit noch in Suchtreffer, Vorschau oder PDF. Jetzt erscheinen sie, wie schon auf Seiten.
- **Kacheln wurden auf breiten Bildschirmen sehr groß.** Auf einem großen Monitor blieb es im Wiki bei vier Spalten und im Fundbüro bei drei, sodass sich jede Kachel über einen großen Teil des Bildschirms zog. Die Zahl der Spalten wächst jetzt mit der Breite des Fensters.
- **Fotos im Fundbüro wurden abgeschnitten.** Das Foto einer Fundsache wurde zugeschnitten, um seine Karte zu füllen, und konnte so den Gegenstand selbst verdecken. Jetzt ist das ganze Foto zu sehen, passend verkleinert.

## v26.18.5

### Neue Funktionen

- **Favoriten im Wiki.** Jede Datei und jeder Ordner, auch was eine Partnerwache mit euch teilt, lässt sich mit dem Stern auf der Kachel oder oben in einer geöffneten Datei markieren und findet sich dann im Ordner Favoriten am Anfang des Wikis. Favoriten sieht nur, wer sie gesetzt hat.
- **Das Wiki zeigt, was seine Dateien sind.** Ein Bild oder ein PDF zeigt sich jetzt auf seiner Kachel, das Bild verkleinert und das PDF mit seiner ersten Seite, sodass sich ein Ordner voller Blätter auseinanderhalten lässt, ohne jedes einzeln zu öffnen. Dateien, die schon da waren, bekommen ihr Bild, wenn jemand ihren Ordner das erste Mal öffnet.

### Verbesserungen

- **Dateien im Wiki lassen sich von ihrer Kachel herunterladen.** Eine hochgeladene Datei bietet auf ihrer Kachel einen Knopf zum Herunterladen, so wie ein Eintrag schon sein PDF anbietet, und gibt die Datei genau so heraus, wie sie hochgeladen wurde.
- **Bilder auf Seiten laden in der Größe, in der sie gezeigt werden.** Ein Banner, eine Galerie oder ein Bild auf einer Seite holt jetzt eine für die Seite passende Fassung statt des Fotos, wie es aufgenommen wurde, sodass Seiten schneller öffnen und auf dem Handy weniger Daten brauchen.

### Fehlerbehebungen

- **Ein PDF in der installierten App zu speichern hinterließ ein leeres Fenster.** Unter Android ersetzte das Speichern eines PDFs in Ember, über Firefox auf dem Startbildschirm installiert, die Seite durch eine leere und speicherte nichts, und jede andere Datei meldete einen Fehler, obwohl das Speichern geklappt hatte. Ein PDF öffnet sich jetzt im Betrachter des Browsers, wo es gespeichert werden kann, und eine gespeicherte Datei gilt nicht mehr als gescheitert.
- **Einen Filter im Anwesenheitsbericht zu speichern tat nichts.** Den Filter unter einem Namen zu speichern endete in einer allgemeinen Fehlermeldung, und ein früher gespeicherter Filter konnte ohne seine Mitgliedstypen oder mit nur einer seiner Gruppen zurückkommen. Ein gespeicherter Filter behält jetzt alle Mitgliedstypen und Gruppen, mit denen er gespeichert wurde, und wendet sie auf die aktuelle Woche, den aktuellen Monat, das Quartal oder das Jahr an.

## v26.18.4

### Neue Funktionen

- **Ungespeichertes bleibt euch erhalten.** Was ihr auf einer Seite, in einer Neuigkeit oder in einem Wiki-Eintrag schreibt, bleibt im Browser, wenn ihr die Seite ohne Speichern verlasst, und wird euch beim nächsten Öffnen wieder angeboten. Ein Entwurf wird nach dem Speichern und nach einer Woche vergessen.

### Verbesserungen

- **Dateien öffnen sich auf dem Handy, statt still nicht zu speichern.** Ein Dokument, ein Bild oder eine Aufnahme wird auf Handy und Tablet jetzt in der App selbst geöffnet, mit einem Knopf zum Speichern; eine Werteliste oder ein Archiv geht wie bisher an das Teilen-Menü. Downloads, die so lange brauchten, dass das Teilen-Menü nicht mehr aufging, endeten vorher im Nichts.
- **Exporte heißen nach dem, was in ihnen steht.** Ein Bericht, eine Liste oder eine Anwesenheitsliste kommt als etwas wie `Anwesenheit - Januar 2026.pdf` an statt jedes Mal gleich zu heißen, sodass zwei davon nicht mehr als durchnummerierte Kopien nebeneinander liegen. Der Name folgt der Sprache, in der die Dokumente der Wache geschrieben werden.
- **Tabellen öffnen sich so, wie euer Programm sie erwartet.** Ihr wählt beim Export zwischen Semikolon und Komma, und jede Tabelle trägt jetzt die Kennung, die Umlaute richtig statt als Buchstabensalat öffnet.
- **Die Anwesenheit lässt sich je Quartal auswerten.** Der Bericht bietet das Quartal neben Woche, Monat und Jahr an.
- **Der Anwesenheitsbericht ist auch als Tabelle zu haben.** Die Stunden zu jedem Namen lassen sich als Tabelle exportieren und nicht nur drucken, für alle, die sie anderswo weiterrechnen.
- **Die Antworten eines Formulars lassen sich drucken.** Bisher gab es sie nur als Tabelle.
- **Der Anwesenheitsbericht lässt weg, wer bei nichts war.** Mitglieder ohne Stunden und ohne Termine füllen die Übersicht nicht mehr mit leeren Zeilen, so wie es die Monatstabellen schon vorher gehalten haben.
- **Ein Bild behält, was über es geschrieben wurde.** Wo eine Datei einen Alternativtext oder eine Bildunterschrift trägt, verwendet eine Seite beides, solange die Kachel nichts Eigenes dazu sagt.
- **Listen lassen sich auf dem Handy leichter abarbeiten.** Was mit einer Zeile möglich ist, steht jetzt am Fuß ihrer Karte, jeder Knopf über die volle Breite, statt neben dem Titel eingequetscht. Auch die Spalten auf einer Karte haben mehr Luft zueinander.
- **Der Text eines Termins wird in den Listen richtig dargestellt.** Unter Termine → Kommende und beim Anlegen einer Anwesenheit zeigte eine Beschreibung mit Überschriften, Hervorhebungen oder einer Aufzählung ihre rohen Zeichen, oder sie lief in voller Länge und schob den nächsten Termin vom Bildschirm. Sie wird jetzt so dargestellt, wie sie geschrieben wurde, und nach ein paar Zeilen abgeschnitten, so wie eine Neuigkeit schon vorher.
- **Zwanzig neue Bilder für ein Ausrüstungsstück.** Hose, Stiefel, Turnschuhe, T-Shirt, Pullover, Kappe und Socke kommen zur Kleidung, Axt, Handlampe, Leiter, Pylone, Kanister, Schaufel, Rucksack, Fernglas, Rettungsring, Fahrzeug, Blaulicht, Lagerfeuer und Landkarte zur Ausrüstung. Die bisherigen Notlösungen heißen jetzt schlichter: aus den Fußspuren werden Sohlen, aus der Leiter wird die Poolleiter.

### Änderungen

- **Dokumente werden auf Deutsch geschrieben, solange nicht Englisch verlangt wird.** Eine Wache ohne eingestellte Sprache bekam ihre Berichte und Listen auf Englisch; jetzt bekommt sie sie auf Deutsch. Stellt die Sprache der Wache auf Englisch, um es beim Alten zu belassen.

### Fehlerbehebungen

- **Eine im Baukasten geschriebene Tabelle ging verloren.** Wer im Texteditor eine Tabelle schrieb, speicherte nur die Wörter darin, sodass die Zeilen mit dem Speichern weg waren. Eine Tabelle bleibt jetzt eine Tabelle.
- **Tabellenzellen waren viel breiter als ihr Inhalt.** Eine Tabelle richtete ihre Spalten nach der Zelle mit dem längsten Wort, und das Ziehen an einer Kante arbeitete dagegen. Die Spalten teilen sich die Breite jetzt gleichmäßig, im Editor wie auf der veröffentlichten Seite.
- **Der Speichern-Knopf konnte außer Reichweite rutschen.** Wer genug in den Texteditor schrieb, schob ihn unter den unteren Rand des Fensters. Der Editor übernimmt das Geschriebene jetzt sofort und braucht keinen Knopf mehr.
- **Eine Suche konnte dabei bleiben, dass es nichts zu finden gibt.** Wer in eine Auswahl für Arten, Inventare oder Ausrüstungsbilder tippte, bevor ihre Liste fertig geladen war, sah unter Umständen keine Treffer, auch nachdem alles angekommen war. Jetzt sucht sie erneut, sobald die Liste da ist.
- **Eine Anwesenheitsliste zu einem Termin kam unausgefüllt an.** Wer eine öffnete, fand jeden Namen offen und musste erst am Termin nachsehen, wer zugesagt hatte. Eine Liste kommt jetzt mit allen Zusagen als anwesend an, und wo der Termin eine Anmeldung verlangte, mit allen ohne Zusage als abgemeldet.
- **Wer jemanden von Hand auf eine Liste setzte, konnte ihn als abgemeldet eintragen.** Bei einer Liste zu einem wiederkehrenden Termin wurden die Antworten von heute gelesen statt die für den Abend, um den es geht. Jetzt entscheiden die Antworten für den Tag der Liste.
- **Das Dokument einer Verlustmeldung ließ sich auf dem iPhone nicht sichern.** Der Download, der das Teilen-Menü öffnet, erreichte jeden Knopf außer diesem, sodass die Datei auf einem iPhone oder iPad ungesichert bleiben konnte. Jetzt geht er denselben Weg wie die anderen.
- **Exporte taten auf einem Android-Handy nichts.** Wer dort auf einen Download-Knopf drückte, bekam unter Umständen nur einen leeren Tab und keine Datei, vor allem in einem Browser, der in einer anderen App steckt. Ein Bericht, eine Liste oder eine Anwesenheitsliste öffnet sich jetzt zum Lesen auf dem Bildschirm und lässt sich von dort weiter sichern oder teilen.

## v26.18.3

### Verbesserungen

- **Die Spaltenauswahl einer Tabelle schließt sich von selbst.** Sie blieb offen, bis ihr Knopf noch einmal gedrückt wurde; jetzt schließt sie sich bei einem Klick irgendwo sonst auf der Seite oder mit Escape. Auch das Menü einer Zeile schließt sich, sobald das einer anderen Zeile geöffnet wird.
- **Die Spaltenauswahl blendet alle oder keine auf einmal ein.** Zwei Knöpfe oben darin zeigen oder verbergen alle Spalten, und eine lange Liste verteilt sich auf mehrere Spalten, statt über den Bildschirm hinauszulaufen.
- **Tabellenfilter passen zum Inhalt der Spalte.** Jede Spalte der Mitgliederliste, der Stücke eines Inventars und der Anmeldungen zu einem Termin bietet den Filter an, den ihr Inhalt verlangt: Daten nach Tag, Geburtsdaten zusätzlich nach Alter, Zahlen über einen Bereich und Auswahlfelder über ihre Namen. Sortiert wird nach derselben Regel, sodass Zahlen und Tage in der richtigen Reihenfolge stehen und Auswahlfelder in der, in der sie angelegt wurden.
- **Tabellen merken sich ihre Spalten.** Wo du das Speichern von Komfort-Einstellungen erlaubt hast, sind die für eine Tabelle gewählten Spalten beim nächsten Besuch im selben Browser noch gewählt, und die Mitgliederliste merkt sich die Auswahl für jeden Reiter einzeln. Die Datenschutzerklärung führt das einmal für alle Tabellen auf und fragt deshalb einmalig erneut nach deiner Zustimmung.
- **Tabellen sortieren und filtern auch auf dem Handy.** Auf einem kleinen Bildschirm sind die Zeilen Karten mit den gewählten Spalten, darüber stehen eine Sortierung und ein Filter.
- **Weitere Listen der Wache sortieren und filtern nach jeder Spalte.** Die Warteschlange der Bewegungen, geliehene Ausrüstung, Inventarprüfungen, die Inventarübersicht, die Mitgliederliste im Inventar, Wartelisten, die Anmeldestatistik, ehemalige Mitglieder, der Stand der Zwei-Faktor-Anmeldung, die Nutzung der Kalender-Feeds sowie Backlog und Archiv der Boards funktionieren wie die Mitgliederliste: Jede Spaltenüberschrift sortiert und filtert, und bei den längeren lassen sich die Spalten wählen.
- **Die Listen des Verbands und der Administration ziehen nach.** Die Mitgliederliste des Verbands, der Speicher, die Bewegungen und die Ausrüstung auf den Wachen sowie in der Administration die Bewerbungen, das Zwei-Faktor-Protokoll, der API-Status, die Datenverfolgung, das Mail-Protokoll und die Beacon-Zahlen sortieren und filtern über jede Spaltenüberschrift und lassen ihre Spalten wählen.
- **Bewerbungen mit unbestätigter Adresse sagen das.** Eine Bewerbung, deren Adresse noch nicht bestätigt ist, erscheint nicht mehr als wartend wie die anderen, sodass sich beide unterscheiden und filtern lassen.
- **Die Beacon-Zahlen zeigen Konten und Wachen.** Zwei Spalten, die erfasst, aber nie angezeigt wurden, lassen sich in der Spaltenauswahl einschalten.

### Änderungen

- **Sortieren und Filtern sind in die Spaltenüberschriften gewandert.** Die Sortierliste und die Filterknöpfe der Bewegungen, die Sortierknöpfe der geliehenen Ausrüstung und der Sortierschalter der Inventarprüfungen sind weg; stattdessen sortieren und filtern die Spaltenüberschriften, und die Bewegungen filtern ihre Stücke nach Inventar und ihre Schritt-Spalte nach Status, nach wem dran ist und nach dem erreichten Schritt beim Namen. Die Mitgliederliste im Inventar vergisst einmalig die vor dieser Version gewählten Spalten.

### Fehlerbehebungen

- **Nach einem Datum sortierte Anmeldungen standen durcheinander.** In der Tabelle der Anmeldungen zu einem Termin sortierte eine Datumsspalte die Tage als Text, sodass der 2. Januar vor dem 15. Dezember des Vorjahres stand. Sie sortiert jetzt nach dem Tag.
- **Ein gespeicherter Mitgliederfilter vergaß die offenen Fragen.** Ein gespeicherter Filter nach Personen, die eine Frage nicht beantwortet hatten, kam beim erneuten Auswählen ohne diese Bedingung zurück. Jetzt kommt er vollständig zurück.
- **Gruppiertes Inventar ignorierte die Tabellensteuerung.** Wo die Stücke eines Inventars nach Art gruppiert sind, hatten Spaltenauswahl sowie Sortieren und Filtern über eine Spaltenüberschrift keine Wirkung. Jetzt gelten sie für jede Gruppe.
- **Erziehungsberechtigte konnten die Dateien eines Termins nicht öffnen.** Bei einem Termin, der nur bestimmten Mitgliedern angezeigt wird, sahen Erziehungsberechtigte ihn über ihre Kinder, bekamen seine Dateien aber verweigert. Die Dateien stehen ihnen jetzt offen, sobald eines ihrer Kinder den Termin sehen darf.
- **Downloads taten auf dem iPhone nichts.** Ein Druck auf einen Download-Knopf auf iPhone oder iPad konnte ohne jede Meldung ins Leere gehen, vor allem im Browser, der in andere Apps eingebaut ist. Die Datei öffnet jetzt das Teilen-Menü, von wo aus sie sich in Dateien sichern oder weitergeben lässt.

## v26.18.2

### Neue Funktionen

- **Eine Personenliste lässt sich mit selbst gewählten Spalten anzeigen.** Wer zu einem Termin kommt, steht jetzt mit dem daneben, was die Wache wissen will, die Mitgliederliste bietet dieselbe Tabelle, und eine Spaltenauswahl lässt sich unter einem Namen speichern und als Blatt zum Mitnehmen oder als Tabelle für den Schreibtisch herausgeben. Angeboten und aufgeschrieben wird nur, was du auch sehen darfst; eine Frage, die du nicht lesen darfst, fehlt ganz, statt leer dazustehen.

### Fehlerbehebungen

- **Ein jährlicher Termin konnte am falschen Tag auftauchen.** Bei einer Wache, deren Uhr der des Servers vorausgeht, zählte ein um Mitternacht beginnender Abend als der Tag davor, sodass der Jahrestag eines jährlichen Termins einen Tag zu früh fiel. Der Tag wird jetzt auf der Uhr der Wache gelesen.
- **Die Anmeldung konnte einen Tag zu früh schließen.** Die Prüfung, die eine Anmeldung eine bestimmte Zahl von Tagen vor dem Termin schließt, fragte den Server nach dem Datum statt die Wache, sodass eine Wache, die vorausgeht, in den letzten Stunden des Abends noch beim Vortag war und ihre Listen einen Tag daneben schloss.

## v26.18.1

### Neue Funktionen

- **Eine Partnerwache kann eigene Plätze bekommen.** Ein mit einer anderen Wache geteilter Termin kann Plätze für sie zurücklegen, mit oder ohne Obergrenze, und diese Wache wählt dann selbst, wer von ihren Leuten sie einnimmt, statt auf eine Bestätigung zu warten. Ist nichts zurückgelegt, bestätigt die ausrichtende Wache wie bisher jeden Gast selbst.
- **Eine Station bestimmt, wann ihre Benachrichtigungen verschickt werden.** Unter Station → Mailing nennt sie die Uhrzeiten, zu denen die gesammelten Benachrichtigungen rausgehen, etwa sieben Uhr morgens und zwei Uhr nachmittags, oder einfach stündlich. Was dazwischen ankommt, wartet auf den nächsten Termin: Eine Station, die eine Mail am Tag will, bekommt jetzt eine Mail am Tag.

### Verbesserungen

- **Eine Abmeldung von einem Termin lässt sich zurücknehmen.** Fünf Minuten lang lässt sich die Abmeldung über die eingeblendete Meldung wieder zurücknehmen, und zurück kommt der Platz, der belegt war, und nicht eine neue Anmeldung am Ende der Warteschlange. Die letzten beiden Knöpfe, die einen Platz auf einen Druck hin zurückgaben, der für einen ganzen Haushalt und der am Termin einer Partnerwache, fragen jetzt vorher nach, wie die übrigen es längst tun.
- **Zu spät zur Anmeldung sagt jetzt, wer weiterhelfen kann.** Eine erneute Anmeldung nach dem Anmeldeschluss endete in einer nichtssagenden Fehlermeldung. Jetzt steht dort, dass die Anmeldung geschlossen ist und dass die Terminleitung noch jemanden aufnehmen kann.
- **Ein Gast einer Partnerwache wird behandelt wie ein eigenes Mitglied.** Seine Anmeldung wird sofort angenommen, wenn der Termin keine Bestätigung verlangt, statt auf eine zu warten, um die nie jemand gebeten wurde, und sie wird abgelehnt, wenn der Termin keine Anmeldungen annimmt, abgesagt wurde oder die Anmeldung geschlossen ist. Eine Abmeldung bleibt vermerkt, damit die ausrichtende Wache erkennt, wer sich abgemeldet hat und wer nie geantwortet hat.
- **Drei weitere Bilder für ein Ausrüstungsstück.** Kopfhörer, Schlüssel und Tacker stehen jetzt zusätzlich zur Auswahl, unter Ausrüstung und Allgemein.
- **Wer einem Verbund folgt, erfährt von dessen Partnerwachen.** Benachrichtigungen aus einem Verbund wurden zwar gesammelt, aber an niemanden verschickt: Wer einem Verbund folgte, um über die Partner auf dem Laufenden zu bleiben, hörte gar nichts. Sie gehen jetzt mit denselben Zeiten und Einstellungen raus wie die einer Station.
- **Die Dateien eines Termins werden gezeigt, nicht nur angeboten.** Ein Druck darauf öffnet die Datei an Ort und Stelle: Herauszufinden, welches von vier Blättern die Karte ist, heißt nicht mehr, alle vier herunterzuladen. Das Speichern bleibt eine eigene Schaltfläche.
- **Dateien zeigen, was in ihnen steckt.** Bilder und die erste Seite eines PDFs stehen jetzt neben der Datei, in der Liste eines Termins wie in der Mediathek, statt einer Reihe gleicher Symbole.
- **Auswahlmöglichkeiten an Terminen und Wartelisten werden zeilenweise erfasst.** Beide fragten die ganze Liste noch in einem einzigen Feld ab, das eine trennte bei Zeilenumbrüchen, das andere bei Kommas: Eine Auswahl mit Komma wurde so unbemerkt zu zweien. Sie nutzen jetzt denselben Editor wie überall sonst, mit einer Zeile je Auswahl, die sich umsortieren lässt.
- **Die Löschen-Schaltfläche an den Feldern eines Termins sitzt an einer festen Stelle.** Sie teilte sich eine Zeile, die umbrach, und konnte dadurch mitten im Bereich zwischen anderen Einstellungen landen; jetzt steht sie oben rechts an ihrem Feld.
- **Die Neuigkeitenliste liest sich wieder als Liste.** Ein langer Beitrag füllt nicht mehr die ganze Seite: Beiträge werden auf wenige Zeilen gekürzt und laden zum Weiterlesen ein, den Rest gibt es auf der Seite des Beitrags.

### Änderungen

- **Benachrichtigungsmails gehen frühestens stündlich raus.** War eine Instanz auf eine kürzere Sammelzeit als eine Stunde eingestellt, werden ihre Stationen jetzt zur vollen Stunde angeschrieben. Wer sich auf eine kürzere Sammelzeit verlassen hat, sollte wissen, dass sie die Wartezeit nicht mehr darunter drückt.

### Fehlerbehebungen

- **Neuigkeiten von den Machern von Ember trugen in der Liste kein Zeichen.** Das Ember-Logo erschien erst im geöffneten Beitrag, nicht in der Liste, aus der er geöffnet wurde: Dort war ein solcher Beitrag von einem an der Station geschriebenen nicht zu unterscheiden.
- **Das Löschen eines Kontos meldete Probleme, die keine waren.** Beim Entfernen eines Kontos wurden mehrere Datenarten aufgeführt, die nicht aufgeräumt werden konnten, obwohl die Datenbank sie längst entfernt hatte und nichts zurückblieb. Die Liste des Aufzuräumenden wird jetzt gegen die Datenbank selbst geprüft und kann nicht mehr unbemerkt auseinanderlaufen.
- **Wer seinen Platz zurückgab, konnte ganz aus der Liste verschwinden.** Ein noch nicht bestätigter Platz wurde beim Zurückgeben gelöscht: Die Anmeldeliste war dann einfach um einen Eintrag kürzer und nichts sagte, wer abgesprungen war, obwohl die Benachrichtigung darüber verschickt wurde. Jeder zurückgegebene Platz bleibt jetzt als zurückgezogen in der Liste, und eine erneute Anmeldung funktioniert wie zuvor.
- **Eine am späten Abend geöffnete Liste galt dem Vortag.** Wurde eine Anwesenheit aus einem wiederkehrenden Termin gestartet, las sie das Datum von der Uhr des Servers statt von der der Station: In den letzten Stunden vor Mitternacht wurde die Liste damit für den vorherigen Termin geöffnet und trug dessen Zeiten. Der Tag richtet sich jetzt durchgehend nach der Station.

## v26.18.0

### Neue Funktionen

- **Eine Problemmeldung kann ein Bild der Seite mitbringen.** Auf Knopfdruck fragt der Browser, was geteilt werden soll, und bietet diesen Tab zuerst an, sodass kein Dialog des Betriebssystems nötig ist; oder du wählst ein Bild, das du selbst gemacht hast. Zieh über alles, was niemand sehen soll, und es wird übermalt, bevor die Meldung rausgeht. Der Dialog tritt während der Aufnahme zur Seite, damit die Seite im Bild ist und nicht der Dialog. Von allein wird nie etwas aufgenommen, Passwortfelder sind abgedeckt, bevor das Bild überhaupt erscheint, und eine Meldung ohne Bild ist wie bisher.
- **Ein Mitglied kann so genannt werden, wie es alle tatsächlich nennen.** Wer im Register Maximilian heißt und in der ganzen Wache Max gerufen wird, hinterlegt einen Spitznamen im Profil, und von da an steht auf dem Brett, in den Kommentaren, bei den Anmeldungen, in den Benachrichtigungen und in den Mails Max. In Mitgliederlisten steht `Maximilian "Max" Hoffmann`, damit niemand raten muss, wer gemeint ist. Das Mitglied setzt seinen eigenen im Profil, wer sich um es kümmert, darf einen für es setzen, und wer die Mitglieder der Wache pflegt, darf einen im Profil des Mitglieds in der Verwaltung richtigstellen. Sonst niemand, und wer ihn geschrieben hat, wird in jedem Fall festgehalten. Eine Wache, die lieber durchgehend Registernamen führt, schaltet das Ganze ab, und jeder hinterlegte Spitzname bleibt erhalten.
- **Auf einem Dokument steht weiterhin der Name aus dem Register.** Anwesenheitsliste, Prüfprotokoll, Bestands- und Bewegungsexporte und die Datenauskunft nennen Maximilian Hoffmann, was immer die Bildschirme sagen. Wer die Wache verlässt, behält in den alten Einträgen den Namen, unter dem die Wache ihn kannte.
- **Eine Anwesenheit kann ohne passende Vorlage begonnen werden.** Neben den Vorlagen steht jetzt eine Leere Anwesenheit, die in einem zweiten Schritt fragt, welche Mitgliedstypen und welche Gruppen eingetragen werden, und danach die gewohnte vorausgefüllte Liste öffnet. Beides ergänzt sich, und eine Wache muss dafür keine leere Vorlage mehr aufbewahren.

### Verbesserungen

- **Eine Vorlage sagt vor der Auswahl, was sie mitbringt.** Jede Kachel auf der Seite für eine neue Anwesenheit nennt jetzt die Gruppen, die sie einträgt, und die Felder, die sie abfragt; die Wahl läuft damit nicht mehr über das Gedächtnis. Eine Vorlage, die niemanden einträgt, sagt auch das.
- **Das Änderungsprotokoll sagt, wann eine Version erschienen ist.** Jeder Eintrag trägt jetzt den Tag seines Erscheinens, mit der genauen Uhrzeit unter dem Mauszeiger, und endet mit einem Link, der seine Änderungen gegenüber der vorherigen Veröffentlichung auf GitHub öffnet.
- **Ein Geburtsdatum kann ohne das Alter dahinter stehen.** Das Feld hat jetzt einen Schalter dafür, eingeschaltet wie bisher, damit eine Wache, die das Alter zusätzlich als eigene Frage stellt, es nicht zweimal in einer Zeile stehen hat.
- **Ein berechnetes Feld bietet nur noch die Einstellungen an, die es erreichen.** Seine Antwort schreibt niemand, also fragt es nicht mehr, ob eine Antwort erwartet wird, ob sie geschrieben werden darf, ob eine Änderung gemeldet werden soll oder womit sie beginnen soll.
- **Eine Namensliste bleibt eine Namensliste.** Was bei jemandem offen ist, steht nicht mehr unter jeder Zeile aufgeklappt. Eine Zeile sagt, wie viele Vorgänge es sind, ein Geburtstag steht weiterhin daneben, und ein Druck öffnet alles. Eine Wache mit vierzig Leuten und je einem Tausch hatte aus der Anwesenheitsliste eine Seite voller Besorgungen gemacht.
- **Ein Tausch kann abgebrochen werden, während das Mitglied davorsteht.** Wer die Kontrolle führt, kann ihn von der Liste aus löschen; es wird vorher gefragt und das Teil genannt. Dort stehen nur Tausche, bei denen noch nichts übergeben wurde, es gibt also nie etwas zurückzunehmen.
- **Das Bild einer Meldung kann an einen Beacon weitergehen, nachdem jemand hier es angesehen hat.** Eine Meldung mit Bild wartet in der Verwaltungsliste, bis sie weitergegeben wird; dort lässt sich vorher mehr übermalen oder das Bild aus der Sendung nehmen. Ein Schalter in den Beacon-Einstellungen schickt sie stattdessen sofort raus, und ein weggelassenes Bild wird einer Meldung nie nachgereicht.
- **Problemmeldungen werden aufgeräumt.** Dreißig Tage nachdem eine Meldung als erledigt markiert wurde, wird sie mit ihrem Bild gelöscht. Bisher blieben sie für immer liegen.

### Fehlerbehebungen

- **Ein Foto auszuwählen ließ die Seite stehen.** Beim Auswählen eines Profilbilds reagierte die ganze Seite nicht mehr, während das Bild auf eine sendbare Größe gebracht wurde, ohne dass etwas davon zu sehen war, und zwar lange genug, dass Leute noch einmal drückten und fragten, ob es geklappt hat. Die Wartemarkierung erscheint jetzt vor der Arbeit, und die Arbeit selbst geht an einen anderen Thread, wo der Browser einen hat.
- **Es wurde eine Profiländerung gemeldet, die niemand gemacht hat.** Ein Profil zu öffnen und zu speichern zählte jede unbeantwortete Frage, die unbeantwortet blieb, als Änderung, weil eine nie beantwortete Frage und eine mit leerem Feld beantwortete unterschiedlich gespeichert und als verschieden gelesen wurden. Ein Alter, das sich aus einem Geburtsdatum selbst errechnet, wurde genauso gemeldet, obwohl es niemand schreiben kann. Beides wird nicht mehr aufgezeichnet und niemandem mehr zur Bestätigung vorgelegt.
- **Wer für ein Mitglied verantwortlich ist, kam nicht an dessen Dokumente.** Die Einverständniserklärung, der ärztliche Vermerk und alles andere, was die Wache für ein Kind aufbewahrt, wurde genau der Person verweigert, die es unterschreibt, und es gab auch keinen Bildschirm dafür. Sie stehen jetzt im Profil unter den eigenen Dokumenten, zum Lesen und nicht zum Hinzufügen, und was vor dem Mitglied verborgen ist, ist auch vor ihnen verborgen.
- **Jemand konnte einen Tag vor dem Geburtstag ein Jahr älter werden.** Auf einem Gerät mit einer Zeitzone hinter UTC lagen das Alter hinter dem Geburtsdatum und das Alter in einer eigenen Spalte beide einen Tag vor sich selbst. Ein Geburtsdatum wird jetzt als der Tag gelesen, den es nennt, egal wo der Lesende sitzt.
- **Ein aus einem Datum berechnetes Alter konnte eine alte Zahl zeigen.** Das Profil eines Mitglieds und das Formular, auf dem seine Antworten gegeben werden, zeigten, was einmal in das Feld geschrieben worden war, statt der Zahl, die sich ergibt, und das änderte sich nie wieder. Es wird jetzt überall berechnet, wo es steht, und angezeigt, ohne zum Überschreiben angeboten zu werden.
- **Ein Mitglied wurde beim Öffnen des eigenen Vorgangs vier Mal abgewiesen.** Die Seite bot das Formular zur Wahl des Ersatzteils allen an, die den Vorgang sehen konnten, und dieses Formular fragt dafür das gesamte Inventar und alle Mitglieder der Wache ab. Das Ersatzteil wird jetzt nur noch dort angeboten, wo das Lager auch gelesen werden darf; sonst steht eine Zeile, dass die Wache es einträgt.
- **Ein Mitglied konnte einen Schritt abbrechen, der ihm nicht gehörte.** Jeder offene Vorgang bot die Schaltfläche zum Ablehnen allen an, die ihn ansahen, gleich auf welche Partei der Schritt wartete. Der Bereich wird jetzt nur noch der Partei gezeigt, die an der Reihe ist, und wem das Übergehen erlaubt ist.
- **Ein Termin am späten Abend zeigte keine seiner Anmeldungen.** Die Seite ermittelte den Tag des Termins nach der Uhr des Lesers, während alles, was zu ihm gehört, nach der Uhr der Station abgelegt wird: ein Termin, der in der Zeitzone des Lesers nach Mitternacht endet, oder aus einem anderen Land als dem der Station betrachtet, zeigte deshalb eine leere Anmeldeliste, aus der sich weder Checkliste noch Umfrage starten ließ. Der Tag wird jetzt durchgehend nach der Uhr der Station gelesen.
- **Das Umbenennen eines Datums leerte jedes daraus berechnete Alter.** Ein Altersfeld hielt sich am Namen der Frage fest, aus der es zählt, sodass eine Umbenennung die Spalte leer zurückließ, ohne zu sagen warum. Es hält sich jetzt an der Frage selbst fest, und bestehende Altersfelder werden übernommen, wie sie sind.

### Änderungen

- **Eine Person wird auf eine Art geschrieben.** Ember hat an etwa hundertsechzig Stellen selbst entschieden, wie ein Name zu schreiben ist, weshalb eine Liste und eine Auswahl sich über dasselbe Mitglied uneinig sein konnten. Jetzt gibt es eine Stelle, die die Frage beantwortet, und eine Art zu fragen, in Java wie in der Datenbank, und einen Test, der beide auf dieselbe Antwort festnagelt.

## v26.17.3

### Fehlerbehebungen

- **Ein Termin wurde in der Uhr des Servers geschrieben statt in der der Wache.** Ein Abend von 09:00 bis 14:00 kam im Benachrichtigungs-Feed als 07:00 bis 12:00 an, weil die Maschine, die den Feed baut, UTC führt und die Zeitzone der Wache nie gefragt wurde. Zeiten stehen jetzt dort, wo der Termin stattfindet, und eine Wache ohne eingetragene Zeitzone liest weiterhin in UTC. Auch die Zeit in einem exportierten Selbstcheck folgt der Wache, und eine Kalenderdatei einer Wache mit unlesbarer Zeitzone fällt nicht mehr auf die des Servers zurück.
- **Die Erinnerung an einen Termin kurz nach Mitternacht kam einen Tag zu früh.** Auf welchen Tag ein Termin fällt und welcher Tag gerade ist, wurden beide in der Uhr des Servers bestimmt, sodass halb eins in der Wache als der Abend davor zählte und jede Erinnerung dafür einen Tag danebenlag.

## v26.17.2

### Verbesserungen

- **Ein Beacon erfährt jetzt, was ein Fehler überhaupt war.** Ein Fehler kam als Logger-Name, Stufe und Anzahl an, und bei einer Warnung ohne Exception ist das alles, was es gibt, sodass mehrere verschiedene Fehlschläge derselben Klasse als eine Zeile ankamen, die keinen davon benannte. Was dabei geloggt wurde, reist jetzt mit, ohne Mailadressen.
- **Eine weitergegebene Meldung kommt mit dem Bildschirm an, über den sie geschrieben wurde.** Browser, Fenstergröße, die Rechte der schreibenden Person und die letzten Anfragen der Seite reisen mit, jede Adresse ohne ihre Parameter, so wie die Seite selbst schon vorher. Wer sie geschrieben hat, bleibt weiterhin in der Wache. „Der Knopf tut nichts" benennt für sich genommen keinen Fehler.

### Fehlerbehebungen

- **Ein Link auf eine andere Seite dieser Instanz war kein Link.** Alles, was als Pfad geschrieben ist, und so ist alles innerhalb von Ember geschrieben, verlor auf dem Weg zur Anzeige seine Adresse und kam als unterstrichener Text an, dem niemand folgen konnte. Der Link in dem Eintrag, den Ember nach einer Aktualisierung schreibt, war einer davon. Links auf andere Seiten waren nie betroffen.
- **Ein Systemeintrag bot einer Wache drei Schaltflächen, die nicht funktionieren konnten.** Bearbeiten, löschen und nachsehen, wer ihn gelesen hat, sind Rechte einer Wache an ihren eigenen Einträgen, und ein Eintrag der Instanz gehört zu keiner Wache, also antwortete jede davon mit „nicht gefunden". Sie werden nicht mehr angeboten, und ein solcher Eintrag trägt jetzt das Ember-Logo statt der Initialen eines Namens, den niemand hat.
- **Eine Benachrichtigung schrieb das Datum so, wie eine Datenbank es schreibt.** Die Erinnerung an einen Termin nannte als Tag 2026-09-19 statt 19.09.2026, ebenso jede andere Benachrichtigung mit einem Tag darin: neue Termine, eine noch fehlende Antwort, ein zurückzugebender Selbstcheck.

## v26.17.1

### Fehlerbehebungen

- **An ein Beacon gemeldet kam nie etwas an.** Jeder Fehler und jede Meldung wurde vor dem Speichern als unsigniert abgewiesen, weil eine Zustellung den Schlüssel nicht mitbrachte, an dem ihre Signatur geprüft wird. Die Abweisung stand auch in keinem Log, sodass Betreiber ein Beacon mit Zahlen darauf und sonst nichts sahen, selbst auf einer Instanz, die an sich selbst meldet. Zustellungen bringen den Schlüssel jetzt mit, und ein Beacon, das eine abweist oder nicht erreichbar ist, steht im Log.
- **Eine Meldung ließ sich nicht von Hand weitergeben.** Meldungen erreichten ein Beacon nur beim Schreiben, alles vor dem Einschalten des Schalters blieb also liegen, ohne Möglichkeit es zu senden. Die Liste bietet jetzt, was das Fehlerprotokoll schon immer bietet: erst sehen, was genau hinausgeht, dann senden.

## v26.17.0

### Neue Funktionen

- **Ein Termin kann Dateien mitgeben.** Der Laufzettel, das Formular zum Mitbringen oder der Plan für den Abend wird aus der Mediathek gewählt oder dort hochgeladen und steht auf der Terminseite zum Herunterladen bereit. Jede Datei ist entweder für alle da, die den Termin sehen dürfen, Partnerwachen eingeschlossen, oder bleibt bei denen, die den Termin durchführen.
- **Das Änderungsprotokoll steht auf Deutsch und kommt mit der Instanz.** Die Seite holt die Einträge nicht mehr aus dem Browser heraus bei GitHub, sondern von der eigenen Instanz. Sie bleibt damit auch ohne Verbindung nach außen lesbar, und niemand muss dafür eine Adresse bei GitHub hinterlassen.
- **Nach einer Aktualisierung steht in den Neuigkeiten, was sich geändert hat.** Wurde die Instanz auf eine neue Version gehoben, schreibt Ember einmalig einen Eintrag für die Verwaltung, mit den Änderungen genau dieser Version und einem Link auf das vollständige Änderungsprotokoll.

### Änderungen

- **Ein neues Recht für die internen Daten eines Termins.** Es öffnet das benötigte Material und die Dateien, die der Termin zurückhält, ohne etwas ändern zu dürfen. Wer Termine bearbeiten darf, hat es bereits, es muss also nichts neu vergeben werden; gib es den Leuten, die Abende durchführen, aber kein Inventar führen.
- **Eine Profilfrage wird einmal geschrieben und denen gestellt, die sie beantworten sollen.** Mitglieder → Konfiguration hat keinen Reiter je Mitgliedsart mehr: auf der einen Seite stehen die Fragen, jede einmal geschrieben, und daneben steht zur gewählten Frage, wer sie gestellt bekommt, beliebig viele Mitgliedsarten und beliebig viele Gruppen, auf demselben Bildschirm, den ein Verband für seine eigenen Fragen nutzt. Ob eine Antwort erwartet wird, wie breit das Feld gezeichnet wird und ob diese Zielgruppe hineinschreiben darf, kann sich je Zielgruppe unterscheiden, und die Reihenfolge jedes Formulars gehört diesem Formular.
- **Eine neue Frage wird niemandem gestellt, bis du sagst, wem.** Sie ist aufgeschrieben und erreicht kein Profil, solange keine Zielgruppe genannt ist, was die Liste an der Zeile sagt, statt es bemerkt werden zu lassen. Anwärter sind jetzt eine eigene Art, eine Wache kann sie also weniger fragen als ein Mitglied.
- **Ein Formular wird dort angeordnet, wo es gelesen wird.** Die Vorschau neben den Fragen ist das Formular selbst: zieh ein Feld dorthin, wo es hingehört, zieh an seiner rechten Kante, um seine Breite zu setzen, und setz einen Abstand hinein, der das Nachfolgende weiterschiebt, damit zwei Fragen unter zwei anderen stehen.
- **Mehrere Fragen lassen sich in einem Zug an eine Zielgruppe stellen.** Hak sie in der Liste an, wähl eine Mitgliedsart oder eine Gruppe, und jede einzelne davon wird dieser Zielgruppe auf einmal gestellt.

### Fehlerbehebungen

- **Dieselbe Frage konnte zweimal auf einem Profil stehen.** Hatte eine Wache dieselbe Frage einmal für die eine und einmal für die andere Mitgliedsart aufgeschrieben, begegnete sie jemandem, der als beides zählt, zweimal, und die beiden Fassungen sammelten verschiedene Antworten auf das, was sich als eine Frage liest. Fragen gleichen Namens und gleicher Art werden bei der Aktualisierung zu einer zusammengeführt, wobei die ausgefüllte Antwort erhalten bleibt, wenn nur eine der beiden eine hatte; der Stand der Fragen und ihrer Antworten von vor der Zusammenführung wird zuvor in eine Datei im Datenverzeichnis geschrieben.
- **Problemmeldungen wurden nie an ein Leuchtfeuer weitergegeben.** Das Einschalten von "Problemmeldungen automatisch weitergeben" merkte sich die Wahl und änderte nichts: eine geschriebene Meldung lag auf der Instanz und ging nicht weiter, ein Leuchtfeuer sammelte also Fehler und Zahlen, aber kein einziges Wort von jemandem. Meldungen werden jetzt weitergegeben, sobald sie geschrieben sind, und die Abfrage in der Adresse der Seite bleibt zurück.
- **Ein Leuchtfeuer wies alles ab, was ihm gemeldet wurde.** Meldete eine Instanz nicht zufällig an sich selbst, wurde jede unterschriebene Sendung als an ein anderes Leuchtfeuer gerichtet abgewiesen, der Betrieb sah also ein leeres Leuchtfeuer ohne Hinweis darauf, warum. Ein Leuchtfeuer wägt eine Sendung jetzt gegen die eigene Adresse ab, und das ist die aus `api.baseUrl`.
- **Was einem Termin fehlt, bot eine Schaltfläche an, die den Drückenden abwies.** Das benötigte Material beim Verband anzufragen wird jetzt nur noch denen angeboten, die überhaupt Material anfragen dürfen; alle anderen sehen weiterhin, was fehlt.
- **Ein später ausgefüllter Bogen trug unter den vergangenen das falsche Datum.** Ein Abend, der Tage oder Wochen später eingetragen wurde, stand unter dem Datum der Eingabe statt unter dem des Abends und sortierte sich zwischen die Bögen jener Woche, sodass ein Abend im Juli ganz oben mit September stand. Vergangene Bögen tragen jetzt ihr eigenes Datum und laufen vom jüngsten Abend abwärts.
