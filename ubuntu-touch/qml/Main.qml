import QtQuick 2.12

// The whole app. The layout is a function of width alone, exactly as on Android and desktop:
// below 840 the Stand and the article are separate screens, from 840 up they sit side by side.
Rectangle {
    id: root
    color: Theme.field
    focus: true

    readonly property bool twoPane: width >= Theme.twoPaneMinWidth
    property string page: "stand"     // "stand" | "sources"
    property var current: null        // the open list row
    property var article: null
    property bool readerOpen: false   // single-pane only: the article is showing

    FontLoader { source: fontsDir + "/hyle_print_medium.ttf"; onNameChanged: Theme.serif = name }
    FontLoader { source: fontsDir + "/pt_serif_regular.ttf"; onNameChanged: Theme.wordmark = name }
    FontLoader { source: fontsDir + "/hyle_grotesk_classic_regular.ttf"; onNameChanged: Theme.sans = name }
    FontLoader { source: fontsDir + "/hyle_print_regular.ttf" }
    FontLoader { source: fontsDir + "/hyle_grotesk_classic_medium.ttf" }

    function openItem(item) {
        current = item
        article = null
        readerOpen = true
        nooz.openArticle(item.id)
    }

    function goBack() {
        if (page === "sources") page = "stand"
        else if (!twoPane && readerOpen) readerOpen = false
    }

    Keys.onEscapePressed: goBack()
    Keys.onBackPressed: goBack()

    Connections {
        target: nooz
        onArticleReady: if (root.current && result.id === root.current.id) root.article = result
        onItemsChanged: {
            // A wide window never shows an empty reading pane: rest on the newest story.
            if (root.twoPane && !root.current && nooz.items.length > 0) root.openItem(nooz.items[0])
        }
    }
    onTwoPaneChanged: if (twoPane && !current && nooz.items.length > 0) openItem(nooz.items[0])

    Item {
        id: stand
        anchors.fill: parent
        visible: root.page === "stand"

        // Phone: the list, or the article over it. Wide: both.
        Item {
            id: listPane
            anchors { left: parent.left; top: parent.top; bottom: parent.bottom }
            width: root.twoPane ? Theme.listPaneWidth : parent.width
            visible: root.twoPane || !root.readerOpen

            Masthead {
                id: mast
                anchors { left: parent.left; right: parent.right; top: parent.top }
                dateText: Qt.formatDate(new Date(), "d MMMM yyyy")
                TextLink { text: nooz.refreshing ? qsTr("Refreshing…") : qsTr("Refresh"); enabledLink: !nooz.refreshing && nooz.sources.length > 0; onClicked: nooz.refresh() }
                TextLink { text: qsTr("Sources"); onClicked: root.page = "sources" }
            }
            Text {
                id: status
                anchors { left: parent.left; right: parent.right; top: mast.bottom; leftMargin: Theme.pad; rightMargin: Theme.pad }
                text: nooz.status
                visible: text.length > 0
                wrapMode: Text.Wrap
                font.family: Theme.sans; font.pixelSize: 12; color: Theme.inkDim
                height: visible ? implicitHeight + 8 : 0
            }
            StandList {
                anchors { left: parent.left; right: parent.right; top: status.bottom; bottom: parent.bottom }
                model: nooz.items
                selectedId: root.current ? root.current.id : ""
                onOpened: root.openItem(item)
                visible: nooz.items.length > 0
            }
            Column {
                anchors { left: parent.left; right: parent.right; verticalCenter: parent.verticalCenter; margins: Theme.pad * 2 }
                spacing: 12
                visible: nooz.items.length === 0 && !nooz.refreshing
                Text {
                    width: parent.width; wrapMode: Text.Wrap; horizontalAlignment: Text.AlignHCenter
                    text: nooz.sources.length === 0 ? qsTr("Nothing here yet. Add a source and Nooz will show what it publishes.") : qsTr("Nothing to show yet. Pull the latest from your sources.")
                    font.family: Theme.serif; font.pixelSize: 20; color: Theme.inkDim
                }
                TextLink {
                    anchors.horizontalCenter: parent.horizontalCenter
                    text: nooz.sources.length === 0 ? qsTr("Add a source") : qsTr("Refresh")
                    onClicked: nooz.sources.length === 0 ? root.page = "sources" : nooz.refresh()
                }
            }
        }

        Rectangle {
            id: divider
            visible: root.twoPane
            anchors { left: listPane.right; top: parent.top; bottom: parent.bottom }
            width: 1; color: Theme.hairline
        }

        ReaderView {
            anchors { left: root.twoPane ? divider.right : parent.left; right: parent.right; top: parent.top; bottom: parent.bottom }
            visible: root.current !== null && (root.twoPane || root.readerOpen)
            item: root.current
            article: root.article
            showBack: !root.twoPane
            onBack: root.readerOpen = false
        }
    }

    SourcesPage {
        anchors.fill: parent
        visible: root.page === "sources"
        onDone: root.page = "stand"
    }
}
