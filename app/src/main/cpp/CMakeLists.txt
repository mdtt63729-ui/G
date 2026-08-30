cmake_minimum_required(VERSION 3.22.1)
project("gitofy-native-git")

set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_CLI OFF CACHE BOOL "" FORCE)
set(BUILD_FUZZERS OFF CACHE BOOL "" FORCE)
set(USE_SSH OFF CACHE BOOL "" FORCE)
set(USE_HTTPS mbedTLS CACHE STRING "" FORCE)
set(USE_HTTP_PARSER builtin CACHE STRING "" FORCE)
set(USE_BUNDLED_ZLIB ON CACHE STRING "" FORCE)
set(ENABLE_REPRODUCIBLE_BUILDS ON CACHE BOOL "" FORCE)
set(REGEX_BACKEND builtin CACHE STRING "" FORCE)

# We never install() libgit2 or mbedTLS — we only link against them.
# Suppressing install export rules avoids the CMake error:
#   install(EXPORT "libgit2Targets") includes target "mbedtls" that is not in any export set.
set(CMAKE_SKIP_INSTALL_RULES ON CACHE BOOL "" FORCE)
set(CMAKE_SKIP_INSTALL_ALL_EXPORT TRUE CACHE BOOL "" FORCE)
# Also nullify install() so subprojects' install(EXPORT) calls become no-ops.
macro(install)
    # Deliberately empty: we never install anything in an Android NDK build.
endmacro()

include(FetchContent)

# libgit2 v1.9.7 is the current patched v1.9 release. Do not downgrade:
# libgit2 <=1.9.4 has multiple 2026 security advisories.
FetchContent_Declare(
    libgit2
    GIT_REPOSITORY https://github.com/libgit2/libgit2.git
    GIT_TAG v1.9.7
    GIT_SHALLOW TRUE
)

# mbedTLS supplies HTTPS/TLS without depending on Android's private BoringSSL ABI.
FetchContent_Declare(
    mbedtls
    GIT_REPOSITORY https://github.com/Mbed-TLS/mbedtls.git
    GIT_TAG v3.6.7
    GIT_SHALLOW TRUE
    GIT_SUBMODULES_RECURSE TRUE
)

set(ENABLE_TESTING OFF CACHE BOOL "" FORCE)
set(USE_STATIC_MBEDTLS_LIBRARY ON CACHE BOOL "" FORCE)
set(USE_SHARED_MBEDTLS_LIBRARY OFF CACHE BOOL "" FORCE)
set(ENABLE_PROGRAMS OFF CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(mbedtls)

# libgit2 ships FindmbedTLS.cmake and searches a conventional include/library
# layout. Point it at the already-built FetchContent tree rather than relying
# on a host-installed TLS library.
set(MBEDTLS_ROOT_DIR "${mbedtls_BINARY_DIR}" CACHE PATH "" FORCE)
set(MBEDTLS_INCLUDE_DIR "${mbedtls_SOURCE_DIR}/include" CACHE PATH "" FORCE)
set(MBEDTLS_USE_TARGETS ON CACHE BOOL "Use the FetchContent mbedTLS targets" FORCE)
list(PREPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")

FetchContent_MakeAvailable(libgit2)

add_library(native-git-bridge SHARED native_git_bridge.cpp)

target_compile_features(native-git-bridge PRIVATE cxx_std_17)
target_compile_options(native-git-bridge PRIVATE -Wall -Wextra -Werror=return-type)

target_include_directories(native-git-bridge PRIVATE
    "${libgit2_SOURCE_DIR}/include"
)

target_link_libraries(native-git-bridge
    PRIVATE
    libgit2package
    util
    llhttp
    log
)

# Android's libc supplies pthread/dl functionality used by libgit2.
if (ANDROID)
    target_link_libraries(native-git-bridge PRIVATE android)
endif()
