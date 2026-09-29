import QtQuick 2.12

// A text-only button: the app draws no boxed controls.
Text {
    id: root
    signal clicked()
    property bool enabledLink: true
    color: enabledLink ? Theme.ink : Theme.inkFaint
    font.family: Theme.sans
    font.pixelSize: 15
    padding: 8
    activeFocusOnTab: true
    Accessible.role: Accessible.Button
    Accessible.name: text
    Accessible.onPressAction: root.clicked()
    MouseArea {
        anchors.fill: parent
        enabled: root.enabledLink
        onClicked: root.clicked()
    }
    Keys.onReturnPressed: if (enabledLink) clicked()
    Keys.onSpacePressed: if (enabledLink) clicked()
}
