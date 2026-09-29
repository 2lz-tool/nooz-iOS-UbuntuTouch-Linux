cmake_minimum_required(VERSION 3.12)
project(nooz VERSION 0.3.1 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)
set(CMAKE_AUTORCC ON)
set(CMAKE_INCLUDE_CURRENT_DIR ON)

find_package(Qt5 REQUIRED COMPONENTS Core Gui Qml Quick Concurrent)

# libnooz_core.so: the Kotlin/Native core (see core/native-bridge). Clickable builds it first and
# passes its directory; a plain desktop build points NOOZ_CORE_DIR at
# core/native-bridge/build/bin/linuxX64/releaseShared.
set(NOOZ_CORE_DIR "" CACHE PATH "Directory containing libnooz_core.so")
if(NOT NOOZ_CORE_DIR)
    message(FATAL_ERROR "Set -DNOOZ_CORE_DIR to the directory holding libnooz_core.so")
endif()
find_library(NOOZ_CORE nooz_core PATHS ${NOOZ_CORE_DIR} NO_DEFAULT_PATH REQUIRED)

# Test hooks (snapshot + boot feed) exist only when asked for; a shipped binary has no such switches.
option(NOOZ_TEST_HOOKS "Compile the screenshot/boot-feed test hooks" OFF)

add_executable(nooz src/main.cpp src/NoozBridge.cpp src/NoozBridge.h qml/qml.qrc)
target_link_libraries(nooz Qt5::Core Qt5::Gui Qt5::Qml Qt5::Quick Qt5::Concurrent ${NOOZ_CORE})
if(NOOZ_TEST_HOOKS)
    target_compile_definitions(nooz PRIVATE NOOZ_TEST_HOOKS=1)
endif()
# Find lib/ next to the binary both in the click package and in a build tree.
set_target_properties(nooz PROPERTIES INSTALL_RPATH "\$ORIGIN/lib;\$ORIGIN/../lib;\$ORIGIN" BUILD_RPATH "${NOOZ_CORE_DIR}")

# Click package layout: everything at the package root; Clickable sets CMAKE_INSTALL_PREFIX to it.
set(FONTS ${CMAKE_CURRENT_SOURCE_DIR}/../core/design/src/commonMain/composeResources/font)
install(TARGETS nooz RUNTIME DESTINATION ${CMAKE_INSTALL_PREFIX})
install(FILES ${NOOZ_CORE} DESTINATION ${CMAKE_INSTALL_PREFIX}/lib)
install(FILES
    ${FONTS}/hyle_print_medium.ttf ${FONTS}/hyle_print_regular.ttf ${FONTS}/pt_serif_regular.ttf
    ${FONTS}/hyle_grotesk_classic_regular.ttf ${FONTS}/hyle_grotesk_classic_medium.ttf
    DESTINATION ${CMAKE_INSTALL_PREFIX}/fonts)
install(FILES assets/nooz.png DESTINATION ${CMAKE_INSTALL_PREFIX})
install(FILES nooz.desktop nooz.apparmor manifest.json DESTINATION ${CMAKE_INSTALL_PREFIX})
