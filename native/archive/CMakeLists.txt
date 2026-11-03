# libcomictv_archive.so: libarchive's RAR/RAR5 readers behind a small JNI layer (see comictv_archive.c).
# Built by build.ps1 for armeabi-v7a and arm64-v8a; the results are committed to app/src/main/jniLibs.
cmake_minimum_required(VERSION 3.18)
project(comictv_archive C)

if(NOT LIBARCHIVE_SOURCE)
  message(FATAL_ERROR "Pass -DLIBARCHIVE_SOURCE=<libarchive-3.8.11 source folder>")
endif()

# Only what RAR reading needs: no crypto, XML, external compressors, iconv or command-line tools.
foreach(option ENABLE_OPENSSL ENABLE_MBEDTLS ENABLE_NETTLE ENABLE_LIBB2 ENABLE_LZ4 ENABLE_LZO ENABLE_LZMA ENABLE_ZSTD
    ENABLE_BZip2 ENABLE_LIBXML2 ENABLE_EXPAT ENABLE_WIN32_XMLLITE ENABLE_PCREPOSIX ENABLE_PCRE2POSIX ENABLE_LIBGCC ENABLE_CNG
    ENABLE_TAR ENABLE_CPIO ENABLE_CAT ENABLE_UNZIP ENABLE_XATTR ENABLE_ACL ENABLE_ICONV ENABLE_TEST ENABLE_COVERAGE ENABLE_INSTALL)
  set(${option} OFF CACHE BOOL "" FORCE)
endforeach()
set(ENABLE_ZLIB ON CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
add_subdirectory(${LIBARCHIVE_SOURCE} libarchive EXCLUDE_FROM_ALL)

add_library(comictv_archive SHARED comictv_archive.c)
target_include_directories(comictv_archive PRIVATE ${LIBARCHIVE_SOURCE}/libarchive)
target_link_libraries(comictv_archive PRIVATE archive_static z)
target_compile_options(comictv_archive PRIVATE -O2 -Wall -fvisibility=hidden)
# 16 KB pages (Android 15+) and no unused code from the static library.
target_link_options(comictv_archive PRIVATE -Wl,--gc-sections -Wl,-z,max-page-size=16384 -Wl,--exclude-libs,ALL)
