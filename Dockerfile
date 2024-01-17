FROM debian:12.4-slim

RUN apt-get update && \
    apt-get install make cmake build-essential git -y

# install libiec61850 library that is required for DER scheduler
RUN git clone https://github.com/mz-automation/libiec61850.git /var/lib/libiec61850 && \
    mkdir /var/lib/libiec61850/build && \
    cd /var/lib/libiec61850/build && \
    git checkout v1.5 && \
    cmake .. && \
    make && \
    make install && \
    ldconfig

# install DER scheduler
## copy local sources into docker
COPY CMakeLists.txt VERSION mkversion /opt/der-scheduler/
COPY src/ /opt/der-scheduler/src
COPY examples/ /opt/der-scheduler/examples
COPY tests/ /opt/der-scheduler/tests
COPY models/ /opt/der-scheduler/models

## build and install DER scheduler
RUN mkdir -p /opt/der-scheduler/build && \
    cd /opt/der-scheduler/build  && \
    cmake .. && \
    make

## create a dummy output skript that will only display new outputs as the console
RUN echo 'echo "$(date) got new output: $@"' > /opt/der-scheduler/build/examples/set_output.sh && \
    chmod +x /opt/der-scheduler/build/examples/set_output.sh

# clean up
RUN apt-get autoremove && \
    apt-get autoclean && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*


# start skript
WORKDIR /opt/der-scheduler/build/examples/
ENTRYPOINT ["/opt/der-scheduler/build/examples/scheduler_example1"]

# IEC 61850 server running on port 102
EXPOSE 102

