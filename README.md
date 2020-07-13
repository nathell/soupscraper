# soupscraper

_Dej, mam umierajoncom zupe_

soupscraper is a downloader for Soup.io.

## Usage

1. [Install Clojure](https://clojure.org/guides/getting_started#_clojure_installer_and_cli_tools)
2. Clone this repo, cd to the main directory
3. `clojure -A:run` to see the options
4. `clojure -A:run https://yoursoup.soup.io` or just `clojure -A:run yoursoup` to do the full magic

If you want to just download a few pages, add the `--earliest` (or `-e`) option. For example: `clojure -A:run -e 2020-07-01 yoursoup` will skip simulating infinite scroll as soon as it encounters posts from June 2020 or earlier.

## FAQ

**I’m on Windows! How can I run this?**

Dunno for now (Clojure’s cli-tools for Windows are notoriously hard to install), but I’ll try to provide a jar ASAP. When that’s done, you’ll just need Java.

**I ran this and it completed, where’s my soup?**

In `$HOME/skyscraper-data/cache/html/soup/yoursoup`.

**But, but… it’s in some weird format!**

Yes, there’s a bunch of files there; you can’t easily view them. Technically, they’re HTMLs and assets, stored as [netstrings](https://cr.yp.to/proto/netstrings.txt), and preceded by another netstring corresponding to HTTP headers as obtained from server, in [edn](https://github.com/edn-format/edn) format.

There are several upsides for having it this way:

- You can abort the program at any time, and restart it later. It won’t redownload stuff; rather, it will reuse what it’s already downloaded.

- Once you’ve downloaded it, it’s there. When Soup.io finally goes dead, it will continue to be there, and you’ll be able to re-run future versions of the program.

Watch this repo. In a few days, it will be able to generate a locally browsable copy of Soup. You know, `index.html` and stuff. But not quite yet.

**It’s hung / doing something weird!**

Try to abort it (^C) and restart. It’s safe.

If you continue to have problems, there’s some logs in `log/`. Create an issue in this repo and attach the logs, possibly trimming them. I’ll see what I can do, but can’t promise anything.

**How’d you write this?**

It uses my scraping framework, [Skyscraper](https://github.com/nathell/skyscraper). Check it out.
