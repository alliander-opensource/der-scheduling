#!/bin/bash

# Transforms a CID file into a CFG file that can be used as a configuration file for libiec61850.

set -e

wget https://github.com/mz-automation/libiec61850/raw/v1.5/tools/model_generator/genconfig.jar -O genconfig.jar --quiet
java -jar genconfig.jar der_scheduler.cid model.cfg

echo "SUCCESS"
