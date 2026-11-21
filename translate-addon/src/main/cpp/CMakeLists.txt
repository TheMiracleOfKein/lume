# Native engine of the Lume Translate add-on: bergamot-translator (MPL-2.0, git submodule under
# third_party/) plus a small JNI bridge, built into liblumetranslate.so.
cmake_minimum_required(VERSION 3.18)
project(lumetranslate CXX C)

set(BERGAMOT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../third_party/bergamot-translator")
set(PATCH_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../patches")
if(NOT EXISTS "${BERGAMOT_DIR}/CMakeLists.txt")
  message(FATAL_ERROR "bergamot-translator is missing: run git submodule update --init --recursive")
endif()

# Small Android build fixes (API 26 libc, Windows hosts), applied once to the submodule checkout.
find_package(Git REQUIRED)
function(apply_patch repo patch)
  # --ignore-whitespace: a Windows checkout with core.autocrlf has CRLF sources; the patches are LF.
  execute_process(COMMAND ${GIT_EXECUTABLE} apply --check --reverse --ignore-whitespace ${patch}
                  WORKING_DIRECTORY ${repo} RESULT_VARIABLE already OUTPUT_QUIET ERROR_QUIET)
  if(NOT already EQUAL 0)
    execute_process(COMMAND ${GIT_EXECUTABLE} apply --ignore-whitespace --whitespace=nowarn ${patch}
                    WORKING_DIRECTORY ${repo} RESULT_VARIABLE failed)
    if(failed)
      message(FATAL_ERROR "Could not apply ${patch}")
    endif()
  endif()
endfunction()
apply_patch("${BERGAMOT_DIR}/3rd_party/marian-dev" "${PATCH_DIR}/marian-android.patch")
apply_patch("${BERGAMOT_DIR}/3rd_party/ssplit-cpp" "${PATCH_DIR}/ssplit-android.patch")

# An unoptimised engine is ~20x slower: debug APKs get the release engine too (their Kotlin side
# stays debuggable). marian picks its flags from this variable.
set(CMAKE_BUILD_TYPE Release)

set(USE_MKL OFF CACHE BOOL "" FORCE)
set(COMPILE_CUDA OFF CACHE BOOL "" FORCE)
set(USE_DOXYGEN OFF CACHE BOOL "" FORCE)
set(COMPILE_TESTS OFF CACHE BOOL "" FORCE)
set(COMPILE_UNIT_TESTS OFF CACHE BOOL "" FORCE)
set(SSPLIT_USE_INTERNAL_PCRE2 ON CACHE BOOL "" FORCE)
set(USE_STATIC_LIBS ON CACHE BOOL "" FORCE)
set(GIT_SUBMODULE OFF CACHE BOOL "" FORCE)
set(USE_NCCL OFF CACHE BOOL "" FORCE)
if(ANDROID_ABI STREQUAL "arm64-v8a")
  # marian picks ruy for int8 and float matrix products on ARM.
  set(BUILD_ARCH armv8-a CACHE STRING "" FORCE)
elseif(ANDROID_ABI STREQUAL "x86_64")
  # The emulator and x86 Chromebooks: intgemm for int8, ruy for float (no BLAS on Android).
  set(BUILD_ARCH x86-64-v2 CACHE STRING "" FORCE)
  set(USE_INTGEMM ON CACHE BOOL "" FORCE)
  set(USE_RUY ON CACHE BOOL "" FORCE)
  set(USE_RUY_SGEMM ON CACHE BOOL "" FORCE)
else()
  message(FATAL_ERROR "Unsupported ABI ${ANDROID_ABI}")
endif()

add_subdirectory(${BERGAMOT_DIR} bergamot EXCLUDE_FROM_ALL)

add_library(lumetranslate SHARED bergamot_jni.cpp)
target_link_libraries(lumetranslate PRIVATE bergamot-translator log)
target_link_options(lumetranslate PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)
set_target_properties(lumetranslate PROPERTIES CXX_VISIBILITY_PRESET hidden)
