/* End-to-end check of libnooz_core's C API, with no Qt involved:
 *   c_smoke <libdir> <base-url-of-tools/native/testserver.py>
 * Opens a fresh store, adds the test feed, refreshes, lists items, opens an article, marks it read. */
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef char *(*open_fn)(const char *, const char *, const char *);
typedef char *(*str_fn)(const char *);
typedef char *(*void_fn)(void);
typedef char *(*items_fn)(int);
typedef void (*free_fn)(char *);

static int failures = 0;
#define CHECK(cond, msg) do { if (!(cond)) { printf("FAIL: %s\n", msg); failures++; } else { printf("ok:   %s\n", msg); } } while (0)

int main(int argc, char **argv) {
    if (argc < 3) { fprintf(stderr, "usage: c_smoke <libdir> <base-url>\n"); return 2; }
    char path[1024];
    snprintf(path, sizeof path, "%s/libnooz_core.so", argv[1]);
    void *lib = dlopen(path, RTLD_NOW);
    if (!lib) { fprintf(stderr, "dlopen: %s\n", dlerror()); return 2; }
    open_fn nooz_open = dlsym(lib, "nooz_open");
    str_fn add_source = dlsym(lib, "nooz_add_source");
    void_fn refresh = dlsym(lib, "nooz_refresh");
    void_fn sources = dlsym(lib, "nooz_sources");
    items_fn items = dlsym(lib, "nooz_items");
    str_fn article = dlsym(lib, "nooz_article");
    str_fn mark_read = dlsym(lib, "nooz_mark_read");
    free_fn nfree = dlsym(lib, "nooz_free");
    CHECK(nooz_open && add_source && refresh && items && article && mark_read && nfree, "all symbols exported");

    char tmpl[] = "/tmp/nooz-smoke-XXXXXX";
    char *root = mkdtemp(tmpl);
    char data[1100], cache[1100];
    snprintf(data, sizeof data, "%s/data", root);
    snprintf(cache, sizeof cache, "%s/cache", root);

    char *r = nooz_open(data, cache, "");
    CHECK(strstr(r, "\"ok\":true"), "nooz_open"); nfree(r);

    char url[1200];
    snprintf(url, sizeof url, "%s/feed.xml", argv[2]);
    r = add_source(url);
    printf("add_source -> %s\n", r);
    CHECK(strstr(r, "\"ok\":true") && strstr(r, "Test Wire"), "add_source resolves the feed title"); nfree(r);

    r = refresh();
    printf("refresh -> %s\n", r);
    CHECK(strstr(r, "\"new\":4"), "refresh ingests the four items"); nfree(r);

    r = items(50);
    CHECK(strstr(r, "Council approves a new tram line"), "items lists the newest headline");
    CHECK(strstr(r, "\"read\":false"), "nothing is read yet");
    char *idp = strstr(r, "\"id\":\"");
    char id[512] = {0};
    if (idp) { idp += 6; char *end = strchr(idp, '"'); memcpy(id, idp, end - idp); }
    printf("first id = %s\n", id);
    nfree(r);

    r = article(id);
    printf("article -> %.300s\n", r);
    CHECK(strstr(r, "\"full\":true") && strstr(r, "first paragraph of story"), "article is fetched and extracted");
    nfree(r);

    r = mark_read(id); nfree(r);
    r = items(50);
    CHECK(strstr(r, "\"read\":true"), "mark_read is reflected in items"); nfree(r);

    r = sources();
    CHECK(strstr(r, "Test Wire"), "sources lists the feed"); nfree(r);

    r = article("no-such-id");
    CHECK(strstr(r, "\"ok\":false"), "unknown article is an error, not a crash"); nfree(r);

    printf(failures ? "\n%d FAILED\n" : "\nALL OK\n", failures);
    return failures ? 1 : 0;
}
