import QtQuick 2.12
import QtQuick.Controls 2.12

// The reader's own sources: add a feed (or a site that has one), switch one off, remove one.
Item {
    id: page
    signal done()

    Column {
        id: head
        anchors { left: parent.left; right: parent.right; top: parent.top; margins: Theme.pad }
        spacing: 10

        Row {
            spacing: 4
            TextLink { text: "‹  " + qsTr("Back"); leftPadding: 0; onClicked: page.done() }
        }
        Text {
            text: qsTr("Sources")
            font.family: Theme.serif; font.pixelSize: 30; color: Theme.ink
            Accessible.role: Accessible.Heading
        }
        Text {
            width: parent.width
            wrapMode: Text.Wrap
            text: qsTr("Nooz shows only what your own sources publish. Paste the address of a feed, or of a site that has one.")
            font.family: Theme.sans; font.pixelSize: 13; color: Theme.inkDim
        }
        Row {
            width: parent.width
            spacing: 8
            TextField {
                id: input
                width: parent.width - addLink.width - 8
                placeholderText: "https://example.com/feed.xml"
                inputMethodHints: Qt.ImhUrlCharactersOnly | Qt.ImhNoPredictiveText
                font.family: Theme.sans; font.pixelSize: 16
                color: Theme.ink
                selectByMouse: true
                background: Rectangle { color: "transparent"; Rectangle { anchors { left: parent.left; right: parent.right; bottom: parent.bottom }
 height: 1; color: Theme.inkDim } }
                onAccepted: page.add()
                Accessible.name: qsTr("Feed or site address")
            }
            TextLink { id: addLink; text: qsTr("Add"); enabledLink: !nooz.busy && input.text.length > 0; onClicked: page.add() }
        }
        Text {
            id: feedback
            width: parent.width
            wrapMode: Text.Wrap
            font.family: Theme.sans; font.pixelSize: 13
            color: Theme.inkDim
            visible: text.length > 0
        }
        Column {
            id: choices
            width: parent.width
            spacing: 2
            Repeater {
                model: page.candidates
                TextLink {
                    width: choices.width
                    horizontalAlignment: Text.AlignLeft
                    text: modelData.title + "  —  " + modelData.url
                    wrapMode: Text.Wrap
                    onClicked: { page.candidates = []; nooz.addFeed(modelData.url, modelData.title) }
                }
            }
        }
    }

    property var candidates: []

    function add() {
        feedback.text = qsTr("Looking…")
        feedback.color = Theme.inkDim
        candidates = []
        nooz.addSource(input.text.trim())
    }

    Connections {
        target: nooz
        onSourceAdded: {
            if (!result.ok) { feedback.text = result.error; feedback.color = Theme.danger; return }
            if (result.choose) { feedback.text = qsTr("This page has several feeds. Pick one:"); feedback.color = Theme.inkDim; page.candidates = result.choose; return }
            feedback.text = qsTr("Added %1.").arg(result.title)
            feedback.color = Theme.inkDim
            input.text = ""
        }
    }

    ListView {
        id: list
        anchors { left: parent.left; right: parent.right; top: head.bottom; bottom: parent.bottom; topMargin: 16 }
        clip: true
        model: nooz.sources
        delegate: Item {
            width: list.width
            height: 64
            Column {
                anchors { left: parent.left; leftMargin: Theme.pad; right: toggle.left; verticalCenter: parent.verticalCenter }
                Text {
                    width: parent.width; elide: Text.ElideRight
                    text: modelData.title
                    font.family: Theme.serif; font.pixelSize: 18
                    color: modelData.enabled ? Theme.ink : Theme.inkDim
                }
                Text {
                    width: parent.width; elide: Text.ElideRight
                    text: modelData.error ? modelData.error : modelData.url
                    font.family: Theme.sans; font.pixelSize: 12
                    color: modelData.error ? Theme.danger : Theme.inkDim
                }
            }
            Row {
                id: toggle
                anchors { right: parent.right; rightMargin: Theme.pad - 8; verticalCenter: parent.verticalCenter }
                TextLink { text: modelData.enabled ? qsTr("On") : qsTr("Off"); onClicked: nooz.setSourceEnabled(modelData.id, !modelData.enabled) }
                TextLink { text: qsTr("Remove"); color: Theme.danger; onClicked: nooz.removeSource(modelData.id) }
            }
            Rectangle { anchors { left: parent.left; right: parent.right; bottom: parent.bottom }
 height: 1; color: Theme.hairline }
        }
    }
}
