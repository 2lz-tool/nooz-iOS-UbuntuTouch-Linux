import QtQuick 2.12

// "Nooz", the date, and the red-and-white dashed rule the paper edition has under its title.
Item {
    id: root
    property alias dateText: date.text
    default property alias actions: actionRow.data
    implicitHeight: 112

    Text {
        id: mark
        text: "Nooz"
        font.family: Theme.wordmark
        font.pixelSize: 34
        color: Theme.ink
        anchors { left: parent.left; leftMargin: Theme.pad; top: parent.top; topMargin: 12 }
        Accessible.role: Accessible.Heading
        Accessible.name: "Nooz"
    }
    Text {
        id: date
        font.family: Theme.sans
        font.pixelSize: 12
        color: Theme.inkDim
        anchors { right: parent.right; rightMargin: Theme.pad; baseline: mark.baseline }
    }
    Row {
        id: actionRow
        spacing: 0
        anchors { left: parent.left; leftMargin: Theme.pad - 8; bottom: parent.bottom; bottomMargin: 4 }
    }
    Canvas {
        id: dashes
        height: 8
        anchors { left: parent.left; right: parent.right; leftMargin: Theme.pad; rightMargin: Theme.pad; top: mark.bottom; topMargin: 10 }
        onWidthChanged: requestPaint()
        onPaint: {
            var c = getContext("2d")
            c.clearRect(0, 0, width, height)
            c.fillStyle = Theme.stripe
            var dash = 9, gap = 6
            for (var x = 0; x < width; x += dash + gap) {
                c.beginPath()
                c.moveTo(x, height); c.lineTo(x + 5, 0); c.lineTo(x + 5 + dash - 3, 0); c.lineTo(x + dash - 3, height)
                c.closePath(); c.fill()
            }
        }
    }
}
