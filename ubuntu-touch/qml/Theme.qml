pragma Singleton
import QtQuick 2.12

// The paper theme, from :core:design's Tokens (paperField / paperInk / paperInkDim / hairline).
QtObject {
    readonly property color field: "#F7F6F3"
    readonly property color raised: "#FFFFFF"
    readonly property color ink: "#141414"
    readonly property color inkDim: "#70706C"
    readonly property color inkFaint: "#C9C8C4"
    readonly property color hairline: "#1F000000"
    readonly property color danger: "#B3261E"
    readonly property color stripe: "#D62F2F"

    // Fonts are bundled next to the app (fonts/), loaded by FontLoaders in Main.qml.
    property string serif: "Hyle Print"
    property string wordmark: "PT Serif"
    property string sans: "Hyle Grotesk Classic"

    readonly property int gap: 8
    readonly property int pad: 16
    // Same rule as the Android and desktop apps: side by side from 840 wide, one pane below.
    readonly property int twoPaneMinWidth: 840
    readonly property int listPaneWidth: 400
    readonly property int readerMaxWidth: 680
}
