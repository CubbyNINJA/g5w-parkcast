# libparkcast.so for the S3 AirPlay receiver (docs/design/S3_RECEIVER.md, "Native build").
#
# Builds, in order: OpenSSL (static libcrypto + libssl, through its own Configure), libplist (static, from a
# source list), UxPlay lib/ (static, with llhttp, playfair and the dns_sd dlopen backend; never mdnsd/), and
# libparkcast.so linked against all three with hidden symbols.
#
# Inputs come from native prep (tools/airplay_s3/native_prep.py), which fetches, verifies and patches them
# into .cache/src/. Run it first; this file never downloads anything.
#
# Direct run (Gradle's externalNativeBuild passes the same values):
#   cmake -G Ninja -DCMAKE_TOOLCHAIN_FILE=<ndk>/build/cmake/android.toolchain.cmake
#         -DANDROID_ABI=armeabi-v7a -DANDROID_PLATFORM=android-19 -S receiver/native -B <build dir>
cmake_minimum_required(VERSION 3.22)
project(parkcast C)

include(ExternalProject)

# --- Toolchain pins (AIR-043): no fallback NDK, ABI or API level -------------------------------------------
set(PARKCAST_NDK_REVISION "25.2.9519653")
if(NOT ANDROID_NDK_REVISION STREQUAL PARKCAST_NDK_REVISION)
  message(FATAL_ERROR "libparkcast needs NDK ${PARKCAST_NDK_REVISION} (r25c); found NDK revision "
                      "'${ANDROID_NDK_REVISION}' at '${ANDROID_NDK}'")
endif()
if(NOT ANDROID_ABI STREQUAL "armeabi-v7a")
  message(FATAL_ERROR "libparkcast builds only for armeabi-v7a; ANDROID_ABI is '${ANDROID_ABI}'")
endif()
if(NOT ANDROID_PLATFORM_LEVEL EQUAL 19)
  message(FATAL_ERROR "libparkcast builds only for API 19; ANDROID_PLATFORM_LEVEL is '${ANDROID_PLATFORM_LEVEL}'")
endif()

set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# --- Prepared sources --------------------------------------------------------------------------------------
set(PARKCAST_CACHE "${CMAKE_CURRENT_SOURCE_DIR}/.cache")
set(PARKCAST_SRC "${PARKCAST_CACHE}/src")
if(NOT EXISTS "${PARKCAST_CACHE}/prep.stamp")
  message(FATAL_ERROR "native prep has not succeeded (${PARKCAST_CACHE}/prep.stamp is missing); run "
                      "python -m airplay_s3.native_prep first")
endif()
set(OPENSSL_SRC "${PARKCAST_SRC}/openssl")
set(PLIST_SRC "${PARKCAST_SRC}/libplist")
set(UXPLAY_SRC "${PARKCAST_SRC}/uxplay")
set(PLIST_VERSION "2.8.1")

# --- OpenSSL 3.5 (static) ----------------------------------------------------------------------------------
# Configure needs a full Perl; Git Bash's lacks the modules (OD-189). Native prep checks this one.
set(parkcast.perl "C:/msys64/usr/bin/perl.exe" CACHE FILEPATH "Perl that runs OpenSSL's Configure")
file(TO_CMAKE_PATH "${parkcast.perl}" PARKCAST_PERL)
if(NOT EXISTS "${PARKCAST_PERL}")
  message(FATAL_ERROR "Perl for OpenSSL's Configure not found at '${PARKCAST_PERL}'; set -Dparkcast.perl=<perl>")
endif()
get_filename_component(PARKCAST_PERL_DIR "${PARKCAST_PERL}" DIRECTORY)

set(NDK_LLVM_BIN "${ANDROID_TOOLCHAIN_ROOT}/bin")
if(CMAKE_HOST_WIN32)
  # MSYS2 Perl reads ANDROID_NDK_ROOT as a POSIX path (/c/...), and finds clang and llvm-ar on PATH.
  # (Split by hand: string(REGEX REPLACE) re-applies a ^ anchor after each match.)
  file(TO_CMAKE_PATH "${ANDROID_NDK}" _ndk)
  if(NOT _ndk MATCHES "^[A-Za-z]:/")
    message(FATAL_ERROR "ANDROID_NDK '${_ndk}' is not a drive path")
  endif()
  string(SUBSTRING "${_ndk}" 0 1 _drive)
  string(TOLOWER "${_drive}" _drive)
  string(SUBSTRING "${_ndk}" 2 -1 _rest)
  set(OSSL_NDK_ROOT "/${_drive}${_rest}")
  file(TO_CMAKE_PATH "$ENV{SystemRoot}/System32" _sys32)
  set(OSSL_PATH "${NDK_LLVM_BIN}|${PARKCAST_PERL_DIR}|${_sys32}")
  set(OSSL_MAKE "${ANDROID_NDK}/prebuilt/windows-x86_64/bin/make.exe")
else()
  set(OSSL_NDK_ROOT "${ANDROID_NDK}")
  set(OSSL_PATH "${NDK_LLVM_BIN}:${PARKCAST_PERL_DIR}:/usr/bin:/bin")
  find_program(OSSL_MAKE make REQUIRED)
endif()
cmake_host_system_information(RESULT _ncpu QUERY NUMBER_OF_LOGICAL_CORES)

set(OSSL_DIR "${CMAKE_CURRENT_BINARY_DIR}/openssl")
set(OSSL_ENV ${CMAKE_COMMAND} -E env "ANDROID_NDK_ROOT=${OSSL_NDK_ROOT}" "PATH=${OSSL_PATH}")
ExternalProject_Add(openssl_build
  PREFIX "${CMAKE_CURRENT_BINARY_DIR}/openssl-ep"
  SOURCE_DIR "${OSSL_DIR}"
  # Build in a copy, so the prepared tree in .cache stays clean.
  DOWNLOAD_COMMAND ${CMAKE_COMMAND} -E copy_directory "${OPENSSL_SRC}" "${OSSL_DIR}"
  LIST_SEPARATOR |
  # no-autoload-config and a /system OPENSSLDIR: nothing is ever read from a writable config path.
  CONFIGURE_COMMAND ${OSSL_ENV} "${PARKCAST_PERL}" Configure android-arm
                    no-shared no-tests no-apps no-docs no-autoload-config no-engine no-dso
                    --openssldir=/system/etc/parkcast-ssl
                    -Wa,--noexecstack -Qunused-arguments -D__ANDROID_API__=19
  BUILD_COMMAND ${OSSL_ENV} "${OSSL_MAKE}" -j${_ncpu} "PERL=${PARKCAST_PERL}" build_libs
  INSTALL_COMMAND ""
  BUILD_IN_SOURCE TRUE
  BUILD_BYPRODUCTS "${OSSL_DIR}/libcrypto.a" "${OSSL_DIR}/libssl.a"
  LOG_CONFIGURE TRUE
  LOG_BUILD TRUE
  LOG_OUTPUT_ON_FAILURE TRUE)

file(MAKE_DIRECTORY "${OSSL_DIR}/include")
add_library(parkcast_crypto STATIC IMPORTED GLOBAL)
set_target_properties(parkcast_crypto PROPERTIES
  IMPORTED_LOCATION "${OSSL_DIR}/libcrypto.a"
  INTERFACE_INCLUDE_DIRECTORIES "${OSSL_DIR}/include")
add_dependencies(parkcast_crypto openssl_build)
add_library(parkcast_ssl STATIC IMPORTED GLOBAL)
set_target_properties(parkcast_ssl PROPERTIES
  IMPORTED_LOCATION "${OSSL_DIR}/libssl.a"
  INTERFACE_INCLUDE_DIRECTORIES "${OSSL_DIR}/include"
  INTERFACE_LINK_LIBRARIES parkcast_crypto)
add_dependencies(parkcast_ssl openssl_build)

# --- libplist 2.x (static, no autotools) -------------------------------------------------------------------
# config.h values checked by hand against the r25c bionic headers for API 19 (CROSSBUILD spike).
file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/plist/config.h"
"#define HAVE_STDINT_H 1
#define HAVE_STDLIB_H 1
#define HAVE_STRING_H 1
#define HAVE_INTTYPES_H 1
#define HAVE_STRDUP 1
#define HAVE_STRNDUP 1
#define HAVE_STRERROR 1
#define HAVE_GMTIME_R 1
#define HAVE_LOCALTIME_R 1
#define HAVE_TIMEGM 1
#define HAVE_STRPTIME 1
#define HAVE_MEMMEM 1
#define HAVE_TM_TM_GMTOFF 1
#define HAVE_TM_TM_ZONE 1
#define HAVE_FVISIBILITY 1
#define __LITTLE_ENDIAN__ 1
#define PACKAGE_VERSION \"${PLIST_VERSION}\"
#define PACKAGE_STRING \"libplist ${PLIST_VERSION}\"
")
set(_ps "${PLIST_SRC}/src")
add_library(parkcast_plist STATIC
  ${_ps}/base64.c ${_ps}/bytearray.c ${_ps}/hashtable.c ${_ps}/ptrarray.c ${_ps}/time64.c ${_ps}/common.c
  ${_ps}/xplist.c ${_ps}/bplist.c ${_ps}/jsmn.c ${_ps}/jplist.c ${_ps}/oplist.c ${_ps}/out-default.c
  ${_ps}/out-plutil.c ${_ps}/out-limd.c ${_ps}/plist.c
  ${PLIST_SRC}/libcnary/node.c ${PLIST_SRC}/libcnary/node_list.c)
target_include_directories(parkcast_plist
  PRIVATE "${CMAKE_CURRENT_BINARY_DIR}/plist" "${PLIST_SRC}" "${_ps}" "${PLIST_SRC}/libcnary/include"
  PUBLIC "${PLIST_SRC}/include")
target_compile_definitions(parkcast_plist PRIVATE HAVE_CONFIG_H PUBLIC LIBPLIST_STATIC)
target_compile_options(parkcast_plist PRIVATE
  -Wall -Wextra -Wno-unused-parameter -Wno-strict-aliasing -fvisibility=hidden -fPIC)

# --- UxPlay lib/ (static; AIR-042(b)) ----------------------------------------------------------------------
# lib/ only, with llhttp, playfair and dns_sd/dns_sd.c (the dlopen backend, patch 0001). Never mdnsd/: no
# responder is linked (AIR-044(c)); libmdnssd.so is only ever dlopen'ed by name.
set(_ux "${UXPLAY_SRC}/lib")
file(GLOB UXPLAY_LIB_SOURCES CONFIGURE_DEPENDS
  "${_ux}/*.c" "${_ux}/llhttp/*.c" "${_ux}/playfair/*.c")
list(APPEND UXPLAY_LIB_SOURCES "${_ux}/dns_sd/dns_sd.c")
add_library(parkcast_uxplay STATIC ${UXPLAY_LIB_SOURCES})
target_include_directories(parkcast_uxplay
  PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/dlstub-include" "${_ux}/playfair" "${_ux}/llhttp"
  PUBLIC "${_ux}")
target_compile_definitions(parkcast_uxplay PRIVATE
  STANDALONE __STDC_CONSTANT_MACROS __STDC_LIMIT_MACROS TARGET_POSIX _LINUX PIC _REENTRANT
  _LARGEFILE64_SOURCE _FILE_OFFSET_BITS=64
  NOHOLD FULL_RANGE_RGB_FIX PLIST_210 PLIST_230 OPENSSL_API_COMPAT=0x10101000L
  EXTERNAL_DNS_SD HAVE_LIBDL "DNSSD_LIBNAME=\"libmdnssd.so\"")
target_compile_options(parkcast_uxplay PRIVATE -O2 -fPIC -Wall -fvisibility=hidden)
target_link_libraries(parkcast_uxplay PUBLIC parkcast_ssl parkcast_crypto parkcast_plist)

# --- libparkcast.so ----------------------------------------------------------------------------------------
set(PARKCAST_SOURCES
  "${CMAKE_CURRENT_SOURCE_DIR}/src/parkcast_jni.c"
  "${CMAKE_CURRENT_SOURCE_DIR}/src/host.c"
  "${CMAKE_CURRENT_SOURCE_DIR}/src/ring.c")
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/src/host.c")
  add_library(parkcast SHARED ${PARKCAST_SOURCES})
  target_include_directories(parkcast PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/src")
  target_compile_options(parkcast PRIVATE -O2 -Wall -Wextra -Wno-unused-parameter -fvisibility=hidden)
  target_link_libraries(parkcast PRIVATE parkcast_uxplay parkcast_ssl parkcast_crypto parkcast_plist dl)
  # API 19's loader (Open on-unit risks, "Loader"): no packed relocations, no RELR, no DT_RUNPATH, and every
  # symbol resolved at link time. --exclude-libs keeps the static deps' symbols out of the dynamic table.
  target_link_options(parkcast PRIVATE
    -Wl,--no-undefined -Wl,--exclude-libs,ALL -Wl,--pack-dyn-relocs=none -Wl,--build-id=sha1
    -Wl,-z,noexecstack -Wl,-z,relro -Wl,-z,now -Wl,--hash-style=both)
  set_target_properties(parkcast PROPERTIES SKIP_BUILD_RPATH TRUE BUILD_WITH_INSTALL_RPATH FALSE)
endif()
