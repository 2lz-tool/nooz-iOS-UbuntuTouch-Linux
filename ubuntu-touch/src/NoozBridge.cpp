#include "NoozBridge.h"

#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QtConcurrent>

extern "C" {
char *nooz_open(const char *data_dir, const char *cache_dir, const char *assets_dir);
void nooz_free(char *p);
char *nooz_add_source(const char *url);
char *nooz_add_feed(const char *url, const char *title);
char *nooz_sources(void);
char *nooz_set_source_enabled(const char *id, int enabled);
char *nooz_remove_source(const char *id);
char *nooz_refresh(void);
char *nooz_items(int limit);
char *nooz_article(const char *id);
char *nooz_mark_read(const char *id);
}

namespace {
// Take ownership of a core string: parse it, free it.
QJsonDocument take(char *raw) {
    if (!raw) return QJsonDocument();
    QJsonDocument doc = QJsonDocument::fromJson(QByteArray(raw));
    nooz_free(raw);
    return doc;
}
QVariantMap takeMap(char *raw) { return take(raw).object().toVariantMap(); }
QVariantList takeList(char *raw) { return take(raw).array().toVariantList(); }
}

NoozBridge::NoozBridge(const QString &dataDir, const QString &cacheDir, const QString &assetsDir, QObject *parent)
    : QObject(parent) {
    m_pool.setMaxThreadCount(1);
    const QByteArray d = dataDir.toUtf8(), c = cacheDir.toUtf8(), a = assetsDir.toUtf8();
    run([this, d, c, a] {
        QVariantMap opened = takeMap(nooz_open(d.constData(), c.constData(), a.constData()));
        QVariantList items = takeList(nooz_items(200));
        QVariantList sources = takeList(nooz_sources());
        QMetaObject::invokeMethod(this, [this, opened, items, sources] {
            if (!opened.value("ok").toBool()) setStatus(opened.value("error").toString());
            m_items = items; m_sources = sources;
            emit itemsChanged(); emit sourcesChanged(); emit ready();
        });
    });
}

template <typename Fn> void NoozBridge::run(Fn fn) {
    setPending(+1);
    QtConcurrent::run(&m_pool, [this, fn] {
        fn();
        QMetaObject::invokeMethod(this, [this] { setPending(-1); });
    });
}

void NoozBridge::setPending(int delta) {
    const bool wasBusy = busy();
    m_pending += delta;
    if (wasBusy != busy()) emit busyChanged();
}

void NoozBridge::setStatus(const QString &s) {
    if (m_status == s) return;
    m_status = s;
    emit statusChanged();
}

void NoozBridge::reload() {
    run([this] {
        QVariantList items = takeList(nooz_items(200));
        QVariantList sources = takeList(nooz_sources());
        QMetaObject::invokeMethod(this, [this, items, sources] {
            m_items = items; m_sources = sources;
            emit itemsChanged(); emit sourcesChanged();
        });
    });
}

void NoozBridge::refresh() {
    if (m_refreshing) return;
    m_refreshing = true;
    emit refreshingChanged();
    setStatus(QString());
    run([this] {
        QVariantMap r = takeMap(nooz_refresh());
        QVariantList items = takeList(nooz_items(200));
        QVariantList sources = takeList(nooz_sources());
        QMetaObject::invokeMethod(this, [this, r, items, sources] {
            m_refreshing = false;
            emit refreshingChanged();
            m_items = items; m_sources = sources;
            emit itemsChanged(); emit sourcesChanged();
            if (!r.value("ok").toBool()) setStatus(r.value("error").toString());
            else if (r.value("failedSources").toInt() > 0)
                setStatus(tr("%n source(s) could not be reached.", "", r.value("failedSources").toInt()));
            else setStatus(QString());
        });
    });
}

void NoozBridge::addSource(const QString &url) {
    const QByteArray u = url.toUtf8();
    run([this, u] {
        QVariantMap r = takeMap(nooz_add_source(u.constData()));
        QMetaObject::invokeMethod(this, [this, r] { emit sourceAdded(r); });
        if (r.value("ok").toBool() && !r.contains("choose")) {
            // A new source is worth showing straight away.
            QVariantMap fetched = takeMap(nooz_refresh());
            Q_UNUSED(fetched)
        }
        QVariantList items = takeList(nooz_items(200));
        QVariantList sources = takeList(nooz_sources());
        QMetaObject::invokeMethod(this, [this, items, sources] {
            m_items = items; m_sources = sources;
            emit itemsChanged(); emit sourcesChanged();
        });
    });
}

void NoozBridge::addFeed(const QString &url, const QString &title) {
    const QByteArray u = url.toUtf8(), t = title.toUtf8();
    run([this, u, t] {
        QVariantMap r = takeMap(nooz_add_feed(u.constData(), t.constData()));
        QMetaObject::invokeMethod(this, [this, r] { emit sourceAdded(r); });
        takeMap(nooz_refresh());
        QVariantList items = takeList(nooz_items(200));
        QVariantList sources = takeList(nooz_sources());
        QMetaObject::invokeMethod(this, [this, items, sources] {
            m_items = items; m_sources = sources;
            emit itemsChanged(); emit sourcesChanged();
        });
    });
}

void NoozBridge::removeSource(const QString &id) {
    const QByteArray i = id.toUtf8();
    run([this, i] {
        takeMap(nooz_remove_source(i.constData()));
        QVariantList items = takeList(nooz_items(200));
        QVariantList sources = takeList(nooz_sources());
        QMetaObject::invokeMethod(this, [this, items, sources] {
            m_items = items; m_sources = sources;
            emit itemsChanged(); emit sourcesChanged();
        });
    });
}

void NoozBridge::setSourceEnabled(const QString &id, bool enabled) {
    const QByteArray i = id.toUtf8();
    run([this, i, enabled] {
        takeMap(nooz_set_source_enabled(i.constData(), enabled ? 1 : 0));
        QVariantList items = takeList(nooz_items(200));
        QVariantList sources = takeList(nooz_sources());
        QMetaObject::invokeMethod(this, [this, items, sources] {
            m_items = items; m_sources = sources;
            emit itemsChanged(); emit sourcesChanged();
        });
    });
}

void NoozBridge::openArticle(const QString &id) {
    const QByteArray i = id.toUtf8();
    run([this, i] {
        QVariantMap article = takeMap(nooz_article(i.constData()));
        if (article.value("ok").toBool()) takeMap(nooz_mark_read(i.constData()));
        QVariantList items = takeList(nooz_items(200));
        QMetaObject::invokeMethod(this, [this, article, items] {
            emit articleReady(article);
            m_items = items;
            emit itemsChanged();
        });
    });
}
