cmake_minimum_required(VERSION 3.20)
project(mimikopi_audio C CXX)

# The desktop piano engine: miniaudio (WASAPI on Windows) + the Android engine's sampler and effects.
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

find_package(JNI REQUIRED COMPONENTS JVM)

add_library(mimikopi_audio SHARED
    src/miniaudio_impl.c
    src/clock.cpp
    src/sample_store.cpp
    src/desktop_engine.cpp
    src/jni_bridge.cpp
)
target_include_directories(mimikopi_audio PRIVATE
    src
    third_party/miniaudio
    ${JNI_INCLUDE_DIRS}
)
target_compile_definitions(mimikopi_audio PRIVATE _USE_MATH_DEFINES NOMINMAX _CRT_SECURE_NO_WARNINGS)
if(MSVC)
    # One self-contained DLL: no Visual C++ runtime to install on the user's computer.
    set_property(TARGET mimikopi_audio PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded")
    target_compile_options(mimikopi_audio PRIVATE /W3 /utf-8)
else()
    target_compile_options(mimikopi_audio PRIVATE -Wall -Wno-unused-function)
    find_package(Threads REQUIRED)
    target_link_libraries(mimikopi_audio PRIVATE Threads::Threads ${CMAKE_DL_LIBS} m)
endif()

# The library lands in one place for every build type, and a copy goes to dist/<platform>, which the installer
# bundles (Compose's appResourcesRootDir) and tests load.
set_target_properties(mimikopi_audio PROPERTIES
    RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}
    RUNTIME_OUTPUT_DIRECTORY_RELEASE ${CMAKE_BINARY_DIR}
    LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}
    LIBRARY_OUTPUT_DIRECTORY_RELEASE ${CMAKE_BINARY_DIR}
)
if(WIN32)
    set(MIMIKOPI_DIST ${CMAKE_CURRENT_SOURCE_DIR}/dist/windows)
elseif(APPLE)
    set(MIMIKOPI_DIST ${CMAKE_CURRENT_SOURCE_DIR}/dist/macos)
else()
    set(MIMIKOPI_DIST ${CMAKE_CURRENT_SOURCE_DIR}/dist/linux)
endif()
add_custom_command(TARGET mimikopi_audio POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E make_directory ${MIMIKOPI_DIST}
    COMMAND ${CMAKE_COMMAND} -E copy $<TARGET_FILE:mimikopi_audio> ${MIMIKOPI_DIST}/
)
