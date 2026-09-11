cmake_minimum_required(VERSION 3.22.1)
project(airplay_native LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

set(RPIPLAY_ROOT ${CMAKE_SOURCE_DIR}/../../../../third_party/RPiPlay)
set(RPIPLAY_LIB ${RPIPLAY_ROOT}/lib)
set(LIBPLIST_ROOT ${CMAKE_SOURCE_DIR}/../../../../third_party/libplist)

if(NOT EXISTS ${RPIPLAY_LIB}/raop.c OR NOT EXISTS ${LIBPLIST_ROOT}/src/plist.c)
    message(FATAL_ERROR "Quest prototype requires real RPiPlay and libplist sources; stub builds are disabled")
endif()

#
# OpenSSL via Google's prefab AAR (com.android.ndk.thirdparty:openssl).
# AGP's prefab feature makes headers + per-ABI .so available under the
# CMAKE_FIND_ROOT_PATH AGP sets up, so find_package just works.
#
find_package(openssl REQUIRED CONFIG)

#
# libplist — compiled in-tree from third_party/libplist. We skip the C++
# bindings (.cpp files), JSON plist (jplist.c has its own dependencies
# we don't need) is kept because RPiPlay handshakes can use it, and we
# bypass the autotools config.h entirely by not defining HAVE_CONFIG_H.
#
if(EXISTS ${LIBPLIST_ROOT}/src/plist.c)
    file(GLOB LIBPLIST_SRC
        ${LIBPLIST_ROOT}/src/plist.c
        ${LIBPLIST_ROOT}/src/bplist.c
        ${LIBPLIST_ROOT}/src/xplist.c
        ${LIBPLIST_ROOT}/src/jplist.c
        ${LIBPLIST_ROOT}/src/oplist.c
        ${LIBPLIST_ROOT}/src/base64.c
        ${LIBPLIST_ROOT}/src/bytearray.c
        ${LIBPLIST_ROOT}/src/hashtable.c
        ${LIBPLIST_ROOT}/src/ptrarray.c
        ${LIBPLIST_ROOT}/src/out-default.c
        ${LIBPLIST_ROOT}/src/out-limd.c
        ${LIBPLIST_ROOT}/src/out-plutil.c
        ${LIBPLIST_ROOT}/src/time64.c
        ${LIBPLIST_ROOT}/src/jsmn.c
        ${LIBPLIST_ROOT}/libcnary/node.c
        ${LIBPLIST_ROOT}/libcnary/node_list.c
    )
    add_library(plist STATIC ${LIBPLIST_SRC})
    target_include_directories(plist PUBLIC
        ${LIBPLIST_ROOT}/include
        ${LIBPLIST_ROOT}/src
        ${LIBPLIST_ROOT}/libcnary/include
    )
    target_compile_definitions(plist PRIVATE PACKAGE_VERSION="2.6.0" _GNU_SOURCE HAVE_STRNDUP=1)
    target_compile_options(plist PRIVATE -Wno-implicit-function-declaration -Wno-int-conversion)
    set(HAVE_LIBPLIST 1)
else()
    message(WARNING "localair: third_party/libplist missing")
    set(HAVE_LIBPLIST 0)
endif()

#
# RPiPlay — only built if all deps (its own source + libplist + openssl) are
# present. Otherwise we fall back to JNI stubs so Kotlin keeps compiling.
#
if(EXISTS ${RPIPLAY_LIB}/raop.c AND HAVE_LIBPLIST)
    message(STATUS "localair: vendoring RPiPlay from ${RPIPLAY_LIB}")

    file(GLOB RPIPLAY_SRC ${RPIPLAY_LIB}/*.c)
    list(FILTER RPIPLAY_SRC EXCLUDE REGEX ".*/dnssd\\.c$")

    file(GLOB PLAYFAIR_SRC
        ${RPIPLAY_LIB}/playfair/*.c
        ${RPIPLAY_LIB}/playfair/*.cpp
    )
    file(GLOB LLHTTP_SRC ${RPIPLAY_LIB}/llhttp/*.c)

    add_library(rpiplay STATIC
        ${RPIPLAY_SRC}
        ${PLAYFAIR_SRC}
        ${LLHTTP_SRC}
        ${CMAKE_CURRENT_SOURCE_DIR}/dnssd_stub.c
    )
    target_include_directories(rpiplay PUBLIC
        ${RPIPLAY_LIB}
        ${RPIPLAY_LIB}/playfair
        ${RPIPLAY_LIB}/llhttp
    )
    target_compile_definitions(rpiplay PRIVATE _GNU_SOURCE OPENSSL_API_COMPAT=0x10101000L)
    target_compile_options(rpiplay PRIVATE -Wno-deprecated-declarations -Wno-pointer-sign -Wno-implicit-function-declaration)
    target_link_libraries(rpiplay PUBLIC plist openssl::crypto)
    set(HAVE_RPIPLAY 1)
else()
    if(NOT EXISTS ${RPIPLAY_LIB}/raop.c)
        message(WARNING "localair: third_party/RPiPlay missing — JNI stubs only")
    elseif(NOT HAVE_LIBPLIST)
        message(WARNING "localair: libplist unavailable — JNI stubs only")
    endif()
    set(HAVE_RPIPLAY 0)
endif()

add_library(airplay_native SHARED
    jni_bridge.cpp
    video_sink.cpp
    audio_sink.cpp
)
target_include_directories(airplay_native PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

if(HAVE_RPIPLAY)
    target_compile_definitions(airplay_native PRIVATE HAVE_RPIPLAY=1)
    target_link_libraries(airplay_native PRIVATE rpiplay)
endif()

find_library(log-lib log)
find_library(android-lib android)
target_link_libraries(airplay_native PRIVATE ${log-lib} ${android-lib} c++_shared)
