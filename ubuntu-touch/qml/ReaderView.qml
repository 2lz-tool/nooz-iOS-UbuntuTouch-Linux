import QtQuick 2.12

// One article: headline, byline, then the extracted text (or the feed's own summary when the page
// could not be read), capped to a comfortable measure and centred on wide windows.
Flickable {
    id: view
    property var item: null          // the list row
    property var article: null       // nooz.openArticle() result, or null while loading
    property bool showBack: false
    signal back()

    contentWidth: width
    contentHeight: column.height + 48
    clip: true
    boundsBehavior: Flickable.StopAtBounds

    Column {
        id: column
        width: Math.min(view.width - 2 * Theme.pad, Theme.readerMaxWidth)
        x: Math.max(Theme.pad, (view.width - width) / 2)
        y: 16
        spacing: 14

        TextLink {
            visible: view.showBack
            text: "‹  " + qsTr("Back")
            leftPadding: 0
            onClicked: view.back()
            Accessible.name: qsTr("Back to the list")
        }
        Text {
            visible: view.item && view.item.topic !== ""
            text: view.item ? view.item.topic.toUpperCase() : ""
            font.family: Theme.sans; font.pixelSize: 12; font.letterSpacing: 1.2
            color: Theme.inkDim
        }
        Text {
            width: parent.width
            text: view.item ? view.item.title : ""
            wrapMode: Text.Wrap
            font.family: Theme.serif; font.pixelSize: 30; lineHeight: 1.08
            color: Theme.ink
            Accessible.role: Accessible.Heading
        }
        Text {
            width: parent.width
            text: view.item ? [view.item.source, view.item.author].filter(function (s) { return s }).join("  ·  ") : ""
            wrapMode: Text.Wrap
            font.family: Theme.sans; font.pixelSize: 13
            color: Theme.inkDim
        }
        Rectangle { width: parent.width; height: 1; color: Theme.hairline }

        Text {
            visible: view.article === null
            text: qsTr("Opening…")
            font.family: Theme.sans; font.pixelSize: 15
            color: Theme.inkDim
        }
        Repeater {
            model: view.article && view.article.ok ? view.article.paragraphs : []
            Text {
                width: column.width
                text: modelData
                wrapMode: Text.Wrap
                textFormat: Text.PlainText
                font.family: Theme.sans; font.pixelSize: 17; lineHeight: 1.45
                color: Theme.ink
            }
        }
        Text {
            visible: view.article && view.article.ok && !view.article.full
            width: parent.width
            wrapMode: Text.Wrap
            text: qsTr("This source shares only a short summary in its feed.")
            font.family: Theme.sans; font.pixelSize: 13; color: Theme.inkDim
        }
        Text {
            visible: view.article && !view.article.ok
            width: parent.width
            wrapMode: Text.Wrap
            text: view.article && view.article.error ? view.article.error : ""
            font.family: Theme.sans; font.pixelSize: 15; color: Theme.danger
        }
        TextLink {
            visible: view.item !== null
            text: qsTr("Read the full story at the source") + " ↗"
            leftPadding: 0
            onClicked: Qt.openUrlExternally(view.item.url)
        }
        Item { width: 1; height: 24 }
    }
}
