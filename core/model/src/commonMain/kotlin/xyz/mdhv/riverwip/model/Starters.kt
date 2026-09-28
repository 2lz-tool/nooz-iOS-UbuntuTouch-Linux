package xyz.mdhv.riverwip.model

/**
 * Built-in starter registry (brief §P1). Expressed in the [Catalogue] schema so
 * P6 can swap in the remote `catalogue.json` with no migration.
 *
 * **Every concrete feed here was verified live at build time** (brief §0). The
 * verification run on 2026-07-07 fetched each URL and confirmed a parseable feed
 * with items; entries that only rotted or blocked were dropped. Excluded after
 * verification: CNN edition RSS (HTTP 503 — retired), Wikinews Special:NewsFeed
 * (HTTP 404), The Wire & The Print (served HTML, no feed items), Deccan Herald
 * (no resolving RSS path found). See STATE.md verification log.
 *
 * `verifiedAt` stamps carry the check date; the P6 CI sentry keeps them fresh.
 */
object Starters {

    private const val V = "2026-07-07"

    private fun rss(
        id: String, title: String, url: String, region: String,
    ) = ServiceDef(
        id = id, kind = "rss", title = title, tier = "A", region = region,
        url = url, verifiedAt = V,
    )

    private const val V2 = "2026-07-11"

    /** All-region expansion, each fetched and confirmed live on 2026-07-11 (STATE.md log). */
    private fun rssJul11(
        id: String, title: String, url: String, region: String,
    ) = ServiceDef(
        id = id, kind = "rss", title = title, tier = "A", region = region,
        url = url, verifiedAt = V2,
    )

    private const val V3 = "2026-07-13"

    /** Depth expansion (Europe/Africa split + more countries), fetched and confirmed live on 2026-07-13 (STATE.md log). */
    private fun rssJul13(
        id: String, title: String, url: String, region: String,
    ) = ServiceDef(
        id = id, kind = "rss", title = title, tier = "A", region = region,
        url = url, verifiedAt = V3,
    )

    private const val V4 = "2026-08-31"

    /**
     * India regional-language expansion (D35). Every entry below was fetched on
     * 2026-08-31 with the User-Agent `HttpClient` actually sends, and checked
     * on four axes rather than just "did it 200": a parseable feed root, at
     * least five items, item titles in the *expected script*, and a newest item
     * no more than a week old. That last pair matters more than it sounds —
     * two candidate feeds returned a clean 200 with a `lastBuildDate` refreshed
     * daily while every actual item was six weeks stale, and one returned a
     * well-formed channel containing zero items.
     */
    private fun rssAug31(
        id: String, title: String, url: String, region: String = "india",
    ) = ServiceDef(
        id = id, kind = "rss", title = title, tier = "A", region = region,
        url = url, verifiedAt = V4,
    )

    /** Concrete, verified RSS feeds — regionally balanced (global + India). */
    val verifiedFeeds: List<ServiceDef> = listOf(
        // --- Global ---
        rss("bbc-top", "BBC News", "https://feeds.bbci.co.uk/news/rss.xml", "global"),
        rss("bbc-world", "BBC World", "https://feeds.bbci.co.uk/news/world/rss.xml", "global"),
        rss("npr-news", "NPR News", "https://feeds.npr.org/1001/rss.xml", "global"),
        rss("guardian-world", "The Guardian: World", "https://www.theguardian.com/world/rss", "global"),
        rss("guardian-intl", "The Guardian: International", "https://www.theguardian.com/international/rss", "global"),
        rss("aljazeera-all", "Al Jazeera", "https://www.aljazeera.com/xml/rss/all.xml", "global"),
        rss("dw-en", "Deutsche Welle (EN)", "https://rss.dw.com/rdf/rss-en-all", "global"),
        rss("france24-en", "France 24 (EN)", "https://www.france24.com/en/rss", "global"),
        rss("nyt-world", "The New York Times: World", "https://rss.nytimes.com/services/xml/rss/nyt/World.xml", "global"),
        rss("nyt-top", "The New York Times: Top Stories", "https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml", "global"),
        rss("propublica", "ProPublica", "https://www.propublica.org/feeds/propublica/main", "global"),
        // --- India ---
        rss("thehindu-home", "The Hindu", "https://www.thehindu.com/feeder/default.rss", "india"),
        rss("thehindu-national", "The Hindu: National", "https://www.thehindu.com/news/national/feeder/default.rss", "india"),
        rss("ndtv-top", "NDTV: Top Stories", "https://feeds.feedburner.com/ndtvnews-top-stories", "india"),
        rss("toi-top", "The Times of India: Top", "https://timesofindia.indiatimes.com/rssfeedstopstories.cms", "india"),
        rss("indian-express", "The Indian Express", "https://indianexpress.com/feed/", "india"),
        rss("livemint", "Livemint", "https://www.livemint.com/rss/news", "india"),
        rss("hindustan-times", "Hindustan Times: India", "https://www.hindustantimes.com/feeds/rss/india-news/rssfeed.xml", "india"),
        rss("business-standard", "Business Standard", "https://www.business-standard.com/rss/home_page_top_stories.rss", "india"),
        rss("scroll-in", "Scroll.in", "https://feeds.feedburner.com/ScrollinArticles", "india"),
        // === India: regional languages (2026-08-31, D35) ===
        // The language name stays in the visible title on purpose: the sources
        // search is a plain substring match over exactly this string, so
        // "Telugu" or "Odia" finds these without inferring language from a URL
        // or adding a metadata field the catalogue schema (v1) doesn't have.
        // --- Telugu ---
        rssAug31("tv9-telugu-ap", "TV9 Telugu: Andhra Pradesh", "https://tv9telugu.com/andhra-pradesh/feed"),
        rssAug31("tv9-telugu-telangana", "TV9 Telugu: Telangana", "https://tv9telugu.com/telangana/feed"),
        rssAug31("sakshi-telugu", "Sakshi (Telugu)", "https://www.sakshi.com/rss.xml"),
        // --- Tamil ---
        rssAug31("daily-thanthi-tamil", "Daily Thanthi (Tamil)", "https://www.dailythanthi.com/feed"),
        rssAug31("thanthi-tv-tamil", "Thanthi TV (Tamil)", "https://www.thanthitv.com/api/v1/collections/latest-news.rss"),
        rssAug31("hindu-tamil", "Hindu Tamil Thisai (Tamil)", "https://www.hindutamil.in/feed"),
        // --- Kannada ---
        rssAug31("tv9-kannada", "TV9 Kannada", "https://www.tv9kannada.com/feed"),
        // --- Malayalam ---
        rssAug31("mathrubhumi-malayalam", "Mathrubhumi (Malayalam)", "https://www.mathrubhumi.com/rss/news"),
        rssAug31("twentyfour-malayalam", "24 News (Malayalam)", "https://www.twentyfournews.com/feed"),
        // --- Marathi ---
        rssAug31("tv9-marathi", "TV9 Marathi", "https://www.tv9marathi.com/feed"),
        // --- Gujarati ---
        rssAug31("tv9-gujarati", "TV9 Gujarati", "https://www.tv9gujarati.com/feed"),
        rssAug31("abp-asmita-gujarati", "ABP Asmita (Gujarati)", "https://gujarati.abplive.com/home/feed"),
        // --- Bengali ---
        rssAug31("abp-ananda-bengali", "ABP Ananda (Bengali)", "https://bengali.abplive.com/home/feed"),
        // --- Punjabi ---
        rssAug31("abp-sanjha-punjabi", "ABP Sanjha (Punjabi)", "https://punjabi.abplive.com/home/feed"),
        // --- Odia ---
        rssAug31("dharitri-odia", "Dharitri (Odia)", "https://www.dharitri.com/feed"),
        rssAug31("samaja-odia", "The Samaja (Odia)", "https://www.samajalive.in/feed"),
        // --- Urdu ---
        rssAug31("siasat-urdu", "The Siasat Daily (Urdu)", "https://urdu.siasat.com/feed"),
        // --- Northeast (English-language, the region's own wire) ---
        rssAug31("northeast-now", "Northeast Now", "https://nenow.in/feed"),
        // --- Hindi: national ---
        rssAug31("abp-hindi", "ABP News (Hindi)", "https://www.abplive.com/home/feed"),
        rssAug31("bhaskar-hindi", "Dainik Bhaskar (Hindi)", "https://www.bhaskar.com/rss-v1--category-1061.xml"),
        // --- Hindi: state desks. ABP's own per-state sections; note their feed
        // <title> is only the section slug ("bihar"), so the display name here
        // is ours, not the publisher's.
        rssAug31("abp-hindi-up-uk", "ABP Hindi: Uttar Pradesh & Uttarakhand", "https://www.abplive.com/news/states/up-uk/feed"),
        rssAug31("abp-hindi-bihar", "ABP Hindi: Bihar", "https://www.abplive.com/news/states/bihar/feed"),
        rssAug31("abp-hindi-delhi-ncr", "ABP Hindi: Delhi NCR", "https://www.abplive.com/news/states/delhi-ncr/feed"),
        rssAug31("abp-hindi-rajasthan", "ABP Hindi: Rajasthan", "https://www.abplive.com/news/states/rajasthan/feed"),
        rssAug31("abp-hindi-madhya-pradesh", "ABP Hindi: Madhya Pradesh", "https://www.abplive.com/news/states/madhya-pradesh/feed"),
        rssAug31("abp-hindi-haryana", "ABP Hindi: Haryana", "https://www.abplive.com/news/states/haryana/feed"),
        rssAug31("abp-hindi-punjab", "ABP Hindi: Punjab", "https://www.abplive.com/news/states/punjab/feed"),
        rssAug31("abp-hindi-gujarat", "ABP Hindi: Gujarat", "https://www.abplive.com/news/states/gujarat/feed"),
        rssAug31("abp-hindi-chhattisgarh", "ABP Hindi: Chhattisgarh", "https://www.abplive.com/news/states/chhattisgarh/feed"),
        rssAug31("abp-hindi-jharkhand", "ABP Hindi: Jharkhand", "https://www.abplive.com/news/states/jharkhand/feed"),
        rssAug31("abp-hindi-himachal-pradesh", "ABP Hindi: Himachal Pradesh", "https://www.abplive.com/news/states/himachal-pradesh/feed"),
        rssAug31("abp-hindi-jammu-kashmir", "ABP Hindi: Jammu & Kashmir", "https://www.abplive.com/news/states/jammu-and-kashmir/feed"),
        rssAug31("abp-hindi-maharashtra", "ABP Hindi: Maharashtra", "https://www.abplive.com/news/states/maharashtra/feed"),
        // === All-region expansion — every feed fetched and confirmed live on 2026-07-11 ===
        // --- Americas ---
        rssJul11("cbc-news-canada", "CBC News (Canada)", "https://www.cbc.ca/webfeed/rss/rss-topstories", "americas"),
        rssJul11("mercopress-south-atlantic-ne", "MercoPress (South Atlantic News Agency)", "https://en.mercopress.com/rss/", "americas"),
        rssJul11("buenos-aires-times-argentina", "Buenos Aires Times (Argentina, English)", "https://www.batimes.com.ar/feed", "americas"),
        rssJul11("mexico-news-daily", "Mexico News Daily", "https://mexiconewsdaily.com/feed/", "americas"),
        rssJul11("the-tico-times-costa-rica", "The Tico Times (Costa Rica)", "https://ticotimes.net/feed", "americas"),
        rssJul11("the-rio-times-brazil", "The Rio Times (Brazil)", "https://www.riotimesonline.com/feed/", "americas"),
        rssJul11("colombia-reports", "Colombia Reports", "https://colombiareports.com/feed/", "americas"),
        rssJul11("the-santiago-times-chile", "The Santiago Times (Chile)", "https://santiagotimes.cl/feed/", "americas"),
        rssJul11("jamaica-gleaner", "Jamaica Gleaner", "https://jamaica-gleaner.com/feed/rss.xml", "americas"),
        // --- Europe & Africa ---
        rssJul11("euronews", "Euronews", "https://www.euronews.com/rss", "europe-africa"),
        rssJul11("euobserver", "EUobserver", "https://euobserver.com/feed/", "europe-africa"),
        rssJul11("mail-guardian-south-africa", "Mail & Guardian (South Africa)", "https://mg.co.za/rss/", "europe-africa"),
        rssJul11("daily-maverick-south-africa", "Daily Maverick (South Africa)", "https://www.dailymaverick.co.za/dmrss/", "europe-africa"),
        rssJul11("allafrica", "AllAfrica", "https://allafrica.com/tools/headlines/rdf/latest/headlines.rdf", "europe-africa"),
        rssJul11("premium-times-nigeria", "Premium Times (Nigeria)", "https://www.premiumtimesng.com/feed", "europe-africa"),
        rssJul11("the-africa-report", "The Africa Report", "https://www.theafricareport.com/feed/", "europe-africa"),
        // --- Middle East & Central Asia ---
        rssJul11("the-national-uae", "The National (UAE)", "https://www.thenationalnews.com/arc/outboundfeeds/rss/?outputType=xml", "mideast-casia"),
        rssJul11("middle-east-monitor", "Middle East Monitor", "https://www.middleeastmonitor.com/feed/", "mideast-casia"),
        rssJul11("al-monitor", "Al-Monitor", "https://www.al-monitor.com/rss", "mideast-casia"),
        rssJul11("middle-east-eye", "Middle East Eye", "https://www.middleeasteye.net/rss", "mideast-casia"),
        rssJul11("arab-news", "Arab News", "https://www.arabnews.com/rss.xml", "mideast-casia"),
        rssJul11("the-astana-times", "The Astana Times", "https://astanatimes.com/feed/", "mideast-casia"),
        // --- South Asia (beyond India) ---
        rssJul11("dawn", "Dawn", "https://www.dawn.com/feeds/home", "south-asia"),
        rssJul11("the-express-tribune", "The Express Tribune", "https://tribune.com.pk/feed/home", "south-asia"),
        rssJul11("the-daily-star", "The Daily Star", "https://www.thedailystar.net/top-news/rss.xml", "south-asia"),
        rssJul11("the-kathmandu-post", "The Kathmandu Post", "https://kathmandupost.com/rss", "south-asia"),
        rssJul11("the-himalayan-times", "The Himalayan Times", "https://thehimalayantimes.com/rssFeed/15", "south-asia"),
        rssJul11("the-island", "The Island", "https://island.lk/feed/", "south-asia"),
        // --- East & SE Asia ---
        rssJul11("the-japan-times", "The Japan Times", "https://www.japantimes.co.jp/feed/", "east-se-asia"),
        rssJul11("yonhap-news-english", "Yonhap News (English)", "https://en.yna.co.kr/RSS/news.xml", "east-se-asia"),
        rssJul11("the-korea-herald", "The Korea Herald", "https://www.koreaherald.com/rss/newsAll", "east-se-asia"),
        rssJul11("south-china-morning-post", "South China Morning Post", "https://www.scmp.com/rss/91/feed", "east-se-asia"),
        rssJul11("taipei-times", "Taipei Times", "https://www.taipeitimes.com/xml/index.rss", "east-se-asia"),
        rssJul11("the-straits-times-singapore", "The Straits Times (Singapore)", "https://www.straitstimes.com/news/singapore/rss.xml", "east-se-asia"),
        rssJul11("vnexpress-international-viet", "VnExpress International (Vietnam)", "https://e.vnexpress.net/rss/news.rss", "east-se-asia"),
        // --- Australia & Pacific ---
        rssJul11("abc-news-australia", "ABC News (Australia)", "https://www.abc.net.au/news/feed/1948/rss.xml", "australia-pacific"),
        rssJul11("the-sydney-morning-herald", "The Sydney Morning Herald", "https://www.smh.com.au/rss/feed.xml", "australia-pacific"),
        rssJul11("the-guardian-australia", "The Guardian Australia", "https://www.theguardian.com/australia-news/rss", "australia-pacific"),
        rssJul11("rnz-new-zealand", "RNZ (New Zealand)", "https://www.rnz.co.nz/rss/national.xml", "australia-pacific"),
        rssJul11("the-new-zealand-herald", "The New Zealand Herald", "https://www.nzherald.co.nz/arc/outboundfeeds/rss/section/nz/?outputType=xml", "australia-pacific"),
        rssJul11("rnz-pacific", "RNZ Pacific", "https://www.rnz.co.nz/rss/pacific.xml", "australia-pacific"),
        rssJul11("png-post-courier", "PNG Post-Courier", "https://www.postcourier.com.pg/feed/", "australia-pacific"),
        rssJul11("the-conversation-australia", "The Conversation (Australia)", "https://theconversation.com/au/articles.atom", "australia-pacific"),
        // === Depth expansion — every feed fetched and confirmed live on 2026-07-13 ===
        // "europe-africa" was the thinnest bucket (7 feeds covering two continents,
        // no individual European country outlets). New feeds below use fresh
        // "europe" / "africa" region tags instead of splitting "europe-africa" in
        // place, so the existing tag and its feeds above are untouched for anyone
        // already filtering by "europe-africa".
        // --- Europe ---
        rssJul13("kyiv-post-ukraine", "Kyiv Post (Ukraine)", "https://www.kyivpost.com/feed", "europe"),
        rssJul13("notes-from-poland", "Notes from Poland", "https://notesfrompoland.com/feed/", "europe"),
        rssJul13("hungary-today", "Hungary Today", "https://hungarytoday.hu/feed/", "europe"),
        rssJul13("the-baltic-times", "The Baltic Times", "https://www.baltictimes.com/rss/", "europe"),
        rssJul13("the-portugal-news", "The Portugal News", "https://www.theportugalnews.com/rss", "europe"),
        rssJul13("ansa-english-italy", "ANSA English (Italy)", "https://www.ansa.it/english/english_rss.xml", "europe"),
        rssJul13("nl-times-netherlands", "NL Times (Netherlands)", "https://nltimes.nl/rss", "europe"),
        rssJul13("dutch-news-netherlands", "DutchNews.nl (Netherlands)", "https://www.dutchnews.nl/feed/", "europe"),
        rssJul13("balkan-insight", "Balkan Insight", "https://balkaninsight.com/feed/", "europe"),
        rssJul13("emerging-europe", "Emerging Europe", "https://emerging-europe.com/feed/", "europe"),
        rssJul13("the-journal-ireland", "TheJournal.ie (Ireland)", "https://www.thejournal.ie/feed/", "europe"),
        rssJul13("rte-news-ireland", "RTE News (Ireland)", "https://www.rte.ie/feeds/rss/?index=/news/&type=news", "europe"),
        rssJul13("the-irish-times", "The Irish Times", "https://www.irishtimes.com/cmlink/the-irish-times-news-1.1319192", "europe"),
        rssJul13("the-moscow-times-russia", "The Moscow Times (Russia)", "https://www.themoscowtimes.com/rss/news", "europe"),
        rssJul13("romania-insider", "Romania Insider", "https://www.romania-insider.com/feed", "europe"),
        rssJul13("the-olive-press-spain", "The Olive Press (Spain)", "https://www.theolivepress.es/feed/", "europe"),
        rssJul13("cyprus-mail", "Cyprus Mail", "https://cyprus-mail.com/feed/", "europe"),
        // --- Africa ---
        rssJul13("the-standard-kenya", "The Standard (Kenya)", "https://www.standardmedia.co.ke/rss/headlines.php", "africa"),
        rssJul13("myjoyonline-ghana", "MyJoyOnline (Ghana)", "https://www.myjoyonline.com/feed/", "africa"),
        rssJul13("egypt-independent", "Egypt Independent", "https://www.egyptindependent.com/feed/", "africa"),
        rssJul13("lusaka-times-zambia", "Lusaka Times (Zambia)", "https://www.lusakatimes.com/feed/", "africa"),
        rssJul13("zambia-daily-mail", "Zambia Daily Mail (Zambia)", "https://www.daily-mail.co.zm/feed/", "africa"),
        rssJul13("the-namibian", "The Namibian", "https://www.namibian.com.na/feed/", "africa"),
        rssJul13("nyasa-times-malawi", "Nyasa Times (Malawi)", "https://www.nyasatimes.com/feed/", "africa"),
        rssJul13("club-of-mozambique", "Club of Mozambique", "https://clubofmozambique.com/feed/", "africa"),
        rssJul13("eye-radio-south-sudan", "Eye Radio (South Sudan)", "https://www.eyeradio.org/feed/", "africa"),
        // --- Middle East & Central Asia (depth) ---
        rssJul13("the-jerusalem-post-israel", "The Jerusalem Post (Israel)", "https://www.jpost.com/rss/rssfeedsfrontpage.aspx", "mideast-casia"),
        rssJul13("iraqi-news", "Iraqi News (Iraq)", "https://www.iraqinews.com/feed/", "mideast-casia"),
        rssJul13("the961-lebanon", "The961 (Lebanon)", "https://www.the961.com/feed/", "mideast-casia"),
        rssJul13("syria-direct", "Syria Direct", "https://syriadirect.org/feed/", "mideast-casia"),
        rssJul13("daily-sabah-turkey", "Daily Sabah (Turkey)", "https://www.dailysabah.com/rssFeed/newsfeed", "mideast-casia"),
        rssJul13("hurriyet-daily-news-turkey", "Hurriyet Daily News (Turkey)", "https://www.hurriyetdailynews.com/rss/news", "mideast-casia"),
        rssJul13("civil-georgia", "Civil Georgia", "https://civil.ge/feed", "mideast-casia"),
        rssJul13("apa-azerbaijan", "APA (Azerbaijan)", "https://en.apa.az/rss", "mideast-casia"),
        rssJul13("trend-news-azerbaijan", "Trend News Agency (Azerbaijan)", "https://en.trend.az/rss/", "mideast-casia"),
        rssJul13("asia-plus-tajikistan", "Asia-Plus (Tajikistan)", "https://asiaplus.news/en/feed/", "mideast-casia"),
        rssJul13("24kg-kyrgyzstan", "24.kg (Kyrgyzstan, English)", "https://www.24.kg/english/rss/", "mideast-casia"),
        // --- South Asia (depth) ---
        rssJul13("the-news-international-pakistan", "The News International (Pakistan)", "https://www.thenews.com.pk/rss/1/1", "south-asia"),
        rssJul13("daily-times-pakistan", "Daily Times (Pakistan)", "https://dailytimes.com.pk/feed/", "south-asia"),
        rssJul13("dhaka-tribune-bangladesh", "Dhaka Tribune (Bangladesh)", "https://www.dhakatribune.com/feed/", "south-asia"),
        rssJul13("the-business-standard-bangladesh", "The Business Standard (Bangladesh)", "https://tbsnews.net/rss.xml", "south-asia"),
        rssJul13("ada-derana-sri-lanka", "Ada Derana (Sri Lanka)", "https://www.adaderana.lk/rss.php", "south-asia"),
        rssJul13("onlinekhabar-english-nepal", "Onlinekhabar English (Nepal)", "https://english.onlinekhabar.com/feed", "south-asia"),
        rssJul13("ariana-news-afghanistan", "Ariana News (Afghanistan)", "https://ariananews.af/feed/", "south-asia"),
        rssJul13("khaama-press-afghanistan", "Khaama Press (Afghanistan)", "https://www.khaama.com/feed/", "south-asia"),
    )

    /**
     * Builder / keyed kinds. These have no single concrete `url`; they carry a
     * template + a verified example so the builder UI can preview exactly what it
     * will fetch. Liveness of the *service* was confirmed at build time where
     * possible (notes record caveats).
     */
    val builders: List<ServiceDef> = listOf(
        ServiceDef(
            id = "google-news", kind = "googlenews", title = "Google News", tier = "A", region = "global",
            homepage = "https://news.google.com/",
            urlTemplate = "https://news.google.com/rss/search?q={query}&hl={hl}&gl={gl}&ceid={ceid}",
            example = "https://news.google.com/rss?hl=en-IN&gl=IN&ceid=IN:en",
            notes = "Builder: top-stories, section topics, or keyword search per locale (hl/gl/ceid).",
            verifiedAt = V,
        ),
        ServiceDef(
            id = "gdelt-doc", kind = "gdelt", title = "GDELT DOC 2.0", tier = "A", region = "global",
            homepage = "https://www.gdeltproject.org/",
            docsUrl = "https://blog.gdeltproject.org/gdelt-doc-2-0-api-debuts/",
            urlTemplate = "https://api.gdeltproject.org/api/v2/doc/doc?query={query}&mode=artlist&format=json&maxrecords={n}&timespan={h}h",
            example = "https://api.gdeltproject.org/api/v2/doc/doc?query=climate&mode=artlist&format=json&maxrecords=75&timespan=24h",
            notes = "No key. Public API; rate-limited (may return HTTP 429/503 under load; retry with backoff).",
            freeTier = FreeTier(rateLimitPerSecond = 1.0, notes = "Soft public rate limit; be gentle."),
        ),
        ServiceDef(
            id = "mastodon", kind = "mastodon", title = "Mastodon", tier = "A", region = "global",
            urlTemplate = "https://{instance}/api/v1/timelines/tag/{tag}?limit={n}",
            example = "https://mastodon.social/api/v1/timelines/tag/news?limit=40",
            notes = "No auth for hashtag timelines (verified). The public timeline may require auth on some instances (mastodon.social returns HTTP 422).",
            verifiedAt = V,
        ),
        ServiceDef(
            id = "guardian-open-platform", kind = "guardian", title = "The Guardian: Open Platform", tier = "B",
            region = "global",
            homepage = "https://open-platform.theguardian.com/",
            keySignupUrl = "https://open-platform.theguardian.com/access/",
            requiresKey = true,
            urlTemplate = "https://content.guardianapis.com/search?api-key={key}&show-fields=headline,trailText",
            notes = "Free key required, labeled honestly. Key stored in Android Keystore, never in the repo.",
            freeTier = FreeTier(requestsPerDay = 5000, rateLimitPerSecond = 12.0, notes = "Developer tier."),
        ),
    )

    /**
     * Tier B reference (brief §P1): the generic keyed `api` kind + key-storage
     * path. One is provided as reference; the rest arrive via `catalogue.json`.
     */
    val tierBReference: List<ServiceDef> = listOf(
        ServiceDef(
            id = "gnews", kind = "api", title = "GNews", tier = "B", region = "global",
            homepage = "https://gnews.io/",
            keySignupUrl = "https://gnews.io/register",
            requiresKey = true,
            urlTemplate = "https://gnews.io/api/v4/top-headlines?category={category}&lang={lang}&apikey={key}",
            example = "https://gnews.io/api/v4/top-headlines?category=general&lang=en&apikey={key}",
            notes = "Reference Tier-B keyed provider. Key in Keystore. Others come from the catalogue.",
            freeTier = FreeTier(requestsPerDay = 100, notes = "Free tier."),
        ),
    )

    /** The full seed catalogue (what ships in the APK before any remote refresh). */
    val seed: Catalogue
        get() = Catalogue(
            version = 1,
            generatedAt = V4,
            services = verifiedFeeds + builders + tierBReference,
        )

    /** One-click concrete feeds, grouped by region for the starters UI. */
    fun feedsByRegion(): Map<String, List<ServiceDef>> =
        verifiedFeeds.groupBy { it.region ?: "other" }

    /**
     * Region tag by derived source id, for the globe's region filter. Sources
     * without a starter pedigree (added by URL/OPML) have no declared region
     * and are treated as global — a feed's geography is not guessable from its
     * URL, and guessing would be a silent lie.
     */
    val regionBySourceId: Map<String, String> by lazy {
        seed.services.mapNotNull { def ->
            def.toSourceOrNull(addedAt = 0L)?.let { it.id to (def.region ?: "global") }
        }.toMap()
    }
}
