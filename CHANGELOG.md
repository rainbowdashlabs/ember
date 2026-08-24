# Changelog

## v26.13.0

### New Features

- **The station has a media library.** Everything the station has uploaded lives in one place at `/station/media`, with folders, tags and a search, and every editor reaches into it. Anyone who may log in can upload and insert what they uploaded themselves, so a picture belongs in a board ticket as readily as on a public page.
- **News entries can hand a file over.** An entry carries attachments picked from the library, named and ordered as the author likes, and they appear as downloads under the text rather than inside it. They travel with the blog feed and to partner stations, so a reader elsewhere is handed the same file.
- **A news entry can be written with the page editor.** An entry can be switched from the plain text field to rows and columns, with images beside the text, callouts, galleries and code blocks. The switch is one way: the text already written moves into a single block and nothing is lost, but an author who wants the plain field back writes a new entry.
- **The instance can say something to every station at once.** Under Stations → System news an administrator writes a notice that appears in every station's own news list, from Ember rather than from anyone in the station, with a System badge beside it. It can be limited to certain member types, it notifies only when asked to, and correcting or withdrawing it does so everywhere at once.
- **The instance keeps a library of its own.** Pictures and files used in a system notice belong to the instance rather than to one station, so a station clearing out its unused files cannot leave a notice with a broken picture. They are uploaded and picked while writing the notice, and served to every station that reads it.
- **A knowledge-base article can be written the same way.** A markdown article switches to the page editor as a news entry does, which is what a training document with a diagram beside its explanation needs. Search, the PDF export and the version history keep working; old versions can be read but not restored, because what is stored is derived from the blocks.
- **A station decides how gear moves between it and the body above.** Under Inventory → Configuration the chain an exchange, a return or an issue walks is edited step by step: what each one is called, who confirms it, which of the two items it is about and where that item is afterwards. Which chain applies follows the owner of the gear, so one inventory holding both can reach different chains for different rows.
- **Stations can belong to an association.** An administrator creates one under Admin → Associations, and whoever runs it gets their own area at `/cluster`, with rights counted separately from those held at any station.
- **A station asks to join an association, and the association answers.** Only a station's owner can apply, under Manage → Association, and the association approves or refuses it with a reason both sides can read. An association can also create stations of its own, which belong to it from the first moment.
- **Stations in an association are connected without anybody arranging it.** Joining one connects the station to the association and, unless the association turns that off, to every other station under it, with calendars, knowledge, gear and the rest already shared both ways. Those connections are the association's, so neither station can end one, and the one carrying the association's own content cannot be paused either.
- **An association decides what its stations may use, how they look, how much room they have and where it is kept.** A module it switches off is locked at every station, with the association named as the reason and nothing already put in it deleted. Colours, theme, shape and logo are handed down as a starting point or locked outright, one by one, and storage is shared out of a pool the instance grants. An association can also keep its files on storage of its own, for itself alone or for every station under it, and either let its stations bring their own or hold them to what it decided. Nothing moves on its own: a station whose files are not yet where the decision says they belong is listed as such and carried across one at a time, and a station joining or leaving takes its files with it before the membership changes.
- **An association has its own members, and one of them can look after every station at once.** Who acts for the association is set there, by role, by grants of their own or through a group, all three separate from anything they hold at a station, and all three edited with the same permission picker a station edits its own members with. Nothing at a station makes somebody a member of the association above it, however senior they are there; belonging is always granted at the association, and it opens its pages and nothing it governs. Somebody trusted with it can also search and edit the people at every station under the association, except their own membership and any station's owner.
- **An association can ask its own questions in every member's profile.** The fields it adds appear in the same form as the station's own, marked as the association's, and are read-only at the station unless the association says otherwise. Changes land in the one change history a profile already had, and the answers are cleared when a station leaves while the history stays.
- **An association keeps its own gear and answers for it.** It sees every piece it owns and where each one is, and the steps of an exchange or a return that only it can confirm wait in a list of its own. An association that does not keep its gear here says so, and then its stations work exactly as they did before.
- **An association can tell its stations something.** News it writes, appointments it makes and articles it files reach every member station, all with the association as the sender, and it writes them on the same screens a station writes its own with: folders, tags, versions and restrictions in the knowledge base, categories and registrations in the calendar, the page editor in both. Nothing is copied: the stations read what was written once, over the connection they already have.
- **The directory groups stations by their association.** Stations that answer to the same association appear under its name instead of scattered through the list, and everything else keeps its own place below. An association has no page of its own; only its stations do.

### Improvements

- **A question under a system notice reaches the instance.** Anyone may comment on a notice from the instance. A station sees the comments written in its own station, and the administrator sees all of them with the station each came from, so a question asked under a notice can be answered.
- **Every text editor can insert a picture.** Where an editor only accepted a pasted address before, it now opens the media library to browse, search, upload and insert. News, board tickets, event descriptions, the knowledge base and the page editor all gain it at once.
- **Clearing out unused files leaves what members brought in.** A file nothing points at is still offered for removal, but one somebody uploaded themselves is kept: a picture can outlive the first place it was used.
- **Gear kept for the body above a station finds its owner when the station joins one.** Equipment already recorded as belonging to the municipality or the association keeps its place, its size and whoever has it, and the association it now names sees it in the list of what it owns. Nothing is moved or recreated, and an exchange already under way carries on.
- **The demo instance has an association over it.** The demo station now answers to one, alongside a station the association made itself, and it comes with people holding each of the association's roles, gear resting in every state a piece can be in, two questions in its members' profiles, and news, an article and an appointment of its own. A neighbouring station's request to join is still waiting, so the screen that answers them has something on it.
- **An exchange opens as the chain it is walking.** The history button now leads to the whole run rather than a list of status changes: every step with the party it belongs to, the ones behind it stamped with who acknowledged them and how, and on the current one either the single button you may press or the sentence naming who is being waited on.
- **Members report their own gear missing.** Under Profile → My inventory anyone can say they cannot find something assigned to them, and a guardian can say it for the person they look after. A station can ask for a short note with the report, under Inventory → Configuration; the note is shown beside the item afterwards.
- **A station asks the association to replace something that is gone.** Losing track of a piece is the station's own business and reaches nobody; asking for a replacement is a separate step on the item's page, carrying the manager's own note beside what the member wrote. The association answers it in its movements queue by sending a replacement or refusing with a reason, and the piece stays recorded as missing either way.
- **An association takes people on at its stations.** The list of members across the stations now offers it, and the first question is which station they join, because a member belongs to one. Somebody who is not meant to sign in is recorded the same way a station records them.
- **An association orders gear into its own store.** Its Inventory → Procurement tab records what has been ordered without asking which person it is for, because an association buys for its store and hands out afterwards. Marking an order arrived puts the piece in the association's store, ready to be sent to a station.
- **What an association requires of its people is counted at the station.** A requirement the association writes now stands on the station's own requirements page, named after the association and not editable there, and gear the association owns counts towards it. There is one definition rather than one per station to keep matching by hand.
- **The association's figures are broken down by size.** Its Inventory → Statistics tab now lists how many of each size it owns and how many of those are still in its own store, which is what somebody ordering a batch actually needs.
- **An association reads and adds to a member's documents.** The page it opens on somebody at one of its stations now shows what is filed about them under the questions, and offers to put a document there. Labelling, binding it to further members and removing it stay with the station, because the document belongs to the station that holds the person and stays there if that station leaves.
- **An association sends gear out to its stations.** Under Inventory → Stock it opens a screen that asks for a station, then for the pieces, and sends the lot as one consignment: the station confirms one arrival rather than one per piece, and everything in it counts as on its way until then. Only what is resting in the association's own store is offered.
- **An association says what a loss report has to carry.** Under its Inventory → Settings it asks for nothing, for a note, or for a document as well, and a report short of that is refused before it is raised. What arrives is read in one place: both notes with the names behind them, and the file beside them.
- **A movement tells whoever's turn it is, not always the station.** When a step is acknowledged, the message goes to the party the next step belongs to and to nobody else, so its arrival is itself the signal that something is waiting. A refused movement tells both ends, with the reason where the member asked.
- **Ember runs on 64-bit ARM machines.** It is now built for ARM as well as for x86, so a Raspberry Pi 4 or newer serves an instance with nothing extra to set up. The machine has to be running a 64-bit system, which on a Raspberry Pi means the 64-bit Raspberry Pi OS rather than the 32-bit one.

### Changes

- **Files move out from under Pages.** What was Pages → Files is now Media in the sidebar, at `/station/media`. It is the same library with the same contents; it simply belongs to the station rather than to its website.
- **Station media moves to a new place on disk.** On the first start after the upgrade each station's media is moved into its new folder, one station at a time, and that station is read-only while its own move runs. An interrupted move picks up where it stopped on the next start, so nothing has to be repeated by hand.
- **A station cannot rename, delete or lend on gear it does not own.** Handing it to a member, shelving it, checking it and reporting it missing all still work, because those say where the gear is. What it is, and who else may borrow it, stays with the owner.
- **A station in an association cannot be moved to another instance.** Transferring it would leave the association holding a station that is no longer there. Leave the association first, and the transfer works as before.
- **An item says who owns it.** Every piece of gear is recorded as belonging either to the station itself or to the body above it, the municipality or the district association, in place of the internal and external labels it carried before. Members are no longer offered as owners, because gear a member bought was never tracked here.
- **An item also says who has it.** Alongside its owner, a piece of gear now records where it actually is: in its owner's store, at the station, with a member, lent to a partner or missing. An item's page shows both, and the lists of what a station holds follow what it has rather than what it owns.
- **An exchange is a chain of steps a station works through.** Gear moving between a station and the body above it walks a named chain instead of five fixed statuses: announced, taken back, posted, replacement received, replacement handed over. Every step is one somebody at the station actually saw, and the chain can be renamed or reshaped under Inventory without losing the wording a finished exchange was walked under.

### Security

- **A news entry could be read from another station.** The lists never offered it, but asking for an entry by its address answered in full, to anyone logged in anywhere, and the comments under it with it. An entry is now readable only by the station it belongs to, and only by the people it is addressed to.

### Fixes

- **The fairness ranking stayed empty for an event in no category.** Deciding who gets a place shows how often each member was registered, accepted and turned away, and for an event that belongs to no category that list could not be fetched at all. It now covers everything the station has done, which is the only sensible comparison when the event names no category to compare within.
- **Partnerships did not follow a station that moved.** When a station was transferred to another instance, the partnerships other stations kept with it were meant to point at its new home, and the step that does that failed outright. They now follow the station, so a partnership keeps working without anyone touching it.
- **A demo instance showed no station logo and an empty file library.** The rule that stops uploads on a demo was also refusing to hand back what was already stored there. Reading works again, while uploading stays disabled.
- **The login button appeared for people who were already signed in.** On a page rendered by the server there is no way to tell whether the browser is carrying a session, and it offered the login anyway until the session came back. It now shows neither until it knows, and the account menu takes its place as before.
- **Signing out and back in on the same browser kept the previous person's stations.** The list of stations and associations an account may act for outlived the session that fetched them, so the next person to sign in was offered the ones before them until the page was loaded afresh. Signing out clears them.
- **A shared article could be listed twice.** An article reachable both on its own and through a shared folder appeared once for each way in. It is listed once, however many shares reach it.
- **Gear a partner had borrowed still looked free.** Equipment lent out to a federation partner could be handed to a member or offered in another lending request while it was away, because only the assignment was checked. It is now held back until the partner has given it back.
- **Exchanged gear the station does not own.** In an inventory holding gear of both owners, completing an exchange put the returned item into the station's free stock even when the station had never owned it. The exchange now follows the owner recorded on the item.
- **The installer set up an instance the machine could not run.** On a machine Ember is not built for it wrote everything out and started containers that stopped again at once, over and over, with only a format error to go on. It now checks the machine before it writes anything and says plainly what is wrong.
- **A second-factor confirmation could stop counting shortly after it was given.** A session renews itself in the background while you work, and the renewal used to forget both the confirmation just made and any device you had asked it to remember; it now carries them over.
- **A refused security confirmation left the action with nothing to answer.** Where the confirmation was accepted but the action turned down again, no new prompt appeared and only a general error showed; it is now offered once more, and a message says plainly when the instance keeps refusing.
- **Staying signed in made the session shorter, not longer.** Ticking the box on your own machine gave half an hour where leaving it unticked gave a full hour; a device you vouch for now keeps its session for thirty days, and an instance that set `auth.sessionMinutes` itself keeps the value it chose.

## v26.12.0

### New Features

- **Members have a document store.** Every profile carries a tab for the files that concern that member, and Members → Documents holds the whole store, a page at a time. A document can belong to several members at once, or to none of them, and it is bound to further members while it is open. Images, text files and PDFs are shown in the application rather than only offered for download, and the tile shows a picture of the document, for a PDF its first page.
- **Documents can be searched by what they say.** The search reads the title and, where a file can be read at all, the text inside it, so a PDF is found by a word in it. Free-text labels sort the store further and are written as they are needed rather than set up in advance.
- **A data export carries the documents too.** What somebody receives when they ask for their data now lists the documents held about them and holds the files themselves, the withheld ones included.
- **A document can be kept beyond a membership.** What is marked to be kept survives its members being marked former, which is what a legally binding document needs; everything else goes with them. A document can also be kept from the members it belongs to, so it is seen only by those who may read other members.
- **Profile fields can be arranged rather than only listed.** Each field takes a whole row, a half or a third, so the short ones stand beside each other, and a new field type is a plain heading that asks for nothing and appears in no export. Under Members → Configuration a preview shows the arrangement as it will be filled in.

### Fixes

- **Rights stay whole.** In some cases it could happen that a member held only part of what their role allows, so pages they are entitled to refused to open until the instance was restarted. A role now hands out all of its rights or none, whenever it is asked.
- **A field template on the group tab lands in the chosen group.** Fields added from a template belonged to no group at all and had to be assigned by hand afterwards. A template now takes the group that is being configured.

## v26.11.12

### New Features

- **Ember installs itself in one command.** `curl -fsSL https://ember-panel.de/install.sh | bash` asks the handful of things that differ between installations, writes the compose file, starts everything and shows the login it created. It offers a port on the machine, which is what a VPN or a local instance wants, or an existing Traefik; PostgreSQL is brought along or joined where it already runs; and the directories for the configuration, the files and the database are chosen rather than assumed.
- **An installation can be clicked together beforehand.** Under `/install` the same questions are answered in the browser, which gives back a six-character code. The command carries only that code, so what has to reach the server is short enough to read out. The code lasts two hours, and the database password is never part of it.
- **A waiting list can work with ages.** A date field set to the type date of birth gives the list an age to go by: it can refuse a registration below a minimum age, and it marks everyone still under the age for joining so they are told apart from those merely waiting their turn. Those entries can be hidden while the list is worked through. An existing date field becomes the date of birth without losing the answers already given.

### Improvements

- **Mail left behind by a stopped delivery can be sent again.** The overview no longer only counts them: it lists each one and offers to put it back in the queue, singly or all at once. Mail that is being sent right now stays untouched.
- **The waiting list shows the date of birth in its own column and sorts by any of them.** Every column heading sorts, the list still opens on the highest score, and a column picked afterwards starts at the top of its own order.

### Fixes

- **A profile field made for a group stays with that group.** It appears under its group and can be filled in there. Fields that ended up without a group are offered for assignment at the top of the group tab under Members → Configuration.
- **Answers in the waiting list read the way they were given.** A date appears as a date and a yes as a yes, in every column a list has been set to show.

## v26.11.11

### Improvements

- **The log is narrowed by the class and the thread it came from.** Both are offered as lists with a count each, searchable so one that is too quiet to sit near the top can still be picked, and threads of the same pool count as one rather than as forty. A line's severity now carries its colour, and a line holding a stack trace says so before it is opened.

### Fixes

- **The link for changing your address opens a page.** Confirming a new email address leads somewhere that says what happened, and it says which of the two confirmations is still outstanding rather than reporting the change as done after the first one.
- **A selection field shows the choices it was given.** The options of a waiting list field appear in the list, come back when it is edited, and fill the dropdown on the public form and on an entry.
- **The scoring formula suggests the fields again.** Opening a bracket lists every field of the waiting list, the waiting-time values and the age function, instead of waiting for a letter that has to be guessed first.
- **The legal pages open when the address is entered directly.** `/privacy`, `/terms` and `/imprint` load whether they are reached through a link or opened fresh, and they say so plainly if the text cannot be fetched instead of failing outright. The shipped deployment files now set `NUXT_BACKEND_URL`, which an installation with its own files must set too.

## v26.11.10

### New Features

- **The instance log can be read from the application.** Under Monitoring the log of the running instance is searched by message or by logger and narrowed to the severities you want, without reaching the server. It is kept in the database only when an operator switches that on under Settings, where the severity and how many days to keep are chosen; the console and the log file always hold everything regardless.
- **A mail overview says where the post stands.** Under Monitoring there is now a page for what became of the email: how much waits, how much a provider accepted, what it reported back afterwards, and which provider each waiting message is standing at. Every provider shows what it has sent today against its allowance.
- **A provider a domain refuses is not asked again.** When a receiving domain turns away the sending server rather than the message, that pairing is remembered and the provider is skipped for that domain instead of spending an allowance on a refusal that is certain. The block lapses on its own after a week, and the mail overview names every one that stands and lets you lift it by hand once the matter is settled.
- **Stay signed in on your own device.** The login screen offers to keep you signed in, and only then does the session run for the long duration. Without it a session ends after an hour, which is what a shared or borrowed machine should get. It says nothing about the second factor: that stays a separate choice.

### Improvements

- **A stuck message says so instead of looking busy.** The mail overview marks a waiting message that no provider can currently carry, so a queue that will never move is told apart from one that is merely working through.
- **The log file no longer grows without end.** It rolls at 100 MB, keeps two weeks or 2 GB of history, and compresses what it rolls, instead of writing a single file per start that nothing ever removed.
- **Sessions may last up to thirty days.** An operator sets how long a session runs on a device somebody vouched for, and separately how long it runs otherwise, under Settings → Security. Signing in over and over frustrates more than it protects, and signing out or changing a password still ends every session at once.
- **The consent text has room to be read.** The window asking to agree to data processing is wider on the login screen and shows more of the document at a time, rather than presenting it in a column the width of a password field.
- **The platform statistics count more.** Mail on its way out, delivery blocks that stand, accounts secured with a second factor, upcoming appointments and their registrations all have a figure now, and two new charts show registrations across the last thirty days and how attendance was answered overall.

### Fixes

- **Chart headings and legends stay clear of the chart.** A heading, a legend and the label on the value axis each keep their own place wherever a chart shows them together.
- **Panels keep an even distance on the statistics page.** Every block on the page is separated the same way, including the first one under each heading.

## v26.11.9

### New Features

- **Mail providers are one list, worked from the top.** An instance and a station each keep an ordered list of providers instead of one provider plus a set of stand-ins behind it. Every entry is edited, moved, tested and given its own delivery address the same way, and the first is simply the first.

### Improvements

- **Every provider carries its own daily allowance.** Free tiers are sold by the day, so an entry that has sent its share hands the post to the next one instead of holding it until tomorrow. A station's overall daily and monthly caps give way to this: the allowance now belongs to the provider that actually has one.
- **A test mail goes to any address you name.** Any provider in the list can be sent a test mail, not only the one currently carrying the post, and to any address rather than only your own. A provider further down is no longer found to be misconfigured at the moment everything above it has run out.
- **Every provider has its own address for delivery reports.** The address ends in the report format the provider sends, so a list holding two different providers is offered two different addresses, for a station as much as for the instance.
- **A section for each email provider is ready to switch on.** The privacy policy ships one written out for Brevo, Sweego, Twilio SendGrid and rapidmail, naming the company behind it, its address and where it processes, plus a blank one for any other server. All ship switched off, and only the one naming the provider in use belongs in the document.
- **A section for Cloudflare is ready to switch on.** An instance reached through Cloudflare passes every visitor request through their servers, so the privacy policy ships a section saying what is processed there, on what legal basis, and that the contracting company sits in the United Kingdom while processing also happens in the United States.
- **You choose which shipped sections to load.** Under Settings → Legal each row leads with the heading it carries, says whether it would replace a section already in the editor, and offers its full text to read beforehand. Selecting everything takes the document as Ember lays it out and leaves the alternatives, such as the several email provider sections, to be picked by hand.

### Fixes

- **Icons arrive with the page itself.** The public pages, the login page and the help center carry their icons in the page the server sends, so they are there from the first moment and stay there when scripts are switched off.
- **Queued mail is no longer stranded at the daily limit.** Reaching the limit leaves the remaining messages waiting for the next attempt instead of leaving them behind.
- **A malformed request says what is wrong with it.** A request the server cannot read is answered with the reason and the field it stumbled on, rather than a bare failure.

## v26.11.8

### New Features

- **Read and write permissions for the knowledge base.** A folder or file can now say what its audience may do with it, not only who sees it: read only, read and edit, or full access including deleting and publishing. A group can be given reading rights on a directory without being able to change anything in it.
- **Ask questions when someone registers for an event.** An event can ask everyone signing up for extra details - shirt size, number of guests, what they are bringing - as text, number, yes/no, date, choice or member. Each question can be mandatory and can start from a default value.
- **Answers stand next to the registration.** The answers marked for the list appear beside the member's name in the registration list, and number questions get their total above it. Event templates carry their questions into every event created from them.

- **Placeholders for details that repeat across the legal documents.** A name in double curly braces stands in for a value - the operator's name, address or email address - and is filled in once under Settings → Legal. Ember finds every placeholder written into any document by itself and applies the same value across all document types and languages.
- **The privacy notice lists what stays in your browser.** Privacy policy and consent text carry a section naming every value the application keeps in your browser, what it is for and how long it stays - grouped into what login needs, what a single feature needs and what only remembers a display preference. The section writes itself from the application, so it stays correct as the software changes.

### Improvements

- **The attendance buttons say what they do.** Marking someone present, absent or excused now carries a name, so a screen reader announces it and hovering shows it.

- **Steps are written straight into the procedure.** Adding a step puts an empty row in the list instead of asking for its title in a dialog first. Every field of a step was already editable in that list.
- **A profile field can be marked as the date of birth.** The new field type behaves like a date field and can serve as the source of a calculated age. A station has one of them: once a field carries it, the type is offered again only after that field is deleted or changed to something else.
- **Terms of service now cover what the product actually does.** The shipped terms describe public pages, sharing with partner stations, AI-assisted question generation, feeds and exports, the obligations of a station admitted to an instance, and how a station's use ends. They are laid out in six parts that can be reordered or switched off individually.
- **The shipped imprint asks to be filled in rather than edited.** It carries placeholders for the operator's name, address, phone number, email address and the person responsible for content, so an instance is ready once those values are entered. A placeholder left empty stays visible in the text instead of leaving a blank line. New operator setting: `api.placeholderFile`.
- **A document written by a lawyer can be taken over as it is.** Under Settings → Legal a document is imported as a file or pasted as text: Ember cuts it into sections, takes the numbering out of the headings and turns references like "§ 12" into links to the section they mean. Numbers that match no section stay untouched and are listed before anything is saved.
- **The terms number themselves.** Paragraph numbers are assigned when the document is shown, so switching a section on, off or into another position renumbers the whole document and every reference in it. A reference whose section is gone is marked in the text instead of pointing nowhere.
- **A section for the email provider is ready to switch on.** Privacy policy and terms of service ship a section describing the service that sends the email, switched off until an instance fills in the provider, its address, its server location and how long it keeps delivery logs.
- **Legal documents can start from the shipped templates.** Under Settings → Legal, a section of the document Ember ships can be loaded into the editor - one at a time or all at once. A section of the same name is replaced, everything else stays as it is, and nothing is written until you save.
- **You decide what stays in your browser, group by group.** Alongside the technically required values there are now two groups you allow or refuse separately: what individual features remember, and what your view settings remember. The choice is offered with the consent and can be changed later under Account → Data & account; taking a group back deletes its values at once.
- **Privacy policy and terms are never blank.** If an instance has no documents of its own, the pages serve the ones Ember ships instead of an error, and the shipped set is laid down where the instance actually reads its documents from.
- **The section on browser storage cannot fall out of date.** It is generated rather than written, so it can only be shown, hidden or moved. Both privacy policy and consent text pick it up, and a change to it prompts for consent again like any other change.
- **The knowledge base only offers what you may actually do.** Editing, deleting and creating appear where your permission allows them, an entry you may only read is marked as read only, and a file you may only read names the folder that decided it. Existing stations notice nothing until they set their first permission.
- **Answers to registration questions are complete for the organisers.** Whoever may edit the event sees every answer including the notes, plus totals per question: numbers are added up and choices counted per option. A question can also be marked as belonging to the organisers, in which case it is neither asked of members nor visible to them.
- **The public calendar of a station opens.** Visiting it shows the station's dates, and the subscription link for a calendar application is offered beside them.
- **A station's public pages arrive complete.** The station name, its menu and its blog, wiki and calendar come with the page the server sends, so search engines and link previews see them.
- **Public pages arrive complete.** The station directory and the imprint, privacy and terms pages carry their content in the page the server sends, so search engines and link previews see it. Visitors reach the same pages as before, a moment sooner.
- **Files shared by partner stations open.** A shared file in the knowledge base opens like one of your own, both from the file list and from a search result, instead of only offering a copy. Text and Markdown files show their content and take comments; other formats still have to be copied into your station first.
- **Question catalogues and test sheets shared by partner stations open.** A shared question catalogue shows its categories and question count, a shared test sheet its sections and points. Both can still be copied into your own station from the page.
- **Knowledge base files save as PDF.** Markdown and text files can be downloaded as a PDF carrying the station name and logo - from the file, from the file list, from a file a partner station shares, and from a page on the public wiki. Headings, lists, tables, quotes and code blocks are kept; images are replaced by their description.
- **Tile and list view of the knowledge base offer the same actions.** Removing a favourite is available in both views, and the buttons on every entry name what they do.
- **Guardians hand out access to the members in their care.** Under Profile → Managed profiles a guardian sets the email address of a member they look after and switches signing in on or off. Allowing it sends the invitation to set a password; refusing it ends the sessions that are open, and a new address does the same.
- **Email falls back to another provider instead of getting stuck.** An instance can list further providers after its first one, each with the number of attempts it gets before the next takes over. A message the receiving side refuses because of the relay itself - a sending address on somebody's block list, for instance - moves straight to the next provider rather than being tried against the same refusal.
- **Ember learns whether an email actually arrived.** A mail provider only confirms that it took the message; what happens afterwards - delivered, bounced, blocked - is now reported back and recorded against the email. Under Settings → Mailing there is an address to paste into the provider (for Brevo under Settings → Transactional emails → Webhook), and Ember generates the key it contains itself.
- **The language of system emails is set for the whole instance.** Under Settings, an operator picks the language emails are written in for accounts that belong to no station - self-registration and the administrator created at first start, which were written to in English whatever the instance wanted. Accounts created from a station keep that station's language.

### Security

- **Guardians only see the profile changes of their own children.** The change list, the pending overview and the acknowledgement of a change are limited to the members a guardian manages; reviewing the whole station stays with the permission meant for it. Nothing changes for anyone holding that permission.

### Changes

- **One menu for the actions on a knowledge base entry.** Editing, downloading and deleting an entry sit in a single menu instead of a row of icons. An entry offering only one action keeps that action as a plain button.
- **Quiz and examinations are named for what a station uses.** The shared menu entry reads "Quiz & Prüfungen" while both are switched on, and "Quiz" or "Prüfungen" when only one of them is. With both in use, the quiz pages and the examination pages sit in a section each instead of in one list of five.
- **Shared knowledge base needs matching versions.** Partner stations still on the previous version pause knowledge-base sharing until both sides have updated. Every other federated feature keeps working in the meantime.

### Fixes

- **Members find the forms they are meant to fill in.** The forms page lists what the station has opened to them instead of showing nothing at all.

- **Editing a procedure saves.** Changing the name, description, due date or visibility of a procedure keeps the change.
- **Choosing a station leads somewhere.** Picking a station opens the page that was asked for instead of returning to the station picker, also after a long break and when the link carries a station of its own.
- **Outstanding tasks appear after a long break.** Coming back to a station shows the forms and tests still to be completed instead of passing them by.
- **The administration area stays shut without the rights for it.** Opening an administration page without instance administration rights leads back to the station instead of showing a panel where nothing works.
- **The sidebar control for narrowing the menu stays on desktop.** It no longer appears on phones, where there is nothing to narrow.
- **Applications for a new station can be decided again.** The list shows the applications waiting for a decision, each with its accepting and rejecting buttons and its actual state.
- **A new instance starts with legal documents in place.** Privacy policy, terms of service, consent text and imprint are laid down on first start in German and English. A language that already holds a document keeps exactly what is there.
- **Members see the events of their station.** Opening the events page shows the station's events for everyone, not only for those who also record attendance.
- **The registration list of an event loads.** Opening an event and switching to its registrations shows who has signed up.
- **Question catalogues shared by partner stations appear again.** The catalogue list shows what partner stations share instead of leaving the shared section empty.
- **Filtering by partner station works across search and lists.** Picking a partner station in the knowledge base, catalogue or test sheet filters matches the entries from that station.
- **Switching off quiz or examinations clears the menu.** A station that switches off one of the two no longer keeps its pages in the sidebar.
- **The member import reports what it did.** Finishing an import shows how many members and helpers were created, how many were linked to a group and how many profile fields were filled, plus anything the import had to point out - instead of an empty page.
- **Chosen files are actually uploaded.** Picking a file - a wiki attachment or original, a folder icon, an avatar, a question or member import - sends the file itself, so the upload completes instead of failing as though no file had been chosen.

## v26.11.7

### New Features

- **Sortable and filterable item lists.** The item tables on the inventory detail and edit pages can show custom fields as columns, sort by any column, and filter by specific values - including by source and assignment state (assigned, not assigned, in storage, not in storage). A column picker shows or hides additional columns.
- **Custom fields when adding items.** The add-item dialog fills in the inventory's custom fields directly, and number fields check their allowed range while typing.
- **Help articles for the setup assistant.** Every step of the station setup assistant has its own help article, reachable from the help center menu and the search box.
- **A help article for every page.** The pages that still had no guide - event templates, a single news article, an inventory item, the answer generator for choice questions, the partner-station views and the reported-problems page - now have one, and every article in the help center is searchable from the menu.
- **Separate help articles for procedures.** The procedure list, the editor, the detail page and the templates each have their own article instead of sharing one general page.

### Changes

- **Federation compatibility is checked per feature.** When two connected stations run different versions, only the features whose data exchange actually changed are paused instead of the whole partnership - everything else keeps federating. The partner page shows which features are paused, and they resume automatically once both stations run the same version.
- **No size placeholder for unsized items.** Item lists and member inventory pages leave the size empty for items without sizes instead of showing a one-size label. Size transitions in exchange and procurement views are unchanged.
- **Item actions in one menu.** The action buttons on each inventory item row are collected in a single menu with labelled entries.
- **Custom item fields are easier to set up.** The technical key and the values of selection options are suggested automatically from the entered names, and fields can be reordered by dragging them (on phones, with up and down arrows).
- **One item edit dialog everywhere.** Editing an inventory item from the edit page now opens the same dialog as the detail page, including custom fields, storage container, and ownership. Custom field values are kept when saving.
- **Confirmations use the app's own dialog.** Deleting a file tag or folder, unassigning an item during a check, and handing over an item another member still holds all ask in a styled dialog instead of a plain browser prompt.
- **Number fields in attendance can have a default.** A number field on an attendance session takes a default value like every other field type, entered as a number rather than as text.

### Security

- **Boards you cannot see stay hidden.** Ticket comments, checklists, links, labels and history on a board you have no access to are no longer readable, and such a board now answers exactly as a missing one so its existence cannot be probed. Being unable to edit a board you can see is still reported separately.

### Fixes

- **Public blog article links work.** Opening a single article from a station's public blog loads the article instead of failing.
- **Reordering tickets on a partner's board works.** Dragging a ticket within a lane on a board shared by a federation partner saves the new order.
- **Creating custom item fields works.** Adding a custom field to an inventory saves correctly for every field type, including selection fields with options.
- **Custom item field values are kept.** Values entered for an item's custom fields show up again after saving, and editing an item's name, identifier, or size no longer clears them.
- **Opening a station loads it completely.** Picking a station on the cross-station overview reliably shows that station's profile and menu instead of occasionally returning to the overview or showing an empty page. Links from emails and feeds that point into a specific station open in that station.
- **A clear message when session data cannot be loaded.** If loading the session fails, an error message with a retry button appears instead of an empty page with a bare menu.
- **Forms and polls on public pages can be submitted.** A form or poll placed on a public page shows the consent checkbox and accepts the submission.
- **Contact form submissions open.** The submissions view for a contact form on a page lists the received responses.
- **Guardian names appear on waiting list entries.** The guardians of a waiting list applicant show their first and last name instead of a dash.
- **Attendance field entries save immediately.** Yes/no, date, selection and member fields in an attendance session are stored as soon as they change, rather than after a short delay.
- **Editing a form response works after new questions are added.** Opening a submitted response shows every question, including ones added after the response was sent.
- **The public waiting list and public blog switches are saved.** Turning either on or off under Station → Federation keeps the setting.
- **Replacing a presentation file works.** Uploading a new version of a presentation in the knowledge base replaces the stored file.
- **Saved member filters apply reliably.** Applying a saved filter on the member list works even when it was saved for a tab that is no longer available.
- **Ordering questions in quiz training keep all their items.** Moving an entry in an ordering question no longer leaves a blank item behind.
- **The feed notification switch shows its real state.** The feed channel under Account → Notifications reflects whether it is actually enabled.
- **Attendance help pages open with the help center menu.** The attendance, attendance settings and attendance settings editing help pages show the help center navigation.
- **Public station pages show their title in the header.** Pages under a station's public area display the page name in the header bar.
- **The relocation notice highlights its menu entry.** Opening the page that announces a station's move marks the matching menu entry as active.
- **Comments on a partner's knowledge base article can be deleted.** Removing your own comment on an article shared by a federation partner completes instead of failing.
- **Notifications for comments on partner news reach the right members.** Replies and mentions on a news article shared by a federation partner are delivered to the members of the station that owns the article.
- **Knowledge base search accepts any input.** Searching for text made only of punctuation returns no results instead of failing with an error.
- **Link previews stay clean when a page cannot be reached.** A link added to a knowledge base article keeps its address as the label instead of picking up the title of an error page.
- **Uploads with unusual file names are accepted.** Files whose name is missing or whose extension is written in capitals are recognised by type instead of failing.
- **The public forms and polls pages show their own titles.** Both pages display their own name in the header instead of the general forms title.
- **Reordering board checklist items and partner board tickets works.** Dragging a checklist entry into a new position on a board ticket is saved, and so is moving a ticket within a lane on a board shared by a federation partner.

## v26.11.6

### Changes

- **Faster barcode recognition.** The scanner camera records at a higher resolution and keeps focusing continuously, so QR codes and barcodes sharpen and are recognised more quickly.

### Fixes

- **The scan button only opens the scanner.** Tapping the scan button inside an add or edit dialog starts the camera without saving the dialog in the background, so no more entries are created with an empty code.
- **The camera turns off when scanning is cancelled.** Closing the scan dialog while the camera is still starting releases the camera immediately instead of leaving it running in the background and disturbing the next scan.

## v26.11.5

### New Features

- **Add several items to a storage container at once.** The scan button on the storage container page becomes an add button that opens a dialog to scan barcodes or search items by name or code, optionally showing only items without a storage place. Multiple items can be selected and placed in the container together.

### Changes

- **Filter the problem log by level.** The problem log in the admin area offers error and warning filter buttons, and acknowledging entries updates the list in place without a reload.
- **Member permissions show where they come from.** When editing a member, permissions already granted by the member type or one of the member's groups appear pre-selected and locked, labelled with their source. Selecting the station administrator permission marks all other permissions as granted.

### Fixes

- **All listed permissions can be granted.** The item hand-out, storage location, form submission, poll result and checklist permissions can be enabled in the permission picker.
- **Opening a station no longer bounces back to the overview.** Picking a station on the cross-station overview reliably lands on the station dashboard instead of occasionally returning to the overview right away.
- **Startup cleanup sweeps run reliably.** The orphaned-account sweep and the stale-transfer cleanup run on every server start instead of logging a warning and being skipped.

## v26.11.4

### Security

- **Visitors only see publicly listed stations.** The station directory at /discovery shows instance-visible stations and stations with public content only to signed-in users; without signing in, only stations that opted into public visibility appear.

### New Features

- **Send a test email from the mail settings.** The station mail settings and the instance mail settings offer a "Send test mail" button that delivers a real test message to your own address, so delivery can be verified end to end.

### Changes

- **Start page links to the demo when sign-up is closed.** On instances where station registration is disabled and a demo address is configured, the start page's main button opens the demo instead of the station application. Without a demo address it keeps pointing to the self-hosting guide.
- **Inviting a member creates the account right away.** Invited people appear in the member list immediately and can be assigned to groups, events and attendance before their first sign-in; the invite email asks them to set a password.
- **Invites to existing accounts join the station.** Inviting an email address that already belongs to an account adds that account as a member of the station instead of failing.
- **Pending invites convert on upgrade.** Invitations that were not yet accepted become member accounts during the upgrade and the previous acceptance page is removed; such members can be sent a fresh password link via the resend button in the member list.
- **Member list shows when a setup link expires.** For members who have not set their password yet, the pending marker shows until when the emailed setup link is valid.

### Fixes

- **Station applications confirm only after submitting.** The application page at /apply shows the form first and the "application received" confirmation only once an application has actually been sent.
- **Pages open in the configured theme.** Public pages paint in the instance's configured theme from the first moment, without briefly flashing the stock colors. The server-side theme also respects the `NUXT_BACKEND_URL` variable, so it works on installations that only set that one.
- **The location map shows its pin.** Picking a station address on the map during station setup or in the station settings shows the draggable pin again.
- **Every permission has a readable name.** The permission picker shows proper German names and descriptions for the checklist, item hand-out, storage location, form submission and poll result permissions instead of raw technical keys, and the checklist group shows its own icon.
- **Page titles follow navigation.** In the station and admin areas, the browser tab title and the page heading with its description update when moving between pages, not only after a full reload.

## v26.11.3

### New Features

- **Step-by-step mail provider guides.** The help center has a dedicated page for each supported mail provider - Brevo, RapidMail, Sweego and Twilio SendGrid - that walks through creating the SMTP credentials and shows which fields to fill in. The mail settings help articles link to them.

### Changes

- **Bundled database upgraded to PostgreSQL 18.** The compose files now mount the database volume at `/var/lib/postgresql` as the new version requires. Existing installations must migrate their data when upgrading, for example with a dump before and a restore after, because the old data directory format is not compatible.
- **Emails wait for mail setup instead of being lost.** On an instance without a configured mail provider, sign-up, invite and password emails stay queued and are delivered automatically once the mail settings are configured. Before, such emails were only written to the server log.
- **Mail settings adapt to the chosen provider.** The mail forms in the admin area, the station settings and the station setup show each provider's own fields with matching labels and guidance - Brevo asks for the account login email and an SMTP key, RapidMail and Sweego for their generated SMTP credentials, Twilio SendGrid only for an API key. Failed connection tests explain which credentials the provider expects.

### Fixes

- **Admin help articles are reachable from the help sidebar.** The admin help area mirrors the admin navigation with sections for settings including mail and security, two-factor, monitoring and developer tools, so articles like the mail settings help show up in the sidebar again.
- **Admin help pages stay in the admin help area.** The help pages for security settings, two-factor and storage monitoring open with the admin help navigation instead of switching to the station help center.
- **Seeded demo forms keep their question settings.** Choice, date and Likert questions on demo instances carry their answer options and scales again instead of falling back to empty settings.

## v26.11.2

### Security

- **Visitor addresses can no longer be forged behind a proxy.** When the server runs behind a reverse proxy or Cloudflare (`network.trustedProxies`, `network.cloudflare`), the visitor address used for rate limiting and security logs is taken from the nearest hop that is not a trusted proxy, so forwarded-address headers sent by the visitor themselves are ignored.

### Fixes

- **Problem monitoring shows timestamps and stacktraces.** Entries on the admin problems page show their first and last occurrence time and the full stacktrace of the recorded error again instead of an invalid date and empty details.
- **Setting a station manager works for stations without one.** Entering a manager email when editing a station in the admin panel invites the account if it does not exist yet and grants it station administrator access, also when the station had no manager before.
- **Everyone can edit their own name and email.** Changing your own first name, last name and email address on the account's profile page works for every signed-in user, without needing the member edit permission. Email changes still take effect only after confirming the link sent to the new address.
- **Remote-storage credential key generates itself.** A production install with a blank `storage.credentialEncryptionKey` writes a fresh key to the config on first start, so station-supplied storage credentials can be encrypted without manual setup.

## v26.11.1

### Security

- **Stricter cross-station isolation.** Every station-scoped resource - pages and their files, forms and responses, events and registrations, members and their profile data, notes, quiz catalogs and attempts, inventory, attendance, waiting lists, knowledge-base documents and boards - is now checked to belong to the signed-in user's own station before it can be read or changed, closing cases where a resource from another station could be reached by supplying its id.
- **Two-factor sign-in is rate limited.** Repeated two-factor and step-up attempts are throttled per account and per address, and a login's pending two-factor challenge is invalidated after several wrong codes, so a stolen password can no longer be paired with unlimited guesses.
- **Authenticator codes are single-use.** A time-based authenticator code can no longer be used more than once within its short validity window.
- **Password resets clear remembered devices.** Resetting a password, and removing a second factor, now revoke every "remember this device" entry so a saved device can no longer skip the two-factor prompt afterwards.
- **Setting up two-factor asks for your password.** Enrolling the first authenticator app or security key now requires confirming the account password, so a stolen browser session alone cannot add its own second factor.
- **Shorter password-reset links.** Self-service password-reset links now expire after one hour, configurable via `auth.resetTokenHours`; operator-issued invites and admin resets keep their longer window.

### Changes

- **Station logos are raster images served at size.** A station logo upload accepts PNG, JPEG, WebP or GIF, and each place that shows the logo receives an appropriately sized copy instead of the full-resolution file, so pages load lighter. SVG uploads are no longer accepted.

### Fixes

- **Event access restrictions keep their match mode.** Choosing whether an event's user-type, group and tag conditions must all match or any single one is enough now persists when the event is saved.
- **Blank profile fields when adding a member.** Custom profile fields without a default value start empty on the new-member form.

## v26.11.0

### New Features

- **Admin overview page.** Administrators land on a real "needs attention" panel at Admin → Dashboard → Übersicht with tiles for failed and stuck emails, pending station applications, stations still in setup, unverified accounts, open federation requests, unreachable discovery peers and open problem reports, plus short lists of the most recent applications and problem reports. Tiles turn green when the count is zero and jump straight to the relevant admin page on click.
- **Admin statistics with charts.** The statistics dashboard now shows daily new-session activity for the last 30 days, a top-ten-stations-by-member bar chart, and pies for email verification and station setup progress alongside the existing tiles.
- **Form list redesigned as tiles.** The station's form list now shows each form as a tile with its status, response count, title and description. Each tile also shows when the form was created and when it last saw activity (either an edit or a new response), and the list can be sorted by last activity, creation date, or title, in either direction. Clicking a tile opens the form - the editor for drafts, analytics for everything else. Publish, close, edit, analytics and delete actions live in a context menu in the tile's upper-right corner.
- **Outstanding members on required-form analytics.** When a form is marked as required, its analytics page lists the eligible members who have not submitted a response yet, so chasing the missing ones is a glance away.
- **Checklists for member follow-up.** Managers can build a list of yes/no questions, pick a member set by user type, group, tag or by hand, and tick each member off as they finish each step. The list overview shows one tile per checklist; on a phone the detail view switches from the wide matrix to a per-member card so every column is readable without horizontal scrolling. Member rows are listed alphabetically by name, and the search bar at the top of the matrix jumps straight to a member as you type. The add-members picker is also sorted alphabetically. Each cell takes an optional note with full history of every change, and the note text shows directly in the matrix next to its toggle. The list's name and description stay editable after creation, columns can be reordered by drag-and-drop or with up/down arrows in the edit dialog (the arrows also work on touch), and a new column lands at the position the manager picks. The list can be refreshed later to pull in newly-matching members, individual members can be added or removed by hand at any time, and the matrix exports to CSV for spreadsheet work or to a printable PDF that carries the station logo and name in a compact running header, uses drawn checkboxes instead of emoji, and follows the station language. Access splits into a read-only permission for staff who only need to look, and a manage permission for the rest.

### Changes

- **Consistent page headers across the app.** Every page now shows its title in the top header bar exactly once - duplicated in-page titles and pages missing a header title are both gone. Applies to the whole admin panel, station manage, station federate, requirements, checklists, quiz, procedures, boards, pages, protocols, lost-and-found and every other station and helpcenter view.

### Fixes

- **Landing page no longer kicks signed-out visitors to the login form.** Arriving at the home page with a stale or expired session in the browser silently clears the dead session and stays on the public landing page instead of redirecting to login.
- **Saving form questions with type-specific settings works again.** Rating, choice, ranking, and Likert questions that carry their own configuration now save without the request being rejected.

## v26.10.2

### New Features

- **Self-registration for member fields on an event.** Member fields on an event can be marked so any eligible station member can put themselves into the slot without the event-edit permission. Single-value fields stay with whoever claims the slot first and only that person can free it again; list fields let members add or remove themselves. Member fields can also be restricted by user type or tag in addition to groups.

### Fixes

- **Traffic recorder no longer log-floods after a station is removed.** Per-station traffic deltas whose station id no longer exists in the database are folded into the instance-global bucket on the next flush and the id is remembered for the rest of the process so subsequent hits skip the failing insert directly. The traffic worker previously logged the same foreign-key violation every flush interval forever.

### Changes

- **Batch event creation loads from an event template.** Picking an event template in the batch creator copies the template's title, description, category, registration toggles, and field set into the form so every event in the batch starts from the same preset. The separate "field layout" feature it replaces has been removed.
- **Member list flags accounts pending setup.** Members whose account has not yet been signed into for the first time show an hourglass icon next to their name in the member list, and managers see a paper-plane button to resend the password-setup email without having to step through the user themselves.
- **Mail relay failures retry indefinitely.** Emails that fail because the relay was unreachable, timed out, or returned a transient error are no longer marked failed on the first attempt - they stay queued and the email worker keeps retrying every ten seconds until the relay accepts them again. Permanent failures (rejected recipient, bad credentials) still mark the row failed so an operator notices.
- **System mails follow the station's language.** Accounts that were created from a station - through an invite, application acceptance, waiting-list registration, member import, or cross-instance import - receive verification, password-setup, password-reset, two-factor-reset, and email-change notifications in the language configured for that station. Accounts from self-signup still default to English.
- **Mail settings validated and clearable.** Saving the instance-wide or per-station mail configuration now runs a live connection test against the configured provider before persisting; the save is rejected with the actual server error if the test fails. A new clear action wipes either configuration back to the unset state.

## v26.10.1

### New Features

#### Station setup walkthrough

- **Setup wizard at /station/setup.** Administrators of a freshly-created station land on a guided walkthrough that covers the address and pin on the map, module selection, member-type permissions, optional member groups, the station's own outbound mail relay, branding, federation visibility (public by default), a first event, an initial knowledge-base page, and member invites. The standard sidebar shows the steps with check marks for what is already done.
- **Pinned setup checklist on the dashboard.** While any step is still open, the dashboard shows a checklist with direct links into the wizard. It disappears once an administrator clicks the finish page to mark setup complete.
- **Member invites by email.** Administrators can invite people by email from the wizard or from the regular members screen. Each recipient gets a single-use link, lands on /invite/<token>, sets a password, and joins the station without the administrator having to create an account first. Invites carry the recipient's name, member type, optional group, and optional guardians; pending invites can be revoked.
- **Roster CSV import in the invite step.** The invite step hands off to the existing member-import screen with full column-mapping (name, email, groups, guardians, profile fields) and preview, then lands back in the wizard once the import is done.

### Changes

- **Waitlist emails go through the instance mail relay.** Verification, registration confirmation, confirm-reminder, and removal-warning emails for the public waiting list now route over the instance-wide mailbox just like account verification and password-reset emails. Stations no longer need their own mail relay set up for these to arrive, and the per-station daily and monthly send caps no longer apply to them.
- **Cross-instance transfer ships less data.** Transferring a station no longer copies the smaller resized renders of every page image. Only the uploaded original travels and the destination rebuilds the resized set locally, so image-heavy stations move in a fraction of the previous bandwidth.
- **Page image storage roughly halved for new uploads.** Each page image now keeps the uploaded original plus a WebP rendition at each configured width. The redundant original-format resizes are no longer generated; existing stations keep their old files until those images are re-uploaded.
- **Transfer progress shows a stable file total.** The per-category file count on the transfer progress page now reflects the full number of files up front instead of climbing as new pages of work are discovered.
- **Two uploads at a time in the page files browser.** Dropping a batch of files into the page files browser uploads two in parallel instead of strictly one after the other, roughly halving the wall-clock time for typical batches.
- **Storage reconciliation also removes orphan files.** The daily reconciliation now deletes files on disk whose owning record is gone (page files, knowledge-base files, lost-and-found, quiz question, and knowledge-base folder icons), so deleted content no longer keeps consuming disk space until the station is rebuilt. Knowledge-base inline images and board attachments are intentionally left alone for now.
- **Deleting a station also removes accounts that have nothing else to belong to.** When a station is deleted, accounts that were only connected to that station and are not instance administrators are removed alongside it. The same cleanup runs when a cross-instance transfer fails part-way, so half-imported accounts are no longer left as ghost rows.

### Fixes

- **Cross-instance transfer carries members without email.** Applicants too young to have an email address (for example youth registered by a guardian through the waiting list) now arrive on the destination station along with the trial-membership and waiting-list entries that pointed at them. Previously every member without an email was silently dropped together with the trial entry that referenced them.

## v26.10.0

### New Features

#### Inventory Storage and Custom Fields

- **Storage containers.** Every room, shelf, drawer and box is a container that can hold items and other containers, nested as deeply as the operator needs. Containers are reached from a new Station → Inventar → Lager entry, can be searched and resolved by scan, and each item carries its container path as a clickable breadcrumb on the item detail page.
- **Custom fields per inventory.** Each inventory now defines its own extra fields with one of five types - date, dropdown, text, number with optional unit, or yes/no. Managers add and reorder them in the inventory editor; the inputs then show up on the item form and the values are persisted alongside the item.
- **Container check workflow.** A new Station → Inventar → Prüfung → Behälter-Prüfung flow walks the items expected in a chosen container, scan by scan. Items are confirmed, marked missing, or flagged lost; items the operator finds but the system did not expect for the container are collected separately. A toggle extends the walk to every nested container.
- **Dedicated assign-and-return page.** A new Station → Inventar → Zuweisen page lets a station member with the new "Inventar zuweisen" permission pick a recipient and then scan items in sequence - each scan assigns the item, scanning an already-assigned item with the same recipient selected returns it. The permission is off by default for every role; the station owner grants it explicitly to whoever runs equipment handover.
- **An item is either with a member or in storage, never both.** Assigning an item to a member clears any container it was placed in; placing an item in a container ends the open assignment. The database enforces the same invariant so no path can leave an item in a halfway state.

#### Pluggable Storage Backends

- **Choose where uploaded files live.** A new backend layer lets the instance keep stored files on local disk (the default) or push them to an SMB share, an SFTP server, or any S3-compatible object store (AWS S3, MinIO, Backblaze B2, Wasabi, Hetzner Object Storage, Cloudflare R2). Operators pick the instance-wide default from Admin → Monitoring → Storage → Backend; remote backends talk their protocol directly, with no kernel mount, FUSE, or container privileges required.
- **Per-station backend overrides.** A station manager can point their own station at a private S3 bucket, SMB share, or SFTP host from Station → Manage → Storage → Backend without instance-admin involvement; credentials are entered in the form, stored encrypted at rest, and a "test connection" button probes them before any change is applied.
- **One-shot migration when the backend changes.** Applying a new backend probes the target, copies every existing file over, and only then flips the active backend in one step - there is no half-migrated state where the configuration points at one backend and the bytes still live on another. A failed migration leaves the previous backend authoritative.
- **Stations on their own backend skip instance quotas.** Once a station is using its own remote backend, the instance-side total, per-page, per-image and per-file caps no longer apply and the quota bars on the storage dashboard hide behind a badge marking the station as using its own backend.
- **Backend audit log.** Every backend create, update, delete, probe, rejection, and migration event is recorded with the actor (account or system), station, and outcome. Visible per station under Station → Manage → Storage → Backend → Audit and instance-wide under Admin → Monitoring → Storage → Audit.
- **Cross-instance station transfer.** A station can be exported from one instance and imported on another in a single flow. The source flags the station read-only for the duration with a banner naming the destination, refuses writes with `503 Service Unavailable`, streams the database rows, stored files, and account avatars over a signed channel to the destination, and clears the flag when the operator aborts.
- **Help articles for the new flows.** Help center entries cover the instance-wide backend swap, the per-station backend picker, and how to read the audit log.
- **New operator config** - `storage.credentialEncryptionKey` (env `STORAGE_CREDENTIAL_ENCRYPTION_KEY`), the AES-256 key used to encrypt station-supplied remote-backend credentials before they hit the database. Required once any station opts into self-service remote storage; auto-generated on first boot in a fresh install.

### Changes

- **Inventar sidebar reorganised.** The Inventar group label itself now opens the inventory overview, Lager is a new top-level entry alongside it, Prüfung splits into Mitglieder-Prüfung and Behälter-Prüfung, and the existing Tausch, Beschaffung, Benötigt, Ausleihe and Inventare entries fold into a new Verwaltung subgroup so the group does not run long.
- **Help center sidebar always expanded.** The help center sidebar no longer inherits a collapse preference from the dashboard. It stays full-width on desktop so articles remain reachable by title.
- **Flyout menus on the collapsed sidebar.** With the desktop sidebar collapsed to its icon rail, hovering or keyboard-focusing a group icon now opens a floating menu showing the group's label and every nested entry, so all destinations stay reachable without first expanding the rail. Nested subgroups chain into further flyouts and badges stay visible on the rail when the menu is closed.
- **Pflichttest toggle in the test builder.** Test managers can now flag a quiz as Pflichttest the same way forms have always offered Pflichtformular. A required test surfaces on the post-login requirements page until the member has submitted an attempt, and the "Starten" button drops them straight into the test runner.

### Fixes

- **Saving a Pflichtformular sticks.** Toggling Pflichtformular in the form builder and saving now persists the flag instead of failing the request.
- **Required test button starts the test.** The button next to a required test on the requirements page opens the test runner directly; it previously dropped you on the test's read-only detail page.
- **Deep links survive multi-station login.** A notification or shared link to a station-scoped page now routes through the cross-station picker when the account belongs to several stations: the original destination is preserved and the picker continues to it after a station is chosen. Notification and feed links also carry the owning station so a single-click sign-in lands directly on the right station.
- **First-login onboarding leaves deep links alone.** Following a notification on first login no longer redirects to the dashboard halfway through; the onboarding tour waits for the next direct dashboard visit.

## v26.9.1

### Changes

- **Legal documents grouped under one directory.** The default paths for the privacy policy, terms of service, consent text and imprint now live under `data/documents/<type>/` instead of four separate top-level directories. Operators upgrading must move existing content into the new layout, or set `privacyPolicyDir`, `tosDir`, `consentDir` and `imprintDir` in `conf.yml` to the existing paths.

### New Features

- **Barcode and QR scanning for inventory.** A scan button next to every internal-id field opens the camera (rear camera on phones, webcam on laptops) and resolves printed Code 128, Code 39, QR, Data Matrix, EAN and UPC labels to the item's internal id. Available when creating or editing an item, on the inventory search bar, when assigning items to a lending request, and in the rapid inventory-check mode - where the modal stays open in continuous mode so you can sweep a pile of returning items. Decoded values are normalised (uppercased, trimmed) and the same normalisation is applied to hand-typed ids so they always match.

### Changes

- **Login without station membership.** Any account with a verified e-mail can now sign in. Administrators land on the admin panel, non-admin users without any station membership land on the Account area; the station overview still gates per-station features behind a station selection.
- **Admin panel button always reachable.** The shield button that opens the admin panel now shows on the cross-station overview, the Account area and the station dashboard whenever the signed-in user is an administrator and isn't already inside the admin panel.
- **Header unified across views.** The cross-station overview, account pages and station panel use the same avatar menu and station-panel button. The station-panel button is only shown when the user actually has a station to switch to.
- **Login returns you where you started.** A login that requires two-factor verification now carries the original destination through the verification step, so you land back on the page you originally tried to open.
- **Member type list ordered by role power.** The member-type dropdown now lists *Manager* first, then *Team*, *Guardian*, *Member*, *Trial* - top-down by responsibility instead of alphabetically.
- **Data export button labels what it does.** The Account → GDPR download button now reads "Download the full data export as a ZIP" and the per-managed-member variant names the person. The page text spells out that the archive covers every station the account belongs to.
- **Trusted devices section translated.** The trusted-devices block on the security page now shows a localised title, description, empty state and column labels.
- **Demo login shows station-less accounts above the tabs.** In demo and dev mode, accounts that don't belong to any station (the demo administrator) appear at the top of the login picker instead of in their own tab.

### Security

- **Guardians can only be attached to members and trial members.** Adult member types (Team, Manager, Guardian) no longer accept a guardian assignment, on the relations tab and on the server.
- **Self-lockout protection on permissions.** A user can't remove their own permissions through the member editor, and the station owner can't have the Station Administrator permission revoked.

### Fixes

- **Wrong permissions after login.** Switching accounts or logging back in after a stale session no longer resolves permissions against the previous account's station. Single-station accounts go straight to the station, multi-station accounts to the picker, and the picker is the only way to enter a station context with full permissions.
- **Station button in the cross-station header.** The button no longer opens the station panel with the wrong station selected; it routes through the picker when no usable station is stored, and selects the only station automatically when there is exactly one.
- **Dashboard crashes with a stale station.** Hitting the dashboard with a station that the current account isn't a member of no longer crashes feed-status, notifications or exchange list calls; the dashboard sends you back to the picker instead.
- **Station Administrator back in the permission picker.** The Station Administrator entry reappears in the per-member permission list when the member's user type already grants it, so it can still be toggled.

## v26.9.0

### New Features

#### Account Settings Hub

- **New Account area.** Profile picture, name, e-mail, password, two-factor, theme, active sessions and GDPR / account deletion are collected on a dedicated set of pages that apply to the user, not to a single station membership.
- **Avatar menu in the header.** An avatar + name button replaces the standalone logout icon. Clicking it opens a dropdown on desktop or a slide-in drawer on mobile, with entries for Account settings and Logout.
- **One profile picture per account.** A user's avatar now follows them across every station they belong to. Existing avatars are not migrated - re-upload once after the update.
- **Station profile slimmed down.** The station-side profile page keeps only the station-specific fields and the incomplete-fields nudge; name, e-mail, password, two-factor, theme and sessions are managed from the new Account area, with a link left in their old spot.

#### Instance Security Configuration

- **Security settings split.** Settings → Security is now a hub linking to three subpages: *Tokens & Sessions* (session and token lengths, token pepper), *HIBP* (breach-check), and *Two-Factor* (toggle, encryption key, TOTP / backup-codes / WebAuthn parameters, per-user-type policies). Every field has an inline description.
- **Two-Factor Management section.** Operational tools (per-account reset, audit log) live under a dedicated sidebar entry, separate from the configuration subpage.
- **Auto-generated secrets.** Missing `tokenPepper` and 2FA encryption key are generated on first boot and written to `config.yaml`, so a fresh production install boots without manual secret setup.

#### Two-Factor Authentication

- **Authenticator apps and security keys.** Users can enrol a TOTP authenticator app (Google Authenticator, Authy, …) by scanning a QR code, and register one or more FIDO2 / WebAuthn security keys (Yubikey, platform authenticators, …) with a name of their choosing. Setup, rename, and removal all live under the new Account → Security page.
- **Backup codes.** Ten one-shot recovery codes are issued the first time a user enrols a second factor and shown once. The user can regenerate the set at any time.
- **Login flow.** When an account has a second factor configured, password login asks for the code or security key on a dedicated verification page before the session is created. A "Remember this device" option skips the prompt on the same browser for up to 30 days (configurable).
- **Sensitive actions ask for a fresh confirmation.** Password change, removing or adding 2FA factors, regenerating backup codes, logging out all other sessions, role / permission changes, federation pairing or sharing edits, and instance-config changes now require a recent second-factor confirmation. The user is shown an in-page modal that resumes the original action on success.
- **Trusted devices panel.** Lists every device the user has marked as trusted with last-seen and expiry, and offers per-device revoke and revoke-all.
- **Audit trail.** Every enrolment, removal, login verification, step-up confirmation, backup-code use, trusted-device add or revoke, and admin reset is recorded per account.
- **Admin panels.** Station admins get a Security entry under Manage to require 2FA per user type, see who has set it up, and reset a member's 2FA. Instance admins get the same controls instance-wide under Settings → Security → Two-Factor plus a dedicated Two-Factor Management entry for account reset and the full audit log.
- **Demo mode.** Security-key setup is hidden in demo deployments, since demo accounts can't realistically be re-paired with a physical key.
- **On by default.** 2FA is enabled out of the box; the encryption secret is generated automatically on first boot if not configured. Existing sessions stay valid, but mandated user types (instance admins, station managers) are prompted to enrol on next login.

#### Public Form Submission

- **Forms can be posted by visitors without an account.** Every form now has a purpose (contact, poll, …) and can be published as a public page or embedded inside a station page.
- **Spam protection.** Repeated submissions from the same client are de-duplicated without storing the visitor's IP, and a rate limiter caps abuse from any single source.
- **Form analytics.** Per-question aggregates power a poll-analytics view and the contact-submissions inbox, with a matching help-center article.

#### Per-Station Page File Browser

- **Files, folders, and tags.** Every station has a dedicated file browser with folders and tag metadata. The page editor's "browse files" picker walks the same tree, so existing uploads can be reused across pages instead of re-uploaded.

#### Page Editor Cell Types

- **Many new cell types** - callout, quote, divider, spacer, accordion, PDF, file download, countdown, partner stations, stats counter, tabs, achievements, image gallery, KB article, news teaser, page link, map, address card, member spotlight, hero banner, external link card, blog signup, audio embed, poll embed, forms CTA, code block, member list, and a nested-rows layout primitive that lets cells be split or wrapped in place.
- **Cut, copy, paste between cells**, with a paste-here shortcut in the empty-cell chooser.

#### Public Quiz Teaser

- A public read-only listing of every quiz catalog marked as public.

#### Collapsible Desktop Sidebar

- A desktop-only toggle animates the sidebar from full width to an icon-only rail. The header row (logo + station name) stays; only the top-level icons remain in the nav. Mobile drawer behaviour is unchanged.

#### Trusted-Proxy and Cloudflare-Aware Client IP

- Operators can declare trusted proxies and a Cloudflare flag in the network config, so the real visitor IP is resolved correctly behind direct, Traefik, Cloudflare or Cloudflare → Traefik deployments.
- The bundled Cloudflare IP range list is refreshed automatically on every boot, with the bundled snapshot as a fallback when upstream is unreachable.

#### Shared Search Pickers

- Consistent search pickers for events, forms, members, news, pages, partner stations and wiki articles are reused across the page editor and several other views.

#### Consent Gating for Public Submissions

- **Acceptance recorded with every public submission.** Anonymous form, poll, and waiting-list submissions require a checkbox accepting the current privacy policy and terms of service; the proof is captured at the moment of submission.
- **GDPR-friendly IP recording.** The client IP captured with the proof is truncated before storage - IPv4 keeps only the first three octets, IPv6 keeps only the /48 prefix.

#### Landing Page Rebuild

- **Completely redesigned home page.** The demo / register / hosting calls-to-action render with live config values on the very first paint.
- **Self-hosted fonts.** Bitter and JetBrains Mono ship with the application, so the landing page no longer fetches fonts from Google at runtime.

#### Theme Improvements

- **No more post-hydration flash.** The instance theme - and the station theme on public station pages - is resolved server-side and applied before any client JavaScript runs.
- **Anonymous visitors get the instance default.** A cached per-user theme no longer leaks into a logged-out session or a fresh tab.
- **Public station themes stay scoped.** A station's theme no longer bleeds into the start page after navigating away.

#### Per-Station Traffic Monitoring

- **Traffic dashboards** at Admin → Monitoring → Traffic and Station → Manage → Traffic show hourly ingress and egress bytes plus request counts per station, split into authenticated, unauthenticated and federation traffic. Time window (24h / 3d / 7d / 30d), metric (egress / ingress / requests) and bucket filter are switchable; the admin view adds a per-station leaderboard.
- **HTTP responses are gzipped by default** for text-shaped content (JSON, HTML, CSS, XML / RSS / Atom, SVG, plain text, ICS feeds). Binary uploads stay untouched.
- **New operator config** - `metrics.trafficEnabled`, `metrics.trafficRetentionDays`, `metrics.trafficFlushIntervalSeconds`, and `api.httpGzipEnabled` / `api.httpGzipLevel` / `api.httpGzipMinSizeBytes` for the gzip tuning.

### Security

- **Bearer tokens hashed at rest.** Session cookies, password-reset codes, email-verification codes and station-delete codes are now stored hashed with a server-side pepper. A database-only leak no longer yields usable tokens - the attacker also needs the server secret.
- **Breaking on upgrade.** The migration removes the plaintext token columns. Every active session and pending recovery link is invalidated; users sign in again once, and pending password-reset / email-verification / station-delete emails have to be re-requested.
- **New required production secret: `auth.tokenPepper`.** Generated automatically on first boot if not already configured. Demo / dev runs fall back to a fixed placeholder.
- **Markdown is sanitised before display.** KB articles, station pages and legal documents pass through a strict HTML allow-list. Scripts, inline event handlers, `javascript:` URLs, cross-origin iframes and off-allow-list images are stripped; legal documents additionally forbid images and iframes.
- **Uploaded files served with a safe content type.** User uploads (KB files, KB presentations, board ticket attachments, public page files) are only served with their declared content type if it's PNG, JPEG, WebP, GIF or PDF; everything else falls back to `application/octet-stream`. Download filenames are sanitised so a crafted upload name cannot inject extra response headers.
- **Federation signatures bind method, path and recipient.** A captured signature can no longer be replayed against a different endpoint, peer or HTTP method. Senders include a per-request nonce; receivers reject duplicates inside the timestamp window.
- **Breaking on upgrade for federation.** The federation protocol revision bumps automatically; unfixed peers will fail signature verification until both sides are upgraded. Coordinate the upgrade with each partner.
- **Auth endpoints rate-limited.** Login, forgot-password, resend-verification, register, verify-email, set-password, change-password, confirm-email-change and refresh all have leaky-bucket limits per IP and (when the request carries an identity) per email or account. Exhausting a bucket returns `429 Too Many Requests` with a `Retry-After` header.
- **Email enumeration on auth endpoints removed.** Registering with an already-used address always reports success and notifies the existing owner out of band. Login responds with a single generic message for wrong account, wrong password and missing permissions; the password check runs in constant time so timing cannot distinguish the cases.
- **Stronger password policy.** New passwords must be at least 12 characters. The hash algorithm now SHA-256-pre-hashes the plaintext before BCrypt, so passphrases longer than 72 bytes no longer collide on their first 72 bytes. Existing hashes still verify and migrate to the new algorithm on the next successful login.
- **HIBP breach checking.** New passwords are checked against Have I Been Pwned before being accepted, and the password is re-checked in the background after every successful login - a match forces a rotation on the next login. Operators can tune or disable the lookup via the new `auth.hibp` config block. Both paths fail-open on an HIBP outage.
- **Password rotation invalidates other sessions and recovery tokens.** Self-service password change keeps the user's current browser signed in; admin reset and token-based set-password log out every session. Each rotation sends an out-of-band notice to the account email.
- **Two-step email change.** Both the old and the new address must click their respective confirmation link before the change commits. The release mail tells the previous owner that someone tried to move their account away and recommends a password reset.
- **Path-traversal hardening.** Image, public logo and admin legal-document routes reject any path segment that would resolve outside the configured directory. Member UUIDs in path segments must be valid UUIDs.
- **Cross-station avatar disclosure closed.** A member's avatar can no longer be fetched by member UUID alone. The caller must share a station membership, be an instance admin, or have an active federation partnership with the target's station.
- **SSRF protection on federation outbound URLs.** Federation and webhook URLs are rejected when the scheme is not HTTPS or the host resolves to a loopback, link-local, private, multicast or otherwise reserved IP. A new `federation.allowPrivateHosts` flag (default off; on in tests and local-dev) bypasses the check during development.
- **Magic-byte sniff for uploaded images.** Image uploads are validated against the PNG / JPEG / WebP / GIF signatures and refused before being written if they don't match.
- **TRACE logs redact credentials.** Bearer tokens, federation signatures and station identifiers are masked in TRACE-level request and response logs.
- **Stronger ETag.** Conditional responses now use a SHA-256-based ETag instead of a 32-bit string hash, eliminating the risk of a forged "not modified" response.
- **Static-file serving removed from the API backend.** The Java process only serves `/api/v1/...` and `/docs` now; the Nuxt server owns every browser-facing route. Misconfiguring the old static-files directory can no longer publish a sensitive folder to the public internet.
- **Generic 400 responses scrubbed.** Unexpected validation errors return a generic "Invalid input" body to the client while the full details still reach the admin problem feed for operators.

### Changes

- **Admin sidebar reorganised.** Every monitoring entry lives under "Monitoring" (problems, problem reports, storage, API status, feed metrics, traffic, discovery, maps) and the dev-only data-tracking inspector lives under "Dev Tools". Update any bookmarks to the old top-level paths.
- **Waiting-list status page rewritten.** The page now shows the e-mail used for reminders under the name, the date the entry joined the list, the next confirmation deadline, and a queue position derived from the waiting-list score (highest score first, oldest entry as tiebreaker) - labelled as a rough indicator rather than the literal admission order. Guardian rows render their full name with e-mail as a fallback.
- **Member editor.** The join-date control moves from the General tab to the Profile tab, next to first name, last name and e-mail.
- **Waiting-list entry detail page.** Metadata chips align consistently and wrap cleanly on narrow widths.
- **Per-run log file.** The server writes a fresh log file at `logs/ember-<timestamp>.log` on every startup, alongside the existing console output.

## v26.8.0

### New Features

#### Discovery Chain (Cross-instance Catalog)

A new two-layer protocol lets every Ember instance build an organic, asynchronously-refreshed catalog of *other Ember instances* and surface their `PUBLIC`-scoped stations on a single discovery page - including stations the local instance has never federated with. See `.concept/discovery.md` for the full design.

- **Ed25519-signed gossip** - every instance owns a long-lived Ed25519 keypair generated on first boot under `data/discovery/`. The fingerprint `sha256(publicKey)[:16]` is the stable instance id used in logs and the admin UI. Distinct from the per-partner RSA keys used by federation, so discovery and federation key rotations stay independent.
- **Async-first ping/callback** - pinging another instance returns `204` immediately; the actual peer list comes back via a delayed `POST` to the originator's callback URL. No long-lived HTTP connections on either side, and slow peers can't pile up against the requester. Replay-protected per-nonce, drift-checked ±5 min.
- **Public station catalog endpoint** - `GET /public/discovery/stations` returns every `PUBLIC`-scoped station with bucketed member count (`<10 / 10-50 / 50-200 / 200+`) so small stations don't leak exact size. `INSTANCE` and `NONE` scopes are filtered at the SQL level, never trusted to the application layer alone. Cacheable for 5 min.
- **Instance info probe** - unauthenticated `GET /public/discovery/info` returns `{baseUrl, instanceId, publicKey, softwareVersion, discoveryEnabled}`. Drives manual peer addition, admin "test connectivity" checks, and any future external aggregator.
- **Bootstrap via federation** - on boot the instance walks its active federation partners, probes their info endpoint, and seeds the peer registry as `BOOTSTRAP` source. No global seed list - operators stay in control of who they federate with first.
- **Manual admin add** - admins can register a known instance by base URL; the discovery public key is fetched from the peer's info endpoint and may optionally be pinned to an admin-supplied value so URL/key drift is caught at add time.
- **Reputation + back-off** - signature failures (−20), timeouts (−1), invalid announcements (−2), and admin downvotes (−50) accumulate per peer; reputations below −50 trigger a 24h ping back-off. Successful callbacks and station fetches each add +1; a daily decay pulls negative scores toward zero by 5/day so transient outages don't permanently degrade a peer.
- **Hard blocklist** - admin-managed list of base URLs or public keys that are refused on both sides of the protocol regardless of reputation. Outbound pings, inbound pings, callbacks, and station fetches all consult the list.
- **Per-instance admin settings** - `discovery_enabled` (kill switch for outbound pings and the public stations endpoint), `discovery_max_depth` (0..10, default 2 - fan-out hint attached to pings), `discovery_ping_interval_minutes` (default 60, minimum 60).
- **Schedulers** - ping cycle (60 min), station-listing refresh (6 h), nonce GC (5 min), reputation decay (24 h). All initial delays staggered so federation seeding fills the registry before the first ping cycle.
- **Admin UI** under `/admin/discovery` - identity card (showing our own instanceId, publicKey, baseUrl), settings panel, peer registry with per-row actions (upvote / downvote / block / unblock / ping now / delete), manual add with probe, blocklist editor, "Discover now" trigger that pings every usable peer and refreshes the station cache in one shot, and "Seed from federation" trigger that rescans the federation partner list.

### Changes

#### Calendar Multi-day Events

- **Google-calendar-style spanning bars** on `/station/events/upcoming` - multi-day events render as a single continuous bar across the week grid instead of one chip per day. Bars carry the event's category colour, round only on the start/end sides, and pack into lanes so multiple overlapping multi-day events stay readable.
- **Recurring multi-day events** - the same spanning logic now enumerates per occurrence of recurring events (weekly, monthly-first, quarterly, yearly), so a multi-day recurring meeting spans correctly on every occurrence and not just the first.
- **Fix `multiDayEndDate` in the upcoming list view** - recurring events no longer display absurd ranges like `Samstag, 2026-07-04 – Sonntag, 2026-06-14`; recurring entries skip the range entirely and one-time events whose end falls on the start day collapse to a single date.

## v26.7.1

### Changes

#### Personal Feed Overhaul (iCal, Atom, RSS)

Every member's personal calendar and notification feed got a top-to-bottom rewrite so feed readers like Thunderbird, Apple Calendar, NetNewsWire, Feedly, and Reeder surface the same context that the web UI does.

- **Guardian-aware visibility** - the iCal feed only hides events when *every* relevant registration is declined/denied, so a guardian whose child is going still sees the event. Events whose registration deadline has passed without any active registrations drop out to keep the calendar clean.
- **Rich iCal event entries** - every event now carries category, recurrence label, registration deadline/limit/status, custom field values, per-managed-member registration breakdown, station-timezone-aware timestamps, and a tap-to-open web link. Cancelled events get a localised `[Cancelled]` prefix so clients strike through or hide them.
- **New `LOCATION` event field** - feeds the standard iCal `LOCATION` property so phones and calendar apps turn it into a tap-to-navigate map link.
- **Rich notification feed entries** - RSS/Atom entries carry a semantic HTML body (status badges with Unicode markers, prominent action button), a plain-text fallback for readers that strip HTML, the notification's actor as the entry author, both localised and stable filterable categories, and embedded images with meaningful alt text.
- **Event context in notifications** - new-event, reminder, cancellation, and registration-status entries surface the event's start/end timestamps and every non-empty custom field value (location, meeting point, notes, …) so feed readers carry the same info as the event-detail page.
- **Rich entry titles** - feed titles now read `News: Q3 schedule published`, `Procurement requested: Hose 25m`, `Registration ✓ Accepted: Open Training`, etc. instead of a bare category. Long fragments are truncated on a word boundary.
- **Same-day event range merge** - events whose start and end fall on the same day collapse into one `When: 15 Sep 17:00 – 19:00` row.
- **Live context lookups** - feed entries pull fresh details at render time: lost-and-found find/claim dates, lending date ranges, inventory ownership (organisation-owned / member-owned / mixed), board ticket title/assignee/priority, procedure progress, and storage-warning category breakdowns.
- **Embedded lost-and-found images** - feed readers can fetch item images via a token-scoped endpoint without exposing the rest of the API.
- **Atom is the recommended format** - featured prominently on the feed settings page with an explainer. RSS collapses into an "emergency fallback" section. iCal gets its own card explaining the calendar-subscription use case.
- **Verbosity presets** - three radio buttons on feed settings (Rich / Compact / Minimal) rewrite the copied URL accordingly. Persists locally; Rich is the default.
- **Privacy hardening** - the feed token never leaks via `Referer` and leaked URLs can't be picked up by search engines. The Regenerate-token / Revoke-token buttons show a confirmation modal warning that the action breaks every subscribed reader immediately.
- **Accessibility** - semantic HTML, `dir="auto"`, persistent link underlines, 44px tap targets, Unicode status symbols so meaning survives monochrome rendering and colour-blindness.

#### Notifications

- **Aggregated batch event notifications** - bulk-created events produce one batched notification per recipient instead of one per row.
- **Complete EN/DE coverage** - every notification type now has a localised category label and message.
- **Correct singular/plural handling** for `newEventsBatch`, `eventReminder`, `registrationDeadlineExpired`, and the email digest subject.

#### Recurring Events

Reminders for recurring events used to deep-link to a generic event page and comments merged across every occurrence. Both are now occurrence-aware end-to-end.

- **Date-aware deep links** - weekly reminders land on the right occurrence.
- **Detail view bound to a single date** - derived from the URL or the next occurrence. The redundant "Next date" container is gone; the date is shown directly as the `Start` / `End` rows.
- **List ↔ calendar toggle on `/station/events/upcoming`** - new month-grid view; the user's choice persists in `localStorage`.
- **Mobile-tight calendar layout** - reclaims roughly 60 px of horizontal space on a 360 px viewport (~21 % wider cells).
- **Per-occurrence comment threads** - comments on a specific occurrence of a recurring event stay scoped to that occurrence.

#### Feed Telemetry (Admin)

A new admin panel under "Monitoring → Feed-Telemetrie" charts feed usage and performance.

- Four summary cards (total requests, fully rendered, 304 cache hits, average render duration), three ECharts diagrams (requests-by-type, latency histogram, daily volume), a status-code breakdown table, and a global reader leaderboard.
- **No per-token attribution by design** - a station admin with DB access cannot derive which member uses which reader.
- Configurable retention windows; default 3 days for request stats, 90 days for feed metrics.
- Help center article explaining every chart, the histogram colour code, the relevant HTTP status codes, and the privacy posture of the reader leaderboard.

#### News View Tracking

- News entries are silently recorded as "seen" when fully visible for 800 ms (distinct from the explicit "I've read this" acknowledgement).
- News editors see a new eye icon on each entry; clicking opens a modal listing who has seen the entry and who hasn't.

#### Backend-driven Search

- The upcoming-events search bar now hits the backend (debounced 250 ms, case-insensitive) instead of filtering the already-loaded page.
- A new prominent `SearchInput` component (primary-color border, magnifying-glass prefix, clear button) replaces 11 page-level search bars (events, help center, board tickets, procedures, protocols, KB, lending offers, quiz catalogs, …).

#### Other Improvements

- **Calendar view multi-day events** - one-time events with multi-day duration now render on every day from start to end.
- **Guardian sees own inventory page** when at least one of their managed members owns an item.
- **Exchange type column** gated by `INVENTORY_EXCHANGE`.
- **Notification settings shortcut** from the dashboard notifications panel.
- **Reactive item state** - submitting an exchange request flips the inventory card into its "exchange pending" state immediately.
- **`/station/quiz/tests` accessible to anyone** - the page handles permission gating internally.
- **Event notes** no longer require member-notes permission for event managers.
- **Quiz reviewers** can list catalog names with `TEST_RESULT_READ` alone.
- **`Exchanged` status renamed to `Done`** (German `Erledigt`) for clarity.
- **`RestrictionPicker` AND/OR toggle** is clearer (two side-by-side buttons with both words always visible).
- **Rich text editor active icon** is finally readable (primary color glow instead of black-on-primary).
- **Comment line breaks preserved** when submitting (Chrome/Edge wrap each line in a `<div>`).
- **Help link for `/station/events/new`** now resolves to a dedicated article instead of a broken redirect.
- **Settings intro tour step** navigates to the page it actually describes.
- **Profile absences row** alignment fixed (date stays centered with the name).

#### Bug Fixes

- Lost-and-found, board ticket, and news notifications now deep-link to the correct page (previously fell back to the dashboard).
- Self-edit on comments now works correctly across news, knowledge base, and events.
- KB tag filter actually filters (was a no-op for search results and missing entirely for browse mode).
- Quick-check skipping no longer leaves the process hung with nothing rendered.
- Attendance config pages reappear (a `.gitignore` overmatch had been silently dropping them from VCS, so demo deploys 404'd).
- `PAGE_EDIT` / `PAGE_MANAGER` permissions are now actually grantable.

### Technical

- **Feed plumbing** - Conditional GET (ETag + If-Modified-Since) on every endpoint; per-token leaky-bucket rate limiting (10 burst, 5/min refill); body size caps (RSS/Atom: last 100 notifications, iCal: `[now − 7 days, now + 1 year]`); per-entry failure isolation; `Referrer-Policy` and `X-Robots-Tag` on every response.
- **Atom `<summary>` / `<content>` swap** - ROME maps `setDescription` → `<summary>` and `setContents` → `<content>`; previously reversed. Duplicate-`term` categories disambiguated via `scheme="urn:ember:notification-type"`.
- **New shared utility `dev.chojo.ember.util.LeakyBucket`** and `HelpCenterHint` Vue component.
- **Notification pipeline** - new `EventsBatchCreated` domain event aggregates bulk notifications; `NotificationService.notify*` now enforces a `NotificationLink` (fails fast otherwise).
- **Recurring events** - new `event-detail-date` route (`/station/events/{id}/{date}`); nullable `event_comment.event_date`; `RemoteCommentRequest` federation payload carries `eventDate` (backwards compatible with peers that omit it).
- **Typed date fields** - `LocalDate` instead of `String` on comment / reminder payloads (Jackson ISO `yyyy-MM-dd`).
- **Permission model cleanup** - `api.roles` → `api.auth` package rename (~160 import sites). `RoleValidation` → `PermissionValidation`, `RolesTest` → `PermissionsTest`. Frontend: `RoleSelector.vue` deleted, `RoleStep.vue` → `UserTypeStep.vue`, `RolesHelp.vue` → `PermissionsHelp.vue`; matching i18n key sweep.
- **`MemberIdentity.sameMember(other)` helper** - UID-only equality for ownership checks; adopted in news / event / KB comment routes and their federation variants. Fixes self-edit when DB-loaded vs. session-enriched identities are compared.
- **`StationIdModule` deserializer added** - previously serializer-only, so round-tripped UUID strings on `int` fields blew up with `InvalidFormatException`. `partnerStationId` added to the field-name set.
- **Bulk-friendly registration lookup** - `EventRepository.findRegistrationsByMembers(Collection<Integer>)` collapses N queries into 1 for guardian iCal feeds.
- **Schema migrations** - patch_11 (`station_event.updated_at`), patch_12 (`feed_metric_daily`, `feed_user_agent_stat`, `event_comment.event_date`, `EXCHANGED` → `DONE` rewrite, `news_view` table, `PAGE_EDIT` / `PAGE_MANAGER` backfill). `data_tracking.json` refreshed and verified.
- **`npm run build` now runs the four convention linters** (`lint-icons`, `lint-conventions`, `lint-helpcenter`, `lint-locales`) before `nuxi build`, matching `build:spa`. Two argument-order bugs in `lint-conventions.mjs` fixed.
- **`helpcenter-admin` layout** split from the generic `helpcenter` layout so the two sidebars are decoupled.
- **Component extractions** to satisfy the 500-line view-size lint: `useKbTagFilter`, `KbDeleteModals`, `KbFiltersBar`, `KbFileContent`.
- **rome-modules dependency** added for MediaRSS support.
- **Demo seeders** refactored to call real services (`NewsService`, `EventService`, `ExchangeService`, …) so notifications fire organically with correct link metadata. New `DemoLostAndFoundSeeder`; one showcase notification of every type seeded for the demo admin.
- **Test infrastructure** - new `UserFeedRoutesIntegrationTest`, `LeakyBucketTest`, `FeedFingerprintTest`, `FeedRateLimiterTest`, `IcalEventRendererTest`, `NotificationFeedRendererTest`, `FeedMetricsRepositoryTest`. `NotificationServiceTest` expanded with pluralisation coverage and a `notifyRejectsDataWithoutLink` regression. `jacocoCoverageCheck` and `testTracking` green.

## v26.7.0

### New Features

#### Storage Monitoring & Quota System
- **Per-station storage tracking** - tracks file storage usage across 5 categories: KB files, board attachments, page images, avatars, and other images
- **Quota enforcement** - configurable per-category and total storage limits with rejection on exceed (HTTP 413)
- **Quota presets** - reusable named profiles (e.g. Small, Standard, Premium) that can be applied to stations in bulk
- **Per-station overrides** - stations can have custom quotas or use instance defaults from config
- **Warning notifications** - domain event notifies station managers when usage crosses the configurable threshold (default 80%)
- **Automatic reconciliation** - background job recalculates actual usage from DB and filesystem on startup and at configurable intervals
- **Presentation compression** - lossless ZIP recompression of PPTX/ODP files, saving 10-30% for files above the threshold
- **Admin dashboard** - storage overview with summary stats, stacked bar charts per station, category pie chart, sortable station table with status badges and preset assignment
- **Station storage view** - read-only usage view for station managers with bar chart and per-category breakdown
- **Preset management** - CRUD UI with size inputs (number + MiB/GiB/TiB dropdown), apply to multiple stations, delete with confirmation
- **Config** - `storage` section in config.yaml with defaults for all quotas, compression, warning threshold, and reconciliation interval
- **Help center** - help articles for both admin and station storage views

#### Federation Version Broadcasting
- **Startup broadcast** - on boot, pings all remote federation partners to exchange version information
- **Version ping endpoint** - new `/remote/federation/ping` returns the current federation version hash
- **Version backfill** - partners created before version tracking get updated on startup
- **Version at creation** - new partners are created with the current federation version instead of placeholder '0'
- **DTO tracking** - federation version hash now includes inner record DTOs from FederationRemoteRoutes, FederationRoutes, LendingRoutes, and BoardRoutes

#### Public Pages (Layout Editor)
- **Page builder** - stations can create public pages using a lightweight layout editor inspired by WordPress/Elementor
- **Row-based layout** - pages are built from horizontal rows, each containing 1-4 columns with free-form percentage widths
- **Content types** - cells support rich markdown (WYSIWYG TipTap editor), images (upload with fit/sizing), and videos (YouTube embeds or direct URLs)
- **Responsive design** - horizontal rows automatically stack vertically on mobile
- **Page hierarchy** - pages support up to 3 levels of nesting with nested URL paths (e.g., `/page/about/team`)
- **Landing page** - one page can be designated as the station landing page, shown first in the sidebar
- **Station slug** - stations get a human-readable URL slug (auto-generated from name, editable) as alternative to UUID
- **SEO metadata** - per-page meta description and OG image, with auto-generation from content
- **Markdown rendering** - server-side commonmark rendering for public pages
- **Station theming** - public pages display the station's configured theme (colors, feel)
- **Image management** - per-page image upload (max 5 MB), orphaned images auto-cleaned on save
- **Copy/cut/paste** - clipboard for rows and cells with paste buttons between rows
- **Column controls** - visual column split buttons, swap button between columns, free-form resize handles
- **Move up/down** - row reordering via buttons
- **Preview mode** - toggle between edit and preview in the editor
- **Page duplication** - duplicate pages with full row/cell tree
- **Publish/unpublish** - PAGE_MANAGER permission for publishing, unpublished parents hide children
- **Help center** - article explaining page management
- **Demo data** - 4 sample pages (Willkommen, Über uns, Unser Team, Ausrüstung, Mitmachen) with hierarchy

#### Station Public URL
- **Public slug** - stations have a customizable URL slug (e.g., `/public/station/jugendfeuerwehr-musterstadt`)
- **Auto-generated** - slugs created from station name on creation, with dedup
- **UUID redirect** - UUID-based URLs automatically redirect to the slug version
- **Discovery links** - station discovery uses slugs for cleaner URLs
- **Settings UI** - editable slug in federation settings with duplicate detection

#### Public Waitlist Registration
- **Public waitlists** - per-waitlist `isPublic` toggle allows external registration without login
- **Per-field visibility** - each waitlist field can be marked as public or hidden from the registration form
- **Email verification** - registrants receive a verification email; token expires after 24 hours
- **Pending approval** - verified registrations get `PENDING` status, requiring WAITLIST_EDIT approval
- **Approve/reject** - expandable pending entries in the waitlist detail view with approve/reject actions showing full registration details
- **Notifications** - WAITLIST_EDIT users are notified when a new public registration arrives
- **Station toggle** - `publicWaitlistEnabled` station setting controls whether public waitlists are available
- **Public registration page** - list selection, form with public fields, guardian inputs, and email verification flow
- **Verification page** - standalone page at `/public/waitlist/verify/{token}` confirming email
- **Public sidebar** - waitlist link in the public station sidebar when enabled
- **Guardian name split** - guardians now have separate firstname + lastname fields for direct account conversion

#### Public Blog
- **Blog entries** - news articles can be flagged as blog posts via a toggle in the editor
- **Blog badge** - blog entries show a "Blog" badge in the internal news list
- **Public blog page** - blog list with title, excerpt, author, and date; detail view with full HTML content
- **Landing fallback** - blog becomes the default landing page when no custom page is set
- **Station toggle** - `publicBlogEnabled` setting controls whether the blog is available
- **Public sidebar** - blog link appears after landing page, before calendar

#### Station Settings UX
- **Reactive save** - federation settings now auto-save on change (debounced 600ms) instead of requiring a save button
- **Save indicator** - shows "Speichern…" spinner and "Gespeichert" checkmark

#### Knowledge Base: Presentation Support
- **Presentation uploads** - upload PowerPoint (.pptx, .ppt) and OpenDocument (.odp) presentations to the knowledge base
- **Automatic PDF conversion** - presentations are converted to PDF server-side via LibreOffice headless for in-browser viewing
- **Async conversion** - upload returns immediately, conversion runs in the background with status tracking (pending/success/failed)
- **Presentation mode** - full-screen slide-by-slide viewer for PDFs and presentations using pdf.js, with keyboard/click/swipe navigation and slide counter
- **Auto-hiding controls** - presentation mode header and navigation buttons fade out after inactivity for a clean viewing experience
- **Original file download** - download the original presentation file from the file detail view
- **Re-upload** - replace the original presentation and trigger reconversion

#### Procedures (Abläufe)
- **New module: Procedures** - per-user checklists for structured processes (onboarding, equipment handout, etc.)
- **Templates** - reusable procedure blueprints with items and dependency chains, managed by PROCEDURE_MANAGER
- **Procedure instances** - created ad-hoc or from templates, with editable items before submission
- **Assignees** - assign procedures to one or more members with member picker
- **Item dependencies** - items can depend on other items (DAG), blocked items shown with lock icon
- **Public/private visibility** - procedures and individual items can be marked private (only visible to PROCEDURE_EDIT users)
- **User-assigned items** - items can be flagged as checkable by assignees; other items require PROCEDURE_EDIT permission
- **Resolve/reopen** - procedures can be resolved at any time and reopened if needed
- **Notifications** - domain events for assignment, resolution, reopening, and item completion
- **Sidebar integration** - badge shows open procedures; visible to all users with assigned procedures
- **Demo data** - 2 templates (onboarding, equipment handout) and 4 sample procedures with mixed states
- **Help center** - overview article for the procedures module

#### Server-Side Rendering
- **Nuxt 3 SSR migration** - frontend migrated from Vue SPA to Nuxt 3 with hybrid rendering: SSR for public pages, ISR for help center, SPA for authenticated station/admin views
- **Two-container deployment** - separate backend (Java) and frontend (Nuxt) Docker images for independent scaling and deployment

#### SEO
- **Dynamic sitemap** - `@nuxtjs/sitemap` generates `/sitemap.xml` with static pages and dynamic station URLs fetched from the discovery API
- **robots.txt** - crawl rules allowing public pages (`/discovery`, `/public/`, `/helpcenter/`) and blocking private routes (`/station/`, `/admin/`, `/api/`)
- **Canonical URLs** - `useCanonical` composable adds `<link rel="canonical">` and `og:url` to all public pages
- **Open Graph & Twitter cards** - all public pages include OG tags (title, description, type, image, locale, site_name) and Twitter card meta
- **Structured data (JSON-LD)** - `SoftwareApplication` on homepage, `Organization` on station pages, `Event` on public calendar (enables rich results), `BreadcrumbList` on KB navigation
- **SearchAction schema** - sitelinks search box on discovery page
- **Google optimizations** - `max-image-preview:large`, `max-snippet:-1`, `max-video-preview:-1` for richer search result previews
- **Google Search Console** - optional `NUXT_PUBLIC_GOOGLE_SITE_VERIFICATION` env var for site verification
- **Help center SEO** - `HelpArticle` component auto-generates meta description and OG tags from article title/subtitle for all 142 help pages

#### Data Tracking System
- **`data_tracking.json`** - single source of truth for every DB table tracked in station transfer, GDPR export, and GDPR deletion. Stores per-column verification flags, FK metadata, lookups, output shape, custom scope paths, and PG `COMMENT ON TABLE`/`COMMENT ON COLUMN` text (descriptions excluded from the hash so editing comments doesn't invalidate verification)
- **Metadata-driven station export/import** - `GenericTableExporter` and `GenericTableImporter` generate SELECT/INSERT queries dynamically from the tracking metadata. `StationExportService` and `StationImportService` are now thin orchestrators with no per-table SQL
- **Topological table order** - `TableOrder` derives the export/import order from FK dependencies (skipping `SET NULL` FKs to break cycles); no hand-coded `TABLE_ORDER` list
- **Custom scope support** - tables reached via an incoming FK (e.g. `account` through `station_member.account_id`) declare a `customScope` in tracking and the engine emits an `IN (SELECT … FROM viaTable WHERE …)` filter
- **FK-flattened lookups** - `lookups` array on `TableEntry` adds joined fields like `account_email` to exported rows; the importer resolves them back to local FK ids
- **Output shape per table** - `SINGLE` for one-row-per-station tables (`station`), `FLAT` for enum-only tables (`station_disabled_module`); the wire format is keyed by DB table name
- **Account migration** - accounts/credentials transfer via `customScope` through `station_member`; existing target accounts (matched by email) are linked as-is, new accounts are created with `force_password_change=TRUE`
- **Federation state transfer** - every federation table (`federation_partner`, capability, share configs across boards/inventory/KB/protocol/quiz, event/news federation) now transfers with the station; the private key column transfers too so partners keep recognising the station post-migration
- **Metadata-driven GDPR export** - `GenericGdprExporter` builds queries from `gdprExport.identityColumns` matching the requested identity type (`ACCOUNT_ID`/`MEMBER_ID`/`MEMBER_UID`). `GdprExportService` shrank from ~470 hand-coded lines to a thin orchestrator; output keyed by DB table name (`accountTables`, `memberTables`, `memberUidTables`)
- **Metadata-driven GDPR deletion** - `GenericGdprDeleter` honours each `gdprDeletion` strategy (`DELETE_EXPLICIT`, `NULL`, `ANONYMIZE` with type-derived sentinels - zero-UUID, `"Gelöscht"`, NULL for nullable int - and `CASCADE`/`RETAIN`/`RETAIN_UNLINKED`/`NOT_APPLICABLE` no-ops with audit logs). UPDATEs run before DELETEs across all tables; DELETEs in reverse-topological order
- **Dev-mode admin panel** - `/admin/data-tracking` view available only when `Demo.dev()` is true (frontend tree-shakes via `import.meta.env.DEV`). Color-coded status badges, summary dashboard, search by table name / column name / description, batch status changes, per-column verified toggles, multi-select dropdowns for `ignoredColumns`, fully editable GDPR deletion strategies, foreign-key chips with key icons, dangling-reference audit banner that flags MEMBER_ID identity columns without an FK to `station_member`, CASCADE chip warnings when the FK parent's effective strategy isn't actually a deletion
- **Federated uploader for board attachments** - `board_ticket_attachment.uploaded_by INT REFERENCES station_member` replaced with `uploader_station_uid UUID` + `uploader_member_uid UUID`; matches the federated identity pattern already used on `board_ticket.creator_*`, `board_ticket_comment.author_*`, `board_ticket_transition.actor_*`, `board_ticket_watcher.watcher_*`. Federated members from partner stations can now attach files

#### Documentation
- **Environment variable reference** - hosting help page now documents all env vars organized by category: Database, API, Mailing, Auth, Theming, Tools, Frontend, Demo, and Docker/Compose - each with default value and beginner-friendly description

### Improvements
- **Type-safe API responses** - replaced ~50 `Map.of()` API responses across routes, services, and export classes with typed Java records for compile-time safety
- **CI retry** - test jobs (repository, service, other) retry once on failure; Docker push steps retry up to 3 times for transient registry errors
- **CI coverage job** - no longer re-runs all tests; skips the default `test` task since coverage data is downloaded from artifacts
- **FileInput component** - new reusable styled file picker component replacing raw `<input type="file">` elements across the knowledge base
- **Frontend Docker image** - replaced `nixos/nix:latest` with `node:24-alpine` for dramatically faster builds (no nix-shell overhead)
- **Inventory item status** - item detail now shows "Zugewiesen" (assigned) or "Verfügbar" (available) instead of generic "Aktiv"
- **Inventory member avatars** - member names in inventory edit view now display with avatars via MemberName component
- **Members sidebar badge** - now includes both pending changes and waiting list entry counts
- **Inventory sidebar badge** - shows pending exchange request count on the inventory section

### Bug Fixes
- **Help center waiting list link** - home page feature tile linked to non-existent route `/helpcenter/station/members/waitinglist` instead of `/helpcenter/station/members/waiting-lists`
- **Inventory item assigned user** - assigned user was not shown on the item detail page; lookup relied on history entries instead of the direct assignment
- **Permission picker rollback** - unchecking a parent permission (e.g. LOST_AND_FOUND_MANAGE) discarded previously selected child permissions (e.g. LOST_AND_FOUND_CREATE) instead of restoring them
- **My Inventory tab visibility** - sidebar tab was always visible even when the user had no assigned inventory items
- **Orphaned quiz attempt rows** - `quiz_test_attempt.member_id` and `graded_by` were bare INT columns without FKs, so deleting a member left dangling references. Both now FK to `station_member.id` with `CASCADE` and `SET NULL` respectively

### Technical Changes

#### Data Tracking Backend
- **`DataTracking` records** - `TableEntry`, `ColumnEntry`, `ForeignKey`, `Lookup`, `CustomScope`, `TransferContext`, `GdprExportContext`, `GdprDeletionContext`, `DeletionStrategy`, `IdentityColumn` with `Status`/`Strategy`/`IdentityType`/`OutputShape`/`Scope` enums
- **`SchemaReader`** - reads PG `information_schema` plus `obj_description` / `col_description` for table+column comments; emits `RawTable` / `RawColumn` / `RawForeignKey`
- **`HashComputer`** - deterministic SHA-256 over columns + FKs; descriptions intentionally excluded
- **`DataTrackingRefresher`** - merges live schema into `data_tracking.json`, refreshing descriptions on every run and preserving verification flags
- **`StationScopeResolver`** - BFS over the FK graph to find the join chain from any table to a `station_id` column; handles the `station` table itself via `id`, skips `SET NULL` FKs
- **`TableOrder.topological`** - Kahn's algorithm over `dependsOn`, breaks cycles via `SET NULL` skipping, leftover nodes appended alphabetically for stable output
- **`GenericTableExporter`** + **`GenericTableImporter`** + **`GenericGdprExporter`** + **`GenericGdprDeleter`** - engine classes driving the four major flows
- **`DataTrackingAdminService`** - dev-mode only service backing the admin panel, file-path-configurable for tests
- **`DataTrackingRoutes`** - handlers registered only when `Demo.dev()` is true
- **Engine wiring** - `StationExportService`/`StationImportService` dropped ~2400 lines of hand-coded SQL; `GdprExportService` dropped ~470 lines; `GdprDeletionService` dropped ~100 lines. Public API preserved on each
- **DB migration** - `board_ticket_attachment` `uploaded_by` → `uploader_station_uid` + `uploader_member_uid` UUID pair with data backfill; missing FKs on `quiz_test_attempt.member_id` (CASCADE) and `graded_by` (SET NULL) added with defensive orphan cleanup
- **Metadata drift fixes** - `entity_note`, `entity_note_version`, `inventory_item`, `profile_field_change_acknowledgement` identity-column names corrected to match real schema; stale entries removed on `form_answer`, `waiting_list_entry_guardian`, `waiting_list_entry_value`, `waiting_list_invite`, `kb_file`
- **CLI cleanup** - removed `DataTrackingReviewer`/`Prompter`/`ReviewCli`/`BackfillCli`/`TransferMetadataBackfillCli` and their gradle tasks; the dev admin panel covers their use cases. Kept `refreshDataTracking` since the frontend can't read live PG schema

#### Storage Monitoring Backend
- **`StorageCategory` enum** - `KB_FILES`, `BOARD_ATTACHMENTS`, `PAGE_IMAGES`, `AVATARS`, `IMAGES`
- **`StorageUsageRepository`** - delta updates, absolute sets, per-station/category queries
- **`StorageQuotaPresetRepository`** - preset CRUD, apply-to-station, reset quotas, station preset name lookup
- **`StorageQuotaService`** - quota checking, per-file/image size limits, delta tracking, warning threshold detection
- **`StorageReconciliationService`** - filesystem walk + DB recalculation, runs on startup (1min delay) and at configured interval
- **`PresentationCompressor`** - lossless ZIP recompression with `Deflater.BEST_COMPRESSION`
- **`StorageRoutes`** - station usage, admin overview, preset CRUD, apply/reset, reconciliation triggers
- **`StorageWarningEvent`** + handler - domain event notifying STATION_MANAGER role
- **`SizeParser` utility** - parses "5G", "50M" etc. into bytes and formats back
- **`Storage` config** - Ocular config element with env var overrides (`STORAGE_*`)
- **DB migration** - `station_storage_usage`, `storage_quota_preset` tables; station quota columns + `storage_preset_id` FK

#### Federation Version
- **`FederationVersionBroadcaster`** - eager singleton, pings all remote partners 2min after startup
- **`/remote/federation/ping`** - returns `VersionPingResponse` (typed record, not Map)
- **`FederationVersionComputer`** - now tracks DTOs from `FederationRemoteRoutes`, `FederationRoutes`, `LendingRoutes`, `BoardRoutes`
- **`FederationRepository.backfillPartnerVersions`** - updates all partners with version '0' on startup
- **`FederationRepository.createPartner`** - sets `federation_version` to current version at creation time

#### Sitemap
- **Jackson XML serialization** - replaced manual XML string concatenation with typed records and Jackson `XmlMapper`
- **`lastmod` dates** - KB files and pages include W3C Datetime `lastmod` from `updatedAt`; index URLs derive `lastmod` from their most recent child
- **Caffeine caching** - sitemap responses cached in-memory for 6 hours

#### Station Applications
- **Enum status** - `StationApplication.status` changed from raw string to `ApplicationStatus` enum
- **DB migration** - existing lowercase status values normalized to uppercase

#### Public Waitlist Backend
- **PENDING status** - new `WaitingListEntryStatus.PENDING` for entries awaiting approval
- **Verification tokens** - `waitlist_verification_token` table with 24h expiry
- **Domain event** - `WaitlistPublicRegistration` event + handler for WAITLIST_EDIT notifications
- **Email template** - verification email in DE/EN

#### Guardian Schema
- **Name split** - `waiting_list_entry_guardian.name` replaced with `firstname` + `lastname` for direct account creation
- **`GuardianInput` type** - extracted from inline object types in frontend for type safety

#### Badge Convention
- **Lint rule** - error-level rule flags `<span>` with `rounded-full` + padding; must use Badge components
- **Refactored** - 54 violations converted to PrimaryBadge, SecondaryBadge, SuccessBadge, etc.
- **Inline type rule** - warning-level rule flags `ref<{ ... }>` patterns that should use named types

#### Bug Fixes
- **AccountRepository.setEmailVerified** - missing `= TRUE` in SET clause
- **EventCommentRepository.delete** - missing `= TRUE` in soft-delete SET clause
- **WaitingListFieldConfig deserialization** - `FieldRequest.config` changed to `String` to match frontend JSON contract

#### Federation Routes
- **Route restructure** - federation management moved from `/station/manage/federation` to `/station/federate` to fix sidebar prefix overlap

#### Help Center
- **Roles page** - rewritten to use correct "Benutzertypen & Berechtigungen" terminology
- **Federation page** - added missing i18n keys (shared5-7, dummy content keys)
- **FormLabel component** - extracted repeated label pattern into reusable component
- **Page editor help** - dedicated help center page for page editor route

#### Demo Service Refactoring
- **DemoService split** - reduced from 2180 to 679 lines by extracting 4 new seeders:
  - `DemoMemberSeeder` (643 lines) - groups, profile fields, users, tags
  - `DemoEventSeeder` (684 lines) - categories, events, attendance, templates
  - `DemoNewsSeeder` (214 lines) - news articles with comments
  - `DemoPageSeeder` (174 lines) - public pages with hierarchy
- **Parallel seeding** - member seeding runs first, all other seeders run in parallel

#### Frontend Architecture
- **`useCanonical` composable** - reusable canonical URL + `og:url` injection from `NUXT_PUBLIC_SITE_URL`
- **`__sitemap` server route** - Nitro server route fetching discoverable stations for dynamic sitemap entries
- **`build.mjs` wrapper** - polls for build output completion, then SIGKILL's the detached nuxi process group to work around esbuild hang

#### CI/CD
- **`ignore-checks`** - Docker Build workflow excludes `Verify Docker Build` from `wait-on-check-action` to prevent deadlock

## v26.6.1

### New Features

#### Mention System
- **Bulk mentions** - mention entire groups, all event participants, registered members, or declined members in comments
- **Mention UI with avatars** - mention dropdown shows user avatars, name colors, and display tags
- **Guardian notifications** - event-related bulk mentions also notify guardians of mentioned members
- **Restricted mention lists** - when content is restricted to certain groups, only eligible members appear in the mention picker

#### Notifications
- **Mention notifications** - dedicated notification for mentions, separate from comment reply notifications
- **News mention notifications** - mentioning users in news comments now triggers notifications
- **News author in notifications** - new news notifications now show the author name

#### Event Detail
- **Tab layout** - event detail page split into Info and Registrations tabs
- **Non-manager registration display** - pending registrations show as simple cards for users without confirmation permissions

### Bug Fixes
- **Event registration date** - registration and decline actions now use the correct event date instead of defaulting to today
- **Recurring event next occurrence** - correctly shows today as next occurrence when the event hasn't ended yet
- **Notification links** - comment and mention notifications for events now link to the specific event detail page instead of the events list
- **Requirements redirect** - requirements page redirects to the dashboard when there are no pending requirements instead of showing an empty page
- **Avatar loading** - user avatars no longer re-fetch on every hover in the mention dropdown

### Improvements
- **Mobile-friendly tile reel** - home page tiles are responsive (1 on mobile, 2 on tablet, 3 on desktop) with always-visible navigation arrows and touch swipe support
- **Sidebar home link** - clicking the Ember logo/name in the sidebar navigates to the home page

### Technical Changes

#### Database
- **Generated `full_name` column** - `account.full_name` stored generated column replaces repeated `TRIM(first_name || ' ' || last_name)` in SQL queries (patch_7)

#### Backend Architecture
- **`MentionType` enum** - replaces raw strings for bulk mention types (`GROUP`, `EVENT`, `REGISTERED`, `DECLINED`)
- **`BulkMentionedInComment` domain event** - new event type resolved by `BulkMentionedInCommentHandler` to individual member notifications
- **`COMMENT_MENTION` notification type** - separate from `NEWS_COMMENT`, with `CommentMention` params and own locale key
- **KB comment events moved to service** - domain event publishing for KB comments moved from `KnowledgeBaseRoutes` to `KnowledgeBaseService`
- **`NewsService` resolves author name** - derives author name from `MemberIdentity` via account lookup instead of requiring callers to pass it
- **Event date validation** - backend derives event date for one-time events from `startTime` and validates day-of-week for recurring events
- **Restriction-filtered completions** - `/station-members/completions` accepts optional `restrictionType` and `entityId` params to filter by entity visibility

#### Frontend Architecture
- **`EventRegistrationsTab` component** - extracted registration logic from `EventDetailView` (319 lines, down from 506)
- **`MentionInput` unified suggestions** - refactored to support members, groups, and special mentions in a single dropdown
- **`UserAvatar` watcher fix** - watches derived `stationUid/memberUid` string instead of deep-watching the identity object

## v26.6.0

### New Features

#### Boards (Planer)
- **Kanban boards** - per-station scrum/kanban boards with customizable lanes, drag-and-drop ticket reordering between lanes, and position indicators
- **Ticket management** - create, edit, delete tickets with title, rich markdown description (tiptap editor), priority (5 levels with icons), assignee, due date, and custom fields
- **Checklists** - add checklists to tickets with drag-and-drop reordering, progress bar, and bulk delete
- **Ticket links** - link tickets with typed relationships (Relates to, Blocks, Blocked by, Causes, Caused by) with confluence-style display
- **Weblinks** - add external URLs to tickets
- **File attachments** - upload files to tickets with tile-based preview grid; image thumbnails, PDF viewer, and CSV table preview in a fullscreen overlay with keyboard navigation (arrow keys)
- **Labels** - color-coded labels per board with multi-select dropdown, inline creation, and label filter on the board and archived views
- **Knowledge base links** - link KB pages to tickets with title search and folder path display
- **Comments** - threaded comments using CommentThread component with @mentions, reply, edit, delete
- **Watch/unwatch** - subscribe to ticket changes and receive notifications
- **Activity feed** - interleaved timeline of comments, lane transitions, and history entries (priority changes, label assignments, title/description/due date changes, field changes) with rich formatting (lane color pills, priority icons, label badges)
- **Lane colors** - assign colors to lanes; used for lane column top borders and the ticket status button
- **Lane assignee** - custom field type `lane_assignee` that auto-assigns a member when a ticket moves to the referenced lane
- **Backlog** - board-level toggle that creates a hidden backlog lane; dedicated table view at `/station/boards/:boardId/backlog`
- **Archived view** - dedicated table view for tickets past the hide-done-after-days threshold at `/station/boards/:boardId/archived` with label filtering
- **Board overview** - `/station/boards` shows only boards accessible to the user
- **Board management** - `/station/boards/manage` for managers to create, edit, delete boards with settings icon per card
- **Board settings** - lane editor with color picker, field editor (string, number, boolean, enum, date, lane_assignee), backlog toggle, view/edit access restrictions
- **Due date reminders** - daily notification to assignee for overdue tickets not in the last lane
- **Full-text search** - PostgreSQL tsvector/tsquery on ticket title and description with relevance ranking
- **Read-only access** - users with view-only access see all content but cannot edit; all edit controls hidden
- **Drag-and-drop** - tickets between lanes with visual drop indicator; checklist items with grip handles

#### Board Access & Permissions
- **Role hierarchy in board access** - MANAGER role now correctly grants access to TEAM-restricted boards via transitive role expansion
- **Dedicated can-edit endpoint** - `GET /boards/{id}/can-edit` for frontend to check edit permission
- **View/edit access restrictions** - per-board role, group, and tag based access control

#### Permission System
- **Granular permissions** - replaced the flat role system with a hierarchical permission tree; each feature area (events, members, inventory, boards, etc.) has its own read/edit/manage permissions
- **User type permissions** - assign extra permissions to entire user types (Trial, Member, Guardian, Team) station-wide via a new management page
- **Permission picker** - new hierarchical permission selector with collapsible groups, icons, and descriptions; replaces the old flat role checkboxes in member edit and group management
- **Sidebar permission gating** - sidebar links are now shown or hidden based on the user's actual permissions rather than all-or-nothing manager checks
- **Read-only views** - users with read permission but not edit permission see content without edit controls (e.g. waiting list, boards)
- **Granular test permissions** - decomissioned QUIZ_MANAGER; replaced with TEST_CATALOG_VIEW, TEST_CATALOG_EDIT, TEST_CONFIGURE, TEST_RESULT_READ, TEST_REVIEW, and standalone TEST_MANAGER/PROTOCOL_MANAGER under STATION_ADMINISTRATOR
- **Protocol permissions** - PROTOCOL_CONFIGURE for definitions, PROTOCOL_CREATE for runs, PROTOCOL_TESTER for grading
- **NEWS_CREATE renamed to NEWS_EDIT** - covers creating, editing, and deleting news posts; NEWS_FEDERATE gates federation sharing
- **Form permissions** - POLL_VIEW_RESULTS for viewing analytics, POLL_CREATE for creating/editing forms; member restrictions in restriction picker
- **Station management permissions** - granular route permissions (STATION_GENERAL, STATION_LOOK_AND_FEEL, STATION_MAIL, STATION_MODULES, STATION_IMPORT_EXPORT) replace STATION_ADMINISTRATOR; sidebar restructured with manage and federation as separate top-level groups
- **Member permissions** - MEMBER_EDIT replaces MEMBER_MANAGER on import/delete/permissions/user-type routes; MEMBER_MANAGE_TAGS for tag CRUD; MEMBER_READ for GET endpoints
- **Inventory permissions** - INVENTORY_EDIT for update/delete items; MEMBER_READ for member inventory items

#### Member Identity & Display
- **Group colors** - assign a display color to groups; the highest-priority group's color is used as the member's name color everywhere
- **Tag badges** - tags can be marked visible with a color and position; they appear as inline colored pill badges next to member names
- **Unified member identity** - a single identity model (station UUID + member UUID + display name) is used everywhere from database through API to frontend
- **MemberName driven by identity** - the `MemberName` component derives its display name solely from the identity object

#### Waitlist Guardians
- **Multiple guardians per waitlist entry** - each entry can have multiple guardians with name, email, and phone number, replacing the single parent name/email fields
- **Guardian auto-onboarding** - when a waitlist entry is accepted, guardian accounts are automatically created with GUARDIAN user type, LOGIN and MEMBER_GUARDIAN permissions, and linked to the child member
- **Trial member type** - waitlist entries are created as TRIAL type until accepted, then converted to MEMBER
- **Expandable guardian details** - clicking a waitlist entry expands to show guardian contact details
- **Dedicated entry creation view** - adding waitlist entries uses a full page view instead of a modal
- **Waitlist permission split** - new WAITLIST_ADD permission for adding entries without full edit access

#### Member Detail & Edit
- **Member detail tabs** - split into tabs: Profile, Permissions, Guardians, Absences, Inventory, Notes
- **Relations tab** - new tab on member edit for assigning guardians to members and members to guardians
- **Absences tab** - users with MEMBER_EDIT can create, view, and delete absences from the member detail view
- **Permissions tab** - shows user type, permissions with human-friendly names, groups, and tags

#### Event Reminders
- **Configurable reminders** - events and event templates support multiple reminders defined in days before the event
- **Reminder scheduler** - background checker sends EVENT_REMINDER notifications to eligible members
- **Smart targeting** - public events notify all non-declined members; registration-required events notify only accepted/pending registrants
- **Template carry-over** - reminders from templates are applied when creating events from templates

#### Federated Comments
- **Event comments** - comment on events shared by federation partners; comments show the author's station badge
- **News comments** - comment on news posts shared by partners with full threading support
- **Knowledge base comments** - threaded comments on KB files with federation support and soft-delete

#### News Federation
- **Per-post sharing** - choose which news posts to share with partners: all partners or specific ones
- **Visibility role** - set a minimum role for shared news visibility at partner stations
- **Federated news in feed** - partner news posts appear inline in the news list, marked with a federation badge

#### Event Cancellation
- **Manual cancellation** - managers can cancel events with a reason
- **Auto-cancellation** - events that don't reach the minimum registration count by a threshold date are automatically cancelled
- **Cancellation notifications** - all registered members receive an EVENT_CANCELLED notification

#### Quiz & Test Improvements
- **New question types** - enumeration, ordering, matching, and fill-in-the-gap questions
- **Readonly catalog view** - users with TEST_CATALOG_VIEW see catalogs and questions with answers without edit controls
- **Test results tab** - test detail view has a Results tab showing all attempts
- **Enriched attempt detail** - single API call returns attempt, full question details, and member identity
- **Grading UX** - "Geprüft & Weiter" / "Geprüft & Beenden" shortcuts; compact icon buttons; reorganized mobile navigation

#### Other
- **Station requirements view** - shows outstanding requirements for the current member with sidebar badge
- **Board improvements** - human-readable URLs, federated ticket links, chronological activity tab, keyboard navigation
- **Sidebar counts** - all sidebar badges load in a single API call
- **Dev error handler** - filename format `HH-mm-ss - source - hash.txt`; `reportCaughtError()` for frontend catch blocks
- **Start/end date sync** - setting a start date auto-fills the end date if empty
- **Modules toggle** - added TEST_PROTOCOL and BOARDS to the modules management page

### Improvements

- **Borderless input fields** - new `borderless` prop on BaseInput/TextInput for clean inline editing
- **Click-to-edit title** - ticket title renders as heading, switches to borderless input on click
- **MemberSelectInput auto-open** - opens dropdown and focuses search immediately on mount
- **IconSelectInput auto-open** - priority selector opens dropdown immediately
- **Click-outside handling** - all sidebar editors (lane, priority, assignee, due date) close when clicking outside the right column
- **Color input component** - new ColorInput.vue for lane color selection in settings
- **SelectInput min-w-0** - global fix for dropdown width issues in flex containers
- **Checklist progress bar** - fixed invisible bar (was using undefined `--accent`, now uses `bg-primary`)
- **Overdue due dates** - highlighted in red on ticket tiles
- **Attachment count on tiles** - paperclip icon with count in ticket tile bottom row
- **Description save button** - replaced checkmark icon with proper "Speichern" PrimaryButton
- **Comment submit button** - changed to "Absenden" matching news comment pattern
- **Sidebar boards** - only shows boards the user can view (managers see all in manage view)

### Bug Fixes

- Fixed getViewAccess/getEditAccess returning empty lists instead of actual stored restriction IDs
- Fixed manage view not showing create/edit controls
- Fixed board managers seeing all boards in sidebar instead of only accessible ones
- Fixed role hierarchy not applied in board access checks (MANAGER not matching TEAM restrictions)
- Fixed file download throwing unauthorized (switched from direct URL to authenticated blob download)
- Fixed `createTicket` CTE missing `attachment_count` column causing runtime error
- Fixed KB link `folderPath` showing double `/` for root-level files
- Fixed KB links not loading on initial ticket detail page load
- Fixed checklist progress bar invisible (undefined CSS variable)
- Fixed lane top border using undefined `--accent` variable

---

### Technical Changes

#### Database
- **Patch 6** - 15 new tables: `board`, `board_lane` (with color), `board_field`, `board_view_access`, `board_edit_access`, `board_ticket` (with full-text search vector), `board_ticket_field_value`, `board_ticket_link`, `board_ticket_checklist_item`, `board_ticket_transition`, `board_ticket_comment`, `board_ticket_watcher`, `board_ticket_weblink`, `board_ticket_attachment`, `board_ticket_history`, `board_label`, `board_ticket_label`, `board_ticket_kb_link`
- Generated tsvector column with GIN index for full-text search
- Board-level `backlog_lane_id` FK for backlog support

#### Backend Architecture
- **18 new entity records** with RowMapping: Board, BoardLane, BoardField, BoardFieldConfig, BoardTicket, BoardTicketLink, LinkType, BoardTicketTransition, BoardChecklistItem, BoardComment, BoardWeblink, BoardTicketAttachment, BoardTicketFieldValue, BoardTicketWatcher, BoardTicketHistory, BoardLabel, BoardTicketKbLink, TicketPriority
- **BoardRepository** - CRUD for boards, lanes, fields, labels, access restrictions, backlog management
- **BoardTicketRepository** - CRUD for tickets, links, checklist, comments, weblinks, attachments, field values, watchers, history, KB links, activity feed (UNION ALL query)
- **BoardService** - access control with role hierarchy expansion via `Roles.expand()`, label management, backlog toggle
- **BoardTicketService** - ticket lifecycle, lane_assignee auto-assignment on move, @mention parsing in comments, watcher notifications, history logging for all changes
- **BoardRoutes / BoardTicketRoutes** - 50+ REST endpoints
- **DueDateReminderChecker** - scheduled executor for daily due date notifications
- **BoardTicketChanged** domain event - consolidated watcher notification for all ticket changes
- **MentionedInComment** extended - `BOARD_TICKET` ethentity type with ticket-detail link
- **LaneData, AccessData, TicketLabelMapping** - extracted to top-level records by spotless

#### Frontend Architecture
- **15 new views**: BoardOverviewView, BoardListView, BoardView, TicketDetailView, BoardSettingsView, BacklogView, ArchivedView + 5 help center pages
- **7 new components**: TicketTile, TicketChecklist, TicketActivity, TicketLinksSection, LabelSelectInput, ColorInput, DragList (reused)
- **boards.ts API** - 40+ functions for all board, ticket, label, attachment, KB link, and history operations
- **Authenticated file handling** - blob download/preview via axios instead of direct URLs

#### Permission Architecture
- Four new enums: `StationPermission`, `StationUserType`, `InstancePermission`, `InstanceUserType` replacing flat role strings
- `station_user_type_permission` DB table with CRUD API endpoints
- `PermissionPicker.vue` component with hierarchical display, implicit grant hiding, and "granted by" attribution
- Permission granularity across all route handlers; read-only routes accept `_READ` where previously they required manager grants

#### Member Identity
- `uid UUID` column added to `station_member`; `MemberIdentity(stationUid, memberUid)` record replaces dual local/federated representation
- `MemberIdentityFactory` service with `MemberNameResolver` Caffeine caching
- Mention format migrated to `@[stationUid/memberUid:Name]` with legacy support

#### Waitlist & Guardians
- `waiting_list_entry_guardian` table with cascade delete; migration backfills from legacy `parent_name`/`email`
- `event_reminder`, `event_template_reminder`, `event_reminder_sent` tables for reminder tracking
- `EventReminderChecker` scheduled executor (every 30 minutes)
- `QuizCatalogRepository.findQuestionsByIds()` batch query for enriched attempt detail

#### Test Coverage
- **Board repository tests** - 20+ tests covering tickets, lanes, labels, attachments, field values, weblinks, search, history, backlog, KB links
- **Board service tests** - 25+ tests covering CRUD, access control with role hierarchy, labels, backlog, field values, attachments, comments, watchers, move/reorder/link operations
- **JaCoCo exclusion** - DueDateReminderChecker, EventReminderChecker excluded (daemon pattern)
- All coverage thresholds met: 95% repositories, 90% services, 80% handlers

---

## v26.5.0

### New Features

#### Comments & @Mentions
- **Event comments** - threaded comments on events, just like news comments
- **@Mentions** - type `@` in any comment to search and tag members; they get a notification
- **Reply notifications** - replying to a comment notifies the original author
- **Soft-delete** - deleting a comment with replies shows "Dieser Kommentar wurde gelöscht" instead of removing the whole thread

#### Notes
- **Notes on inventory items, member profiles, and events** - managers can keep internal notes with version history
- Member profile notes are only visible to managers

#### Feeds (iCal, RSS, Atom)
- **iCal feed** - subscribe to your events in Thunderbird, Outlook, Google Calendar, or any calendar app
- **RSS and Atom feeds** - follow your notifications in any feed reader
- **Feed management** - generate, regenerate, or revoke your feed token; toggle which notification types appear in feeds
- Dashboard shows a reminder when feeds are not set up or inactive

#### Event Templates
- **Reusable templates** - save and load event templates with all fields, attendance settings, and registration limits
- **Quick fields** - Ort, Treffpunkt, and Thema quick-add buttons in the field editor

#### Federated Events
- **Cross-station event sharing** - share events with federation partners
- **Remote registration** - register for events at partner stations
- Partner station events shown on the upcoming events page

#### Federated Knowledge Base
- **Shared KB browsing** - browse files and folders from partner stations
- **Federated search** - search queries partner stations in parallel
- **Partner filter** - show only content from a specific partner

#### Public Calendar & Station View
- **Public calendar** - expose an event calendar for visitors without an account
- **Public station page** - unified public view with calendar and knowledge base tabs
- Event fields can be marked as public or internal

#### Event Categories
- Create, edit, reorder, and delete event categories
- Configure how many events each category shows on the overview
- Mark categories as public for the public calendar

#### Registrations
- **Grouped view** - registrations grouped by event, sorted by deadline
- **Fairness table** - acceptance/denial ratio per member for fair decision-making
- **Registration limit** - cap the number of accepted registrations per event
- **Deadline notifications** - managers are notified when a deadline expires with pending registrations

#### Inventory
- **Item detail page** - view item metadata, current assignment, full history, and manager notes

#### Theming
- **New themes** - color blind accessible themes and fire theme
- **Feel setting** - choose between rounded or cornered UI style
- **Hierarchical settings** - instance, station, and user each pick their theme; each level can lock for the level below

#### Problem Reports
- **Report a problem** - floating bug icon on all station pages; automatically captures page, roles, and recent requests
- **Admin review** - view, acknowledge, and delete problem reports

#### Admin Settings
- **Legal documents** - edit privacy policy, terms of service, consent text, and imprint
- **Mailing settings** - configure SMTP in the admin UI

### Improvements

- New help pages for theming, sessions, notifications, modules, import, federation, comments, templates, notes, categories, legal, and mailing
- iCal and RSS/Atom setup guides for Thunderbird, Outlook, Android, and iOS
- News has a dedicated detail page with always-visible comments
- Clicking a notification links directly to the relevant page and auto-acknowledges
- Sidebar headers are now clickable and collapsible
- Item names in inventory tables link to the detail page
- Admin and station settings split into focused sub-views
- Improved landing page
- **Form answer validation** - submitted answers are now validated against question rules (option range, multi-select limits, rating scale, ranking order, likert bounds)
- Absences visible to both event and attendance managers

### Bug Fixes

- Fixed @mentions not matching between frontend and backend
- Fixed deleting a comment removing all replies - now soft-deletes
- Fixed news author being notified on every comment instead of only on replies
- Fixed KB share links pointing to the wrong URL
- Fixed federated KB files navigating to a non-existent local file
- Fixed absences section visible to non-managers on event detail
- Fixed past event registrations appearing on the dashboard
- Fixed modal component warnings

---

### Technical Changes

#### Architecture
- **Domain event system** - `DomainEventBus` with Guice multibinding; 19 event handlers decouple notification logic from routes
- Services publish events after state changes; handlers create notifications
- Notifications no longer created in route handlers

#### Code Quality
- All `String config` fields replaced with typed records (`ProfileFieldConfig`, `EventFieldConfig`, `AttendanceFieldConfig`, `FormQuestionConfig`, `WaitingListFieldConfig`) with `parse()`/`toJson()`
- All `String *Type` fields replaced with proper enums (`ProfileFieldType`, `EventFieldType`, `AttendanceFieldType`, `NoteEntityType`, `CommentEntityType`, `FilterTableType`, `ContentType`, `ChangeType`)
- `QuizService.createQuestion()` accepts `QuestionConfig` instead of raw JSON
- `FormQuestionConfig.validate(FormAnswerValue)` validates answers per question type on submission
- `MultiLimitType` enum replaces raw `String multiLimitType` (NONE, AT_MOST, AT_LEAST, EXACTLY)
- Unified `QuestionType` enum - removed duplicate inner `FormQuestion.QuestionType`
- Removed dead `NewsCommented` event (superseded by `CommentCreated`)

#### Frontend Components
- `InfiniteReel`, `PublicEventList`, `DiffView`, `ThemeSelector`, `NoteEditor` components
- Comment highlight via `?comment=123` query param
- Lightweight `GET /station-members/completions` endpoint for @mention autocomplete

#### Infrastructure
- JaCoCo coverage enforcement: 95% repositories, 90% services, 80% handlers
- Unit tests for all 19 domain event handlers
- Parallel CI test jobs (`testRepositories`, `testServices`, `testOther`)
- Coverage verification across parallel CI jobs
- Javadoc verification in CI
- Comprehensive service test suite (attendance, auth, batch events, comments, consent, federation, fields, templates, feeds, forms, KB, notes, profiles, quiz, registrations, applications, protocols, settings)
- Database patch 5: public columns for stations, categories, events, fields, boards, problem reports, feed tracking

---

## v26.4.0

### New Features

#### Event Batch Import/Creation
- **Batch event creation** - create multiple events at once with a multi-step wizard (schedule, edit, confirm)
- **Date generation** - auto-generate recurring date ranges by count, interval, and event type
- **Batch edit table** - spreadsheet-style editing of generated events before creation
- **Event layouts** - reusable field templates for consistent event configuration across batch and single creation
- **Layout management view** - dedicated view for creating/editing event layouts with field configuration
- **Event filter bar** - filter upcoming events by category and other criteria
- **Events by category** - categorized display in the event index view
- **Registration stats panel** - fairness statistics for event registration acceptance/denial decisions (accepted/denied ratio per member)

#### Federation System
- **Multi-station federation** - connect with other stations to share content (Knowledge Base, Quiz catalogs, Test Protocols)
- **Partnership management** - create, suspend, resume, or end federation partnerships
- **Capability configuration** - control which content types can be shared per direction (import/export) per partner
- **Cross-instance federation** - RSA-signed HTTP communication between separate Ember instances
- **Shared content browsing** - browse KB files, quiz catalogs, and protocols shared by partners
- **One-click content copy** - copy federated content to your own station
- **Metadata caching** - browse federated content even when remote instance is temporarily unavailable
- **Webhook notifications** - real-time change notification between federated instances
- **Sync polling** - change log based sync for detecting content updates

#### Inventory Lending
- **Lending requests** - request inventory items from federated partner stations with date ranges
- **Request lifecycle** - REQUESTED → APPROVED → LENT → RETURNED → CLOSED workflow
- **Item assignment** - assign specific items to approved lending requests
- **Built-in messaging** - chat between requesting and owning stations with system messages
- **Inventory blocking** - block inventories or items during date ranges to prevent lending
- **Available browsing** - browse available inventory from partners with date filtering and search
- **Lent-out tracking** - view currently lent out items per inventory
- **Lending blocks** - tile-based creation UI supporting multiple inventories and items per block

#### Federation Discovery
- **Discovery registry** - stations can opt into being discoverable (none/instance/public visibility)
- **Public discovery page** (`/discovery`) - browse discoverable stations without login
- **Pairing codes** - stateless codes (`ember-BASE64(uid)-BASE64(host)`) for requesting federation
- **Station invite codes** - manager-generated codes that auto-activate (consent already given)
- **Pair requests** - discovery codes create pending requests that target station must accept/decline
- **Pair request management** - view and accept/decline incoming federation requests

#### Public Knowledge Base
- **Public KB mode** - OFF, ALLOW_ALL, or DENY_ALL per station
- **Per-file/folder visibility override** - override the global mode for individual items
- **Public browsing** - unauthenticated access to browse, read, and search public KB content
- **Public file viewer** - rendered markdown, PDF download, image display, YouTube embeds
- **Full-text search** - PostgreSQL tsvector search on public content with snippets

#### Unified Restrictions System
- **Consolidated architecture** - single restriction table per entity type replacing scattered tables
- **Flexible modes** - AND/OR logic for combining role, group, tag, and member restrictions
- **Role hierarchy** - transitive permission inheritance in PostgreSQL (MANAGER → TEAM → LOGIN)
- **Manager bypass** - management roles automatically bypass restrictions in their domain
- **Database functions** - efficient PL/pgSQL restriction checking with member identity resolution

#### Quiz AI Generation
- **AI-powered question generation** - generate quiz questions and wrong answers via AI providers
- **Batch generation** - generate multiple questions per category with context awareness
- **Custom prompts** - override default prompts per generation batch
- **Async job processing** - long-running generation with polling for results

#### Quiz CSV Import
- **CSV file import** - import questions from CSV into quiz catalogs
- **Column mapping** - flexible mapping of CSV columns to question fields
- **Custom separators** - configure separators for columns and multi-answer fields

#### API Monitoring (Admin)
- **Request logging** - all API requests logged with method, path, status code, and duration
- **Performance dashboard** - slowest/fastest endpoints, hourly stats, status code breakdown
- **Endpoint detail view** - drill into individual endpoints for response time charts and request history
- **Problem log** - application-wide problem logging with acknowledge/filter functionality

#### GDPR Export Improvements
- **ZIP format** - data export downloads as ZIP instead of plain JSON
- **PDF summary** - human-readable Typst-generated PDF with account info, memberships, inventory
- **User files included** - KB files created by the user bundled in the ZIP

#### Station Export/Import
- **UUID preservation** - station UUID preserved during transfer (federation codes survive)
- **Knowledge base export** - KB folders, files, content, and version history in station transfer
- **Logo export** - station logo transferred as base64

### Improvements

#### Frontend Architecture
- **Component library expansion** - 30+ new base components (Table, Typography, Display, Input, Discovery)
- **Convention linting** - automated checks for raw HTML elements, CSS class count, repeated patterns, file size
- **Help center linting** - validates every route has a corresponding help article
- **Icon linting** - verifies all FontAwesome icons are properly registered
- **View decomposition** - large views split into focused sub-components (Attendance, Inventory, Members, Quiz, Knowledge Base)
- **Style guide** - updated `/style` page showcasing all base components

#### Knowledge Base
- **Edit modals** - improved file/folder editing with restrictions, tags, and public visibility

#### Attendance
- **Session view refactoring** - decomposed into toolbar, header, member list, check mode, summary, and fields panels
- **Rapid check mode** - fast check-in/out workflow

#### Events
- **Export modal** - configurable event data export

#### Waiting List
- **Detail sub-views** - separated into overview, waiting, invites, testing, and finished sections

#### Theme & UI
- **Theme initialization fix** - dark/light mode applies correctly on first visit
- **Dark mode chart colors** - fixed ECharts label colors in dark mode
- **Station switcher** - improved station selection UI in footer

#### Quiz
- **Question point calculation rework** - improved scoring logic for quiz questions
- **Code cleanup** - refactored quiz configuration editors and catalog views

#### Federation
- **Webhook service cleanup** - improved reliability and code quality
- **Federation service refactoring** - cleaner entity handling with proper enums for ChangeType and ContentType
- **HTTP client improvements** - better error handling in federation communication

#### Admin
- **Station management** - enhanced with federation, discovery, and module settings
- **Docker workflow** - releases tag as `latest`, pushes to `main` tag as `dev`

### Security & Technical

- **Station-scoped access enforcement** - all entity read/write operations now validate that the authenticated user belongs to the correct station, preventing cross-station data access even with a valid session
- **Repository hardening** - queries now consistently filter by station ID to prevent unauthorized cross-station reads (Events, News, Members, Forms, Inventory, Knowledge Base, Attendance, Groups, Tags, Waiting List, Federation)
- **RSA-2048 signing** - federation requests cryptographically signed
- **Station UUIDs** - external identifiers prevent enumeration
- **Role hierarchy enforcement** - database-level transitive permission checking
- **Private key per station** - generated at station creation

### Privacy Policy

- Updated data export description to reflect ZIP+PDF+files format (Art. 15 and Art. 20 GDPR)

### Infrastructure

- **Renovate** - automated dependency updates with 14-day stabilization, auto-merge for minor/patch
- **Database patches 4-6** - federation tables, unified restrictions, role hierarchy, API logging, discovery settings

### Bug Fixes

- Fixed admin problems view not truncating error messages
- Fixed event field editor and value input handling for new field types
- Fixed attendance service integration with event batch creation

---

## v1.2.0

### New Features

#### Test Protocols (Prüfungsprotokolle)

- **Full test protocol system** for practical exams (e.g. Jugendflamme) - create protocol templates with hierarchical sections, subsections, and individual checkboxes with 0.5 or 1 point values
- **Protocol builder**: create and edit protocols with sections, subsections, and items. Edit protocol name, description, and pass threshold. Add descriptions to sections and items
- **Test runs**: create a test run from a protocol template, select members to test by group/role/individual. Runs have OPEN/CLOSED lifecycle
- **Touch-optimized grading wizard**: step-by-step or section-selectable grading view with large touch-friendly checkboxes. Auto-saves on every check. Section selector tabs with live score progress
- **Member locking**: while a tester grades a member, others are locked out. Re-entry allowed for the same tester. Auto-unlock on exit
- **Section completion tracking**: mark sections as "tested" with checkmark indicators. Track progress per member (e.g. "5/7 Abschnitte")
- **Evaluation table**: color-coded matrix view (like the Jugendflamme CSV) with sections as rows, members as columns. Average column. Pastel color coding (green ≥90%, yellow ≥60%, orange ≥30%, red <30%). Sticky first 3 columns for horizontal scrolling. Filter for incomplete members
- **PDF exports**:
  - Per-member protocol PDF (landscape, two-column): logo + station name header, checkboxes, per-section tester names, section headers as 3-column table (Name | Prüfer | Score), right-aligned points, horizontal separator lines
  - Evaluation table PDF (landscape): full matrix with pastel cell coloring, subsection detail rows, bold sum rows with separators, station branding
  - ZIP download: all member PDFs + evaluation table in a single ZIP file
- **Demo data**: Jugendflamme Stufe 1 protocol seeded with all 7 sections (Notruf, Knoten, Schläuche, Verteiler, Strahlrohr, Erste Hilfe, Unterflurhydrant). Open run for current year + completed run from last year with randomized scores
- **Roles**: `PROTOCOL_MANAGEMENT` (create/manage protocols and runs) and `PROTOCOL_TESTER` (grade members), both included in MANAGER
- **Module**: `TEST_PROTOCOL` (toggleable per station)
- **Help center**: dedicated help page with structure explanation, grading demo, and locking description

## v1.1.0

### New Features

#### Knowledge Base (Lernsammlung)

- **Rich text editor** (Tiptap-based) with full WYSIWYG formatting: bold, italic, underline, strikethrough, headings (H1–H3), bullet/ordered lists, blockquotes, code blocks, tables, horizontal rules, colored text, highlighted text
- **Editor refactored** into self-contained sub-components: `EditorToolbar`, `EditorTableBar`, `EditorLinkDialog`, `EditorImageDialog`, `EditorVideoDialog`, `EditorBubbleMenu`, `ImageNodeView`
- **Image support**: upload images or insert from URL, with resizable width controls directly below each image in the editor
- **Video embedding**: YouTube, Vimeo, PeerTube, Dailymotion - auto-detects provider and generates correct embed URL
- **Link dialog**: Confluence-style floating panel with KB file search by title, folder path display, and inline text editing. Replaces native `prompt()` dialogs
- **Link tooltip**: hovering on a link shows URL, edit button, open-in-new-tab button, and unlink button
- **Table editing**: contextual toolbar for adding/removing rows and columns, sticky below the app header for long documents
- **Raw markdown toggle**: switch between rich text and raw markdown view
- **Bubble menu**: formatting toolbar on text selection; link tooltip on link hover; dismiss button to close without losing selection
- **Word document import**: upload `.docx`, `.odt`, `.rtf`, `.html` files - automatically converted to markdown via pandoc
- **PDF text extraction**: uploaded PDFs are indexed for full-text search using Apache PDFBox
- **Search improvements**: prefix matching (e.g. "Notr" matches "Notruf"), highlighted snippets with yellow `<mark>` tags, markdown/HTML stripped from snippet text
- **Related files**: "further reading" links between KB files with add/remove UI on file detail page
- **File detail view**: shows last edit time and editor name, editable description, leaves edit mode after saving
- **Tags**: case-insensitive tag autocomplete on files and folders
- **Folder icons**: upload custom icons for folders, displayed in grid and list views. Icon updates now persist correctly in the database
- **Version history**: colored diff view with proper green/red backgrounds using `color-mix()`, version author names displayed
- **Condensed list view**: compact file browser with divider-separated rows instead of card containers
- **Binary file storage on disk**: PDFs, images, and other binary files stored in `data/kb-files/` instead of the database. Dropped `content BYTEA` column from `kb_file_content`
- **Link entries**: open in new tab instead of iframe embed
- **YouTube metadata**: fetches video title/author via oEmbed API for search indexing
- **Formatting showcase**: demo file in KB root showing all supported editor formatting

#### Quiz System

- **Full quiz feature**: catalogs, categories, question management, test creation, grading
- **Question types**: Multiple Choice, Fill-in-the-Blank, Free Answer, Connect, Image+Text, True/False, Ordering, Enumeration
- **AI question generation**: supports OpenAI, Anthropic Claude, Google Gemini. Session-based multi-turn conversations to avoid duplicate questions. Polling endpoint for streaming results
- **CSV import**: dedicated view with 3-step flow (upload → column mapping → preview/edit). Backend CSV parsing with Apache Commons CSV. Per-question answer splitting, type-specific configuration, AI wrong answer generation
- **PDF export**: Typst-based with checkboxes, fill-in-the-blank gaps, word banks, section summaries, image embedding, page break control
- **Test lifecycle**: DRAFT → ACTIVE → CLOSED with frozen questions generated at activation. Attempt counting per student
- **Auto-grading**: MC, T/F, connect, ordering, fill-blank auto-graded on submit. Free answer/image text require manual grading
- **Config as JsonNode**: question config stored as typed JSON objects instead of raw strings

#### Waiting List

- **Full waiting list feature**: registration forms with custom fields, invite codes, scoring formulas
- **Status lifecycle**: WAITING → INVITED → TESTING → JOINED/WITHDRAWN with timestamp recording for each transition
- **Member creation**: on invite, creates station member with testing group assignment
- **Attendance tracking**: testing members added to attendance sessions via their testing group, attendance count tracked
- **Self-service**: public registration page, interest confirmation, self-withdrawal via token
- **Auto-confirmation**: scheduled daemon checks for expired confirmations, sends reminders, auto-withdraws after grace period
- **Editable registration date**: managers can edit when an entry was added to the waitlist
- **Email notifications**: registration confirmation, confirm reminder, removal warning templates (DE + EN)
- **Demo data**: seeded entries across all statuses with attendance records

#### Admin Settings

- **Platform settings view**: station registration toggle, auth config (token sizes, session duration), mailing config (SMTP), legal document editing with versioning
- **Patch notes view**: pulls releases from GitHub API, renders release notes with markdown formatting, accessible via clickable version in footer

### UI & Component Improvements

- **SelectionToggleButton**: shared component for role/group/tag toggle selections (replaces raw buttons in 6+ views: EventEditView, IndexView, EventModal, NewsEditView, AbsenceView)
- **DropdownMenuItem**: shared component for dropdown menu items (used in KnowledgeBaseView)
- **Markdown content CSS**: comprehensive `.markdown-content` class replacing non-functional `prose` classes (Tailwind Typography plugin was not installed). Covers headings, lists, quotes, tables, code blocks, images, iframes, horizontal rules, alternating table row backgrounds
- **`--border` CSS variable**: properly defined for light (`#c0c0c0`) and dark (`#3a3a3a`) modes - fixes invisible borders throughout the app
- **Search snippet highlighting**: matched terms shown with yellow `<mark>` background
- **EmberLogo component**: reusable logo display with blink animation, used across landing page, sidebar, help center, 404 pages
- **ThemePicker component**: theme color selection
- **NotFoundContent/NotFoundView**: 404 pages with branding
- **FormulaInput component**: formula editor for waiting list scoring
- **Style guide updated**: SelectionToggleButton and DropdownMenuItem added to `/style`
- **Help center**: added pages for Knowledge Base editor, admin settings; updated existing pages

### Infrastructure

- **Data directory initialization**: legal document templates bundled in JAR, copied to `data/` on first startup if files are missing
- **`.dockerignore`**: excludes `data/`, build artifacts, and IDE files from Docker builds
- **WebP image support**: TwelveMonkeys ImageIO library for native WebP reading; graceful fallback for unsupported formats
- **Pandoc integration**: `PANDOC_BIN` env variable for document conversion (defaults to `pandoc`)
- **Strikethrough in CommonMark**: added GFM strikethrough extension to the markdown renderer
- **Request body redaction**: auth, AI, and config endpoints excluded from request/response logging
- **Shared utilities**: `CsvParser` (Apache Commons CSV), `PandocConverter`, `TextDiff` (unified diff patches), `TypstCompiler` (PDF generation)
- **Unit tests**: markdown rendering, quiz PDF export, waiting list service, score evaluator

### Bug Fixes

- Fixed diff view colors using `color-mix()` instead of broken Tailwind CSS variable opacity
- Fixed `prose` classes doing nothing - replaced with custom `.markdown-content` CSS
- Fixed markdown toggle crash (`el is null`) by using `v-show` instead of `v-if` for editor content
- Fixed link clicks opening URLs in the editor - intercepted via `editorProps.handleClick`
- Fixed horizontal rule invisible - changed border color to `color-mix(in srgb, var(--text) 25%, transparent)`
- Fixed heading buttons not working - added `clearNodes()` before `setHeading()` to exit lists/blockquotes
- Fixed P button no effect - changed to `clearNodes().setParagraph()`
- Fixed images not showing in editor - lift `<img>` out of `<p>` tags before setting editor content
- Fixed image upload for WebP - added fallback for formats ImageIO can't read
- Fixed folder icon not showing after upload - now updates `folder.iconUrl` in database
- Fixed table controls bar not appearing - moved reactive refs before `useEditor()` call
- Fixed BubbleMenu conflicts - merged two BubbleMenus into one with `shouldShow` callback
- Fixed search snippets showing tsvector tokens - now uses `ts_headline` on actual `text_content`
- Fixed demo mode station registration - disabled via `station_registration_enabled` setting

### Dependencies Added

- `@tiptap/*` (vue-3, starter-kit, extensions for table, highlight, youtube, image, color, text-style, underline, link, placeholder)
- `turndown` (HTML → Markdown conversion)
- `marked` (Markdown → HTML parsing)
- `diff` (text diffing for version history)
- Apache PDFBox 3.0.5 (PDF text extraction)
- Apache Commons CSV 1.14.0 (CSV parsing)
- TwelveMonkeys ImageIO WebP 3.13.0 (WebP image support)
- OpenAI Java SDK, Anthropic Java SDK, Google GenAI SDK (AI question generation)
- java-diff-utils 4.15 (unified diff patches)
