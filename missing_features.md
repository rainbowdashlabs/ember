# Station export

Allow exporting the station data via api into another ember instance.
For that create a token that can be entered into the other ember instance. The host url needs to be given as well. Require that both instances are on the same version to avoid fuckups. The version is contained in the environment variables shown in the docker file on prod instances.
Verify that it works with a unit test.
The response might be required to be chunked to avoid memory issues and exceeding of the host machines memory.
do not include gdpr and other instance specific data. Also ignore account roles and session tokens.

# Rename Member manager to guardian
rename the enum value and the framing of the role through the product.

# Locally serve fontawesome fonts in production
Serve the fontawesome fonts locally in production.

# Improve caching
Since the website is mostly running behind cloudflare caching can be quite powerful to reduce api hits.
Use the cache-control headers to control the caching behavior.
Use the etag header to cache responses and avoid revalidation.
Use the last-modified header to cache responses and avoid revalidation.
Use other measures to improve api performance especially for static data.