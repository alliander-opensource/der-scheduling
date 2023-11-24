cmake_minimum_required(VERSION 3.13)

# Set the plugin name to build
project(der_scheduler)

include(FetchContent)

list(APPEND CMAKE_MODULE_PATH "${PROJECT_SOURCE_DIR}/cmake")

message(STATUS ${CMAKE_CXX_FLAGS})

# Generation version header file
set_source_files_properties(version.h PROPERTIES GENERATED TRUE)
add_custom_command(
  OUTPUT version.h
  DEPENDS ${CMAKE_SOURCE_DIR}/VERSION
  COMMAND ${CMAKE_SOURCE_DIR}/mkversion ${CMAKE_SOURCE_DIR}
  COMMENT "Generating version header"
  VERBATIM
)
include_directories(${CMAKE_BINARY_DIR})

FetchContent_Declare(
  cjson
  GIT_REPOSITORY "https://github.com/DaveGamble/cJSON.git"
  GIT_TAG "master"
)

SET(ENABLE_CJSON_TEST FALSE CACHE BOOL "" FORCE)
SET(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(cjson)

include_directories(${cjson_SOURCE_DIR})
include_directories(${cjson_BINARY_DIR})

# Find source files
#file(GLOB SOURCES *.cpp)
file(GLOB SOURCES ${CMAKE_CURRENT_LIST_DIR}/src/scheduler/*.c)
file(GLOB STORAGE_SOURCES ${CMAKE_CURRENT_LIST_DIR}/src/scheduler/storage/*.c)

message(STATUS ${SOURCES} ${STORAGE_SOURCES})

# Add ./include
include_directories(${CMAKE_CURRENT_LIST_DIR}/src/scheduler)

# Create shared library
add_library(der_scheduler STATIC ${SOURCES} ${STORAGE_SOURCES} version.h)

set_target_properties(der_scheduler PROPERTIES LINKER_LANGUAGE C)

target_link_libraries(der_scheduler -L/usr/local/lib -liec61850)

# Add additional libraries
target_link_libraries(der_scheduler -lpthread -ldl)

# Set the build version 
set_target_properties(der_scheduler PROPERTIES SOVERSION 1)

add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/examples)

add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/tests)
