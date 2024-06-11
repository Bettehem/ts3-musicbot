===============================================================================
                         The TS3 ClientQuery Plugin
===============================================================================

The TeamSpeak 3 Client Query Plugin is a plugin for the TS3 client that is
enabled by default and that binds to TCP Port 25639 on the local interface and
allows 3rd party applications to query information from the TS3 client and to
send commands to the TS3 client.

A list of all available commands can be retrieved after connecting (e.g. by
running "telnet 127.0.0.1 25639" by typing "help". Detailed description for
each command is available too, type "help <command>"

To use the clientquery telnet interface, an application needs to authenticate
itself using the command "auth apikey=<apikey>" telnet command.
The API key is specific per user and automatically created when the TeamSpeak
client starts. The individual API key is displayed in the clientquery settings
dialog. The application making the connection is responsible to prompt the user
for his API key.
