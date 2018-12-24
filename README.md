TS3 MusicBot let's you control your music from a TeamSpeak servers channel via chat.


<h4>Requirements:</h4>
- Linux based os in a virtual machine or spare computer  



<h4>Installation:</h4>
- Download sp from https://gist.github.com/wandernauta/6800547 and copy it to /bin/sh and make it executable<br>    
- Install youtube-dl and mpv for youtube support<br>
- Install spotify and log in<br>
- Install TeamSpeak client and log in to desired server and enter channel where you want the bot to be, and then click the text box at the bottom where it says "Enter Chat Message..."<br>
- Install PulseAudio Volume Control (pavucontrol) so you can route audio from your music app to TeamSpeak<br> 
- Install jre8-openjdk and java-openjfx<br>
- Install xdotool so the bot can interact with TeamSpeak's chat


<h4>Use:</h4>
1. You have two ways of getting the bot:<br>
    *  a) Install IntelliJ idea to build the bot from source<br>
    *  b) Download latest build from https://gitlab.com/Bettehem/ts3-musicbot/tags<br>
2. Open the app with java -jar ts3-musicbot.jar<br>
3. Enter or Browse the path of your teamspeak channel's channel.html file<br>
4. Press Ready.<br>
5. In PulseAudio Volume Control, go to Recording tab and select TeamSpeak's audio source to be your default speakers.<br>
6. Make sure you click the chat text box and you should be good to go<br> 

<h4>Commands:</h4>
- All commands start with the "%" character. You have to enter these in the chat of the channel your bot is connected to.<br>
- Check Command List wiki page for list of commands: https://gitlab.com/Bettehem/ts3-musicbot/wikis/command-list<br>



