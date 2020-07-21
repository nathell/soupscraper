## Update 2020-07-21: Pssst, it still works

Soup.io is officially ~dead~ in new hands now, but old servers haven’t been turned off yet. So apparently you still have a chance of backing up your soup.

Here’s how:

1. [Edit your hosts file](https://support.rackspace.com/how-to/modify-your-hosts-file/) ([instructions for macOS](https://www.imore.com/how-edit-your-macs-hosts-file-and-why-you-would-want)) and add the following entries:

```
45.153.143.247     soup.io
45.153.143.247     YOURSOUP.soup.io
45.153.143.248     asset.soup.io
```

Put in your soup’s name in place of `YOURSOUP`.

2. Follow the instructions below.

# soupscraper

_Dej, mam umierajoncom zupe_

soupscraper is a downloader for Soup.io. Here’s a screencast of the local copy that it can generate for you:

![Screencast](https://user-images.githubusercontent.com/43891/87584084-3fe0f180-c6dd-11ea-84d7-ba84d8824a3b.gif)

See an example [here](http://soup.tomash.eu/archive/).

## Usage

1. [Install Clojure](https://clojure.org/guides/getting_started#_clojure_installer_and_cli_tools)
2. Clone this repo, cd to the main directory
3. `clojure -A:run` to see the options
4. `clojure -A:run https://yoursoup.soup.io` or just `clojure -A:run yoursoup` to do the full magic

If you want to just download a few pages, add the `--earliest` (or `-e`) option. For example: `clojure -A:run -e 2020-07-01 yoursoup` will skip simulating infinite scroll as soon as it encounters posts from June 2020 or earlier.

### Without installing Clojure

1. Install Java if you haven’t already (tested on JRE 11, any version >=8 should work)
2. [Download the jar](https://github.com/nathell/soupscraper/releases)
3. Follow step 3 or 4 above, replacing `clojure -A:run` with `java -Djdk.tls.client.protocols=TLSv1,TLSv1.1,TLSv1.2 -jar soupscraper-0.1.jar`

For example:

```
java -Djdk.tls.client.protocols=TLSv1,TLSv1.1,TLSv1.2 -jar soupscraper-20200717.jar yoursoup
```

## FAQ

**I’m on Windows! How can I run this?**

Use the “Without installing Clojure” approach above.

**I ran this and it completed, where’s my soup?**

In `soup`. Unless you change the output directory with `--output-dir`.

**There’s some shit in `~/skyscraper-data` which takes up a lot of space!**

Yes, there’s a bunch of files there; you can’t easily view them. Technically, they’re HTMLs and assets, stored as [netstrings](https://cr.yp.to/proto/netstrings.txt), and preceded by another netstring corresponding to HTTP headers as obtained from server, in [edn](https://github.com/edn-format/edn) format.

There are several upsides for having a local cache of this kind.

- You can abort the program at any time, and restart it later. It won’t redownload stuff; rather, it will reuse what it’s already downloaded.

- Once you’ve downloaded it, it’s there. When Soup.io finally goes dead, it will continue to be there, and you’ll be able to re-run future versions of the program.

If you’re super happy about your output in `soup`, you can delete `~/skyscraper-data`, but be aware that from then on you’ll need to redownload everything if you want to update your output.

**It’s hung / doing something weird!**

Try to abort it (^C) and restart. It’s safe.

If you continue to have problems, there’s some logs in `log/`. Create an issue in this repo and attach the logs, possibly trimming them. I’ll see what I can do, but can’t promise anything.

**How’d you write this?**

It uses my scraping framework, [Skyscraper](https://github.com/nathell/skyscraper). Check it out.

## License

Copyright 2020, Daniel Janus and contributors

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
