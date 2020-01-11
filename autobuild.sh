#!/bin/sh

###
#This script automatically tries to build the project when changes are detected,
#and attempts to push the .jar file to the desired machine if build succeeds.
#You already need to have your ssh keys set up with the remote machine for the scp command to work as expected.


#First set the BUILD_DIR and HOST_ADDR variables, for example:
#export BUILD_DIR=out/artifacts/ts3_musicbot
#export HOST_ADDR=user@cooladdress.com:~/cooldirectory/.

###




#Start monitoring current dir
while true; do
    inotifywait -e modify,create,delete,move -r $(pwd) && \
        if ./build.sh; then
            echo "Building succesful. Pushing file to remote..."
            scp $BUILD_DIR $HOST_ADDR
        else
            echo "Building failed."
        fi
done
