#include <QDir>
#include <QGuiApplication>
#include <QQmlContext>
#include <QQmlEngine>
#include <QQuickItem>
#include <QQuickView>
#include <QStandardPaths>
#include <QUrl>
#include <QTimer>

#include "NoozBridge.h"

#ifdef NOOZ_TEST_HOOKS
#include <QImage>
#include <QProcessEnvironment>
#endif

int main(int argc, char *argv[]) {
    QCoreApplication::setOrganizationName("nooz");
    QCoreApplication::setApplicationName("nooz");
    QGuiApplication app(argc, argv);

    const QString appDir = QCoreApplication::applicationDirPath();
    // Ubuntu Touch confines each app to its own XDG directories; QStandardPaths already points at them.
    const QString dataDir = QStandardPaths::writableLocation(QStandardPaths::AppDataLocation);
    const QString cacheDir = QStandardPaths::writableLocation(QStandardPaths::CacheLocation);
    QDir().mkpath(dataDir);
    QDir().mkpath(cacheDir);

#ifdef NOOZ_TEST_HOOKS
    const QProcessEnvironment env = QProcessEnvironment::systemEnvironment();
    const QString fontsOverride = env.value("NOOZ_FONTS_DIR");
#endif
    QString fontsDir = appDir + "/fonts";
#ifdef NOOZ_TEST_HOOKS
    if (!fontsOverride.isEmpty()) fontsDir = fontsOverride;
#endif

    NoozBridge bridge(dataDir, cacheDir, appDir + "/assets");

    QQuickView view;
    view.setResizeMode(QQuickView::SizeRootObjectToView);
    view.setTitle("Nooz");
    view.engine()->rootContext()->setContextProperty("nooz", &bridge);
    view.engine()->rootContext()->setContextProperty("fontsDir", QUrl::fromLocalFile(fontsDir).toString());
    view.setSource(QUrl("qrc:/Main.qml"));
    if (view.status() != QQuickView::Ready) return 1;
    view.resize(400, 720);
    view.show();

#ifdef NOOZ_TEST_HOOKS
    // Test-only: NOOZ_BOOT_FEED adds a feed and refreshes; NOOZ_OPEN_FIRST opens the first story;
    // NOOZ_SNAPSHOT writes the window to a PNG at NOOZ_SIZE=WxH and exits.
    const QString size = env.value("NOOZ_SIZE");
    if (!size.isEmpty()) {
        const QStringList wh = size.split('x');
        view.resize(wh.value(0).toInt(), wh.value(1).toInt());
    }
    const QString bootFeed = env.value("NOOZ_BOOT_FEED");
    const QString snapshot = env.value("NOOZ_SNAPSHOT");
    QObject::connect(&bridge, &NoozBridge::ready, &app, [&] {
        if (!bootFeed.isEmpty()) bridge.addSource(bootFeed);
    });
    QObject::connect(&bridge, &NoozBridge::itemsChanged, &app, [&] {
        if (env.contains("NOOZ_OPEN_FIRST") && !bridge.items().isEmpty()) {
            static bool opened = false;
            if (!opened) {
                opened = true;
                QMetaObject::invokeMethod(view.rootObject(), "openItem", Qt::QueuedConnection, Q_ARG(QVariant, bridge.items().first()));
            }
        }
    });
    if (!snapshot.isEmpty()) {
        // Give the boot feed time to arrive, then shoot. The wait is generous because CI runners are slow.
        QTimer::singleShot(env.value("NOOZ_SNAPSHOT_DELAY_MS", "6000").toInt(), &app, [&] {
            QImage img = view.grabWindow();
            if (img.isNull() || !img.save(snapshot)) qWarning("snapshot failed (null=%d)", img.isNull());
            qInfo("LAYOUT twoPane=%d", view.rootObject()->property("twoPane").toBool() ? 1 : 0);
            app.quit();
        });
    }
#endif

    return app.exec();
}
