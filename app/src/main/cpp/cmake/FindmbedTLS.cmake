# Project-local finder used when libgit2 is configured as a FetchContent child.
# The mbedTLS targets already exist because FetchContent_MakeAvailable(mbedtls)
# ran before libgit2 is configured. Avoid filesystem find_library() calls that
# cannot see libraries which have not been built yet.

if(TARGET mbedtls AND TARGET mbedx509 AND TARGET mbedcrypto)
    set(MBEDTLS_FOUND TRUE)
    set(MBEDTLS_INCLUDE_DIR "${mbedtls_SOURCE_DIR}/include")
    set(MBEDTLS_LIBRARY mbedtls)
    set(MBEDX509_LIBRARY mbedx509)
    set(MBEDCRYPTO_LIBRARY mbedcrypto)
    set(MBEDTLS_LIBRARIES mbedtls mbedx509 mbedcrypto)
else()
    set(MBEDTLS_FOUND FALSE)
endif()
