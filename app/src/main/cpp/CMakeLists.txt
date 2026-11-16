cmake_minimum_required(VERSION 3.22.1)

project("clothsim")

add_library(clothsim SHARED
    cloth.cpp
    jni_bridge.cpp)

find_library(log-lib log)

target_link_libraries(clothsim ${log-lib})
