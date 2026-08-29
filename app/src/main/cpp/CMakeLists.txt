cmake_minimum_required(VERSION 3.22.1)
project(modem73_android LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# --- modem73 core -------------------------------------------
# Passed from app/build.gradle.kts as -DMODEM73_ROOT=... (from local.properties
# `modem73.core.dir`), else the third_party/modem73 submodule/symlink.
if(NOT DEFINED MODEM73_ROOT OR MODEM73_ROOT STREQUAL "")
    set(MODEM73_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/modem73")
endif()
get_filename_component(MODEM73_ROOT "${MODEM73_ROOT}" ABSOLUTE)
if(NOT EXISTS "${MODEM73_ROOT}/modem.hh")
    message(FATAL_ERROR
        "modem73 core not found at '${MODEM73_ROOT}' (no modem.hh). "
        "Set modem73.core.dir in local.properties or populate third_party/modem73.")
endif()
message(STATUS "modem73 core: ${MODEM73_ROOT}")

# --- version string ---------------------------------------------------------
find_package(Git QUIET)
set(MODEM73_VERSION "")
if(GIT_FOUND)
    execute_process(
        COMMAND ${GIT_EXECUTABLE} -C "${MODEM73_ROOT}" describe --tags --exact-match
        OUTPUT_VARIABLE _m73_tag OUTPUT_STRIP_TRAILING_WHITESPACE
        ERROR_QUIET RESULT_VARIABLE _m73_rc)
    if(_m73_rc EQUAL 0 AND NOT _m73_tag STREQUAL "")
        string(REGEX REPLACE "^v" "" MODEM73_VERSION "${_m73_tag}")
    endif()
endif()
if(MODEM73_VERSION STREQUAL "" AND EXISTS "${MODEM73_ROOT}/Makefile")
    file(STRINGS "${MODEM73_ROOT}/Makefile" _m73_base REGEX "^BASE_VERSION[ \t]*:?=")
    if(_m73_base)
        string(REGEX REPLACE "^BASE_VERSION[ \t]*:?=[ \t]*" "" MODEM73_VERSION "${_m73_base}")
        string(STRIP "${MODEM73_VERSION}" MODEM73_VERSION)
    endif()
endif()
if(MODEM73_VERSION STREQUAL "" AND GIT_FOUND)
    execute_process(
        COMMAND ${GIT_EXECUTABLE} -C "${MODEM73_ROOT}" describe --tags --always --dirty
        OUTPUT_VARIABLE _m73_desc OUTPUT_STRIP_TRAILING_WHITESPACE
        ERROR_QUIET RESULT_VARIABLE _m73_rc)
    if(_m73_rc EQUAL 0 AND NOT _m73_desc STREQUAL "")
        string(REGEX REPLACE "^v" "" MODEM73_VERSION "${_m73_desc}")
    endif()
endif()
if(MODEM73_VERSION STREQUAL "")
    set(MODEM73_VERSION "dev")
endif()
message(STATUS "modem73 version: ${MODEM73_VERSION}")
foreach(_m73_ref HEAD packed-refs)
    if(EXISTS "${MODEM73_ROOT}/.git/${_m73_ref}")
        set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${MODEM73_ROOT}/.git/${_m73_ref}")
    endif()
endforeach()
if(EXISTS "${MODEM73_ROOT}/Makefile")
    set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${MODEM73_ROOT}/Makefile")
endif()

# --- the native library -----------------------------------------------------
add_library(modem73 SHARED
    modem73_jni.cc
    engine.cc
    ${MODEM73_ROOT}/deps/miniaudio.c
    ${MODEM73_ROOT}/deps/cJSON.c
)

target_include_directories(modem73 PRIVATE
    ${MODEM73_ROOT}
    ${MODEM73_ROOT}/phy
    ${MODEM73_ROOT}/deps
    ${MODEM73_ROOT}/deps/aicodix/dsp
    ${MODEM73_ROOT}/deps/aicodix/code
    ${MODEM73_ROOT}/deps/aicodix/modem
)

target_compile_definitions(modem73 PRIVATE
    MODEM73_VERSION="${MODEM73_VERSION}"
    WITH_UI=1
)

set_source_files_properties(${MODEM73_ROOT}/deps/miniaudio.c ${MODEM73_ROOT}/deps/cJSON.c PROPERTIES COMPILE_OPTIONS "-O2;-w")

# Same optimisation level as the upstream Makefile (-O3), no -march=native:
# the NDK toolchain targets the ABI baseline (armv8-a+NEON, x86-64+SSE4.2).
target_compile_options(modem73 PRIVATE
    -O3 -Wall -Wextra -fvisibility=hidden
    -Wno-unused-parameter -Wno-unused-variable -Wno-sign-compare
)

find_library(log-lib log)
find_library(android-lib android)
set(HAMLIB_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/hamlib)
set(HAMLIB_A ${HAMLIB_ROOT}/${ANDROID_ABI}/lib/libhamlib.a)
if(EXISTS ${HAMLIB_A})
    file(WRITE ${CMAKE_CURRENT_BINARY_DIR}/hamlib_stub.c "int modem73_hamlib_stub(void) { return 0; }\n")
    add_library(hamlib SHARED ${CMAKE_CURRENT_BINARY_DIR}/hamlib_stub.c)
    target_link_options(hamlib PRIVATE -Wl,--whole-archive ${HAMLIB_A} -Wl,--no-whole-archive)
    target_link_libraries(hamlib PRIVATE ${log-lib})
    target_include_directories(modem73 PRIVATE ${HAMLIB_ROOT}/include)
    target_compile_definitions(modem73 PRIVATE WITH_HAMLIB=1)
    target_sources(modem73 PRIVATE ${MODEM73_ROOT}/hamlib_ptt.cc)
    target_link_libraries(modem73 PRIVATE hamlib)
endif()
target_link_libraries(modem73 PRIVATE ${log-lib} ${android-lib})
