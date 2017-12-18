# Sets the minimum version of CMake required to build your native library.
# This ensures that a certain set of CMake features is available to
# your build.

cmake_minimum_required(VERSION 3.4.1)

# Specifies a library name, specifies whether the library is STATIC or
# SHARED, and provides relative paths to the source code. You can
# define multiple libraries by adding multiple add_library() commands,
# and CMake builds them for you. When you build your app, Gradle
# automatically packages shared libraries with your APK.

find_library( # Defines the name of the path variable that stores the
              # location of the NDK library.
              log-lib

              # Specifies the name of the NDK library that
              # CMake needs to locate.
              log )

add_library( # Specifies the name of the library.
             native

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             src/main/cpp/src/native.cpp)

add_library(pythonlib SHARED IMPORTED)
set_target_properties(pythonlib PROPERTIES IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/src/main/cpp/lib/libpython3.6m.so.1.0)

include_directories(src/main/cpp/inc/)
include_directories(src/main/cpp/inc/python3.6m)

target_link_libraries( # Specifies the target library.
                       native

                       # Links the log library to the target library.
                       ${log-lib}
                       pythonlib)
