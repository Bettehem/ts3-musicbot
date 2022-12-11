#!/bin/sh

# This script lets you easily test ts3 clientquery commands on your musicbot system.
printf "Enter TeamSpeak ClientQuery API key (defaults to whatever is in ~/.ts3client/clientquery.ini): "
read -r API_KEY
CLIENTQUERY_FILE="$HOME/.ts3client/clientquery.ini"
if [ -z "$API_KEY" ]; then
  while [ ! -f "$CLIENTQUERY_FILE" ]; do
      # shellcheck disable=SC2088
      echo "~/.ts3client/clientquery.ini doesn't exist! Starting TeamSpeak client to generate the file."
    pkill -9 ts3client_linux
    xvfb-run teamspeak3 &
    while [ -z "$(pgrep -l ts3client)" ]; do
      echo "Waiting for TeamSpeak to start"
      sleep 10
    done
  done
  pkill -9 ts3client_linux
fi
API_KEY="$(grep api_key "$CLIENTQUERY_FILE" | cut -d= -f2)"

printf "Enter teamspeak client address (defaults to localhost): "
read -r TS_ADDRESS
if [ -z "$TS_ADDRESS" ]; then
  TS_ADDRESS="localhost"
fi
while true; do
  printf "Enter teamspeak command: "
  read -r TS_COMMAND
  if [ "$TS_COMMAND" != "exit" ] && [ "$TS_COMMAND" != "quit" ]; then
    (echo "auth apikey=$API_KEY"; echo "$TS_COMMAND"; echo quit) | nc "$TS_ADDRESS" 25639; echo
  else
    break
  fi
done
