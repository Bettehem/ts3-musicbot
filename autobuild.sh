#!/bin/sh

###
#This script automatically tries to build the project when changes are detected,
#and attempts to push the .jar file to the desired machine if build succeeds.
#You already need to have your ssh keys set up with the remote machine for the scp command to work as expected.


#First set the BUILD_DIR and HOST_ADDR variables, for example:
#export JAR_FILE=out/artifacts/ts3_musicboti/ts3-musicbot.jar
#export HOST_ADDR=user@cooladdress.com:~/cooldirectory/.

###




#Start monitoring current dir
while true; do
    inotifywait -q -e modify,create,delete,move -r $(pwd)
        if ./build.sh; then
            echo "Building succesful. Pushing file to remote..."
            scp $JAR_FILE $HOST_ADDR
        else
            echo "Building failed."
        fi
done
