# GDPR Consent and Data Privacy

Satisfy the requirements written here: https://gdpr.eu/gdpr-consent-requirements/
We are falling under this rule:
- You have a legitimate interest to process someone’s personal data. This is the most flexible lawful basis, though the “fundamental rights and freedoms of the data subject” always override your interests, especially if it’s a child’s data.

Implement a proof of consent at described here: https://www.cookieyes.com/documentation/proof-of-consent/
Use the cloudflare geolocation header to determine the user's location.
If no header is present just leave it empty.
Rework the current consent banner that is shown when the user wants to login on, ensuring it complies with GDPR standards and includes clear information about data collection and usage, such as the purpose of data collection, the types of data collected, and the rights of the user to access, rectify, or delete their personal data.
It should also be composed dynamically based on user given markdown files 

Use common mark to parse the markdown files.
```
version("commonmark","0.28.0")
library("commonmark", "org.commonmark", "commonmark").versionRef("commonmark")
library("commonmark-ext-gfm-tables", "org.commonmark", "commonmark-ext-gfm-tables").versionRef("commonmark")
library("commonmark-ext-heading-anchor", "org.commonmark", "commonmark-ext-heading-anchor").versionRef("commonmark")
library("commonmark-ext-autolink", "org.commonmark", "commonmark-ext-autolink").versionRef("commonmark")
bundle("commonmark", listOf("commonmark", "commonmark-ext-gfm-tables", "commonmark-ext-heading-anchor", "commonmark-ext-autolink"))
```

Generate a data privacy from markdown files.
Files should be given in a order which is essentially defined by alphabetical order of the files in a directory.
The privacy policy should be generated dynamically based on the service used for messages for example.
Same goes for the consent banner. Make sure to emphrase that we store data in cookies and local storage of the browser.

Make sure that we emphathise that we do not share any personal data stored in the product.
Ensure that instead of archiving a member we can also delete it by anonymizing its data and deleting the mail adress, making it impossible to identify the member.

