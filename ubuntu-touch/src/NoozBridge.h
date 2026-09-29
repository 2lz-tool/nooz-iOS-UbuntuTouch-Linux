#pragma once

#include <QObject>
#include <QThreadPool>
#include <QVariant>
#include <QVariantList>

/*
 * QML's view of the Nooz core. Every call into libnooz_core happens on one worker thread (the core is
 * a single store and its calls block on the network), and results come back as signals / properties
 * on the GUI thread.
 */
class NoozBridge : public QObject {
    Q_OBJECT
    Q_PROPERTY(QVariantList items READ items NOTIFY itemsChanged)
    Q_PROPERTY(QVariantList sources READ sources NOTIFY sourcesChanged)
    Q_PROPERTY(bool refreshing READ refreshing NOTIFY refreshingChanged)
    Q_PROPERTY(bool busy READ busy NOTIFY busyChanged)
    Q_PROPERTY(QString status READ status NOTIFY statusChanged)

public:
    NoozBridge(const QString &dataDir, const QString &cacheDir, const QString &assetsDir, QObject *parent = nullptr);

    QVariantList items() const { return m_items; }
    QVariantList sources() const { return m_sources; }
    bool refreshing() const { return m_refreshing; }
    bool busy() const { return m_pending > 0; }
    QString status() const { return m_status; }

    Q_INVOKABLE void refresh();
    Q_INVOKABLE void addSource(const QString &url);
    Q_INVOKABLE void addFeed(const QString &url, const QString &title);
    Q_INVOKABLE void removeSource(const QString &id);
    Q_INVOKABLE void setSourceEnabled(const QString &id, bool enabled);
    Q_INVOKABLE void openArticle(const QString &id);

    /// Re-read the item and source lists from the core (cheap; done after every change).
    void reload();

signals:
    void itemsChanged();
    void sourcesChanged();
    void refreshingChanged();
    void busyChanged();
    void statusChanged();
    void sourceAdded(const QVariantMap &result);
    void articleReady(const QVariantMap &result);
    void ready();

private:
    template <typename Fn> void run(Fn fn);
    void setPending(int delta);
    void setStatus(const QString &s);

    QThreadPool m_pool;
    QVariantList m_items;
    QVariantList m_sources;
    bool m_refreshing = false;
    int m_pending = 0;
    QString m_status;
};
