cmake_minimum_required(VERSION 3.22.1)
project(mimikopi_audio CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)

add_library(mimikopi_audio SHARED
    sample_store.cpp
    piano_engine.cpp
    jni_bridge.cpp)

target_compile_options(mimikopi_audio PRIVATE -O3 -Wall -Wextra -Werror=return-type)
target_link_libraries(mimikopi_audio oboe::oboe log android)
