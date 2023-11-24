include_directories(
   .
)

set(example_SRCS
   scheduler_example1.c
)

configure_file(../models/model.cfg model.cfg COPYONLY)

add_executable(scheduler_example1
  ${example_SRCS}
)

target_link_libraries(scheduler_example1
    der_scheduler
    cjson
    m
)