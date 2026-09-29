import QtQuick 2.12

// The Stand: the newest stories from the reader's own sources, one ruled row each.
ListView {
    id: list
    property var selectedId: ""
    signal opened(var item)
    clip: true
    boundsBehavior: Flickable.StopAtBounds
    activeFocusOnTab: true

    delegate: Item {
        id: row
        width: list.width
        height: content.implicitHeight + 28
        readonly property bool isSelected: list.selectedId === modelData.id
        Accessible.role: Accessible.ListItem
        Accessible.name: modelData.title + ", " + modelData.source + (modelData.read ? ", read" : ", unread")
        Accessible.onPressAction: list.opened(modelData)

        Rectangle { anchors.fill: parent; color: row.isSelected ? Theme.raised : "transparent" }
        Column {
            id: content
            anchors { left: parent.left; right: parent.right; leftMargin: Theme.pad; rightMargin: Theme.pad; verticalCenter: parent.verticalCenter }
            spacing: 6
            Text {
                width: parent.width
                text: modelData.title
                wrapMode: Text.Wrap
                font.family: Theme.serif
                font.pixelSize: 20
                lineHeight: 1.1
                color: modelData.read ? Theme.inkDim : Theme.ink
            }
            Text {
                width: parent.width
                text: modelData.source + (modelData.source ? "  ·  " : "") + Qt.formatDateTime(new Date(modelData.publishedAt), "MMM d, HH:mm")
                elide: Text.ElideRight
                font.family: Theme.sans
                font.pixelSize: 12
                color: Theme.inkDim
            }
        }
        Rectangle { anchors { left: parent.left; right: parent.right; bottom: parent.bottom }
 height: 1; color: Theme.hairline }
        MouseArea { anchors.fill: parent; onClicked: list.opened(modelData) }
    }

    footer: Item {
        width: list.width
        height: list.count > 0 ? 64 : 0
        Text {
            anchors.centerIn: parent
            visible: list.count > 0
            text: qsTr("that's everything from your sources for this period")
            font.family: Theme.sans
            font.pixelSize: 12
            color: Theme.inkDim
        }
    }
}
