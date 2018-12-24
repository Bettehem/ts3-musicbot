TS3 MusicBot let's you control your music from a TeamSpeak servers channel via chat.


Requirements:
- Linux based os in a virtual machine or spare computer



Installation:
- Download sp from https://gist.github.com/wandernauta/6800547 and copy it to /bin/sh and make it executable
- Install youtube-dl and mplayer for youtube support
- Install spotify and log in
- Install TeamSpeak client and log in to desired server and enter channel where you want the bot to be
- Install PulseAudio Volume Control (pavucontrol) so you can route audio from your music app to TeamSpeak
- Install jre8-openjdk or equivalent



Use:
1. Install IntelliJ idea to build the bot from source
2. Open the app with java -jar ts3-musicbot.jar
3. Enter or Browse the path of your teamspeak channel's channel.html file
4. Press Ready.


Commands:
- All commands start with "%" character. You have to enter these in the chat of the channel your bot is connected to.

%sp-pause                   -Pauses the Spotify playback
%sp-resume                  -Resumes the Spotify playback
%sp-play                    -Resumes the Spotify playback
%sp-skip                    -Skips the currently playing track
%sp-next                    -Skips the currently playing track
%sp-prev                    -Plays the previous track
%sp-playsong <track>        -Plays a Spotify song. <track> should be your song link or Spotify URI
%sp-playlist <playlist>     -Plays a Spotify playlist. <playlist> should be your playlist's Spotify URI. Links aren't currently supported


