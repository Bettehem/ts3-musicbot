package ts3_musicbot.util

data class CommandList(
    var commandPrefix: String = "%",
    //list of available commands. First is the name of the command, second is the default value.
    var commandList: MutableMap<String, String> =
        mapOf(
            Pair("help", "%help"),
            Pair("queue-add", "%queue-add"),
            Pair("queue-playnext", "%queue-playnext"),
            Pair("queue-playnow", "%queue-playnow"),
            Pair("queue-play", "%queue-play"),
            Pair("queue-list", "%queue-list"),
            Pair("queue-delete", "%queue-delete"),
            Pair("queue-clear", "%queue-clear"),
            Pair("queue-shuffle", "%queue-shuffle"),
            Pair("queue-skip", "%queue-skip"),
            Pair("queue-voteskip", "%queue-voteskip"),
            Pair("queue-move", "%queue-move"),
            Pair("queue-stop", "%queue-stop"),
            Pair("queue-status", "%queue-status"),
            Pair("queue-nowplaying", "%queue-nowplaying"),
            Pair("queue-pause", "%queue-pause"),
            Pair("queue-resume", "%queue-resume"),
            Pair("queue-repeat", "%queue-repeat"),
            Pair("info", "%info"),
            Pair("search", "%search"),
            Pair("goto", "%goto"),
            Pair("return", "%return"),
            Pair("sp-pause", "%sp-pause"),
            Pair("sp-resume", "%sp-resume"),
            Pair("sp-play", "%sp-play"),
            Pair("sp-stop", "%sp-stop"),
            Pair("sp-skip", "%sp-skip"),
            Pair("sp-next", "%sp-next"),
            Pair("sp-prev", "%sp-prev"),
            Pair("sp-playsong", "%sp-playsong"),
            Pair("sp-playlist", "%sp-playlist"),
            Pair("sp-playalbum", "%sp-playalbum"),
            Pair("sp-nowplaying", "%sp-nowplaying"),
            Pair("yt-pause", "%yt-pause"),
            Pair("yt-resume", "%yt-resume"),
            Pair("yt-play", "%yt-play"),
            Pair("yt-stop", "%yt-stop"),
            Pair("yt-playsong", "%yt-playsong"),
            Pair("yt-nowplaying", "%yt-nowplaying"),
            Pair("sc-pause", "%sc-pause"),
            Pair("sc-resume", "%sc-resume"),
            Pair("sc-play", "%sc-play"),
            Pair("sc-stop", "%sc-stop"),
            Pair("sc-playsong", "%sc-playsong"),
            Pair("sc-nowplaying", "%sc-nowplaying"),
        ).toMutableMap()
) {
    var helpMessages = createHelpMessages()

    private fun createHelpMessages(): Map<String?, String> =
        mapOf(
            Pair(
                "help", "\n" +
                        "General commands:\n" +
                        "${commandList["help"]} <command>                              -Shows this help message. Use ${commandList["help"]} <command> to get more help on a specific command.\n" +
                        "${commandList["queue-add"]}                                   -Add track(s) to queue by link or directly searching from yt/sp/sc and adding the first match to the queue.\n" +
                        "${commandList["queue-playnext"]}                              -Add track/playlist/album etc. to the top of the queue. Add multiple links separated by a comma \",\". Shuffle with the -s option\n" +
                        "${commandList["queue-playnow"]}                               -Add track/playlist/album etc. to the top of the queue and start playing it immediately.\n" +
                        "${commandList["queue-play"]}                                  -Play the song queue\n" +
                        "${commandList["queue-list"]} <--all,--limit>                  -Lists current songs in queue. Add the -a/--all option to show all tracks or -l/--limit to set a limit to the amount of tracks.\n" +
                        "${commandList["queue-delete"]} <link(s)/position(s)>          -Delete song(s) from the queue. If you want to delete multiple tracks, just separate them with a comma \",\". Optionally you can just use a position to delete a track.\n" +
                        "${commandList["queue-clear"]}                                 -Clears the song queue\n" +
                        "${commandList["queue-shuffle"]}                               -Shuffles the queue\n" +
                        "${commandList["queue-skip"]}                                  -Skips current song\n" +
                        "${commandList["queue-voteskip"]}                              -Vote to skip the currently playing track. All users currently listening will also have to run ${commandList["queue-voteskip"]} for the track to be skipped.\n" +
                        "${commandList["queue-move"]} <link> -p <pos>                  -Moves a track to a desired position in the queue. <link> should be your song link and <pos> should be the new position of your song.\n" +
                        "${commandList["queue-stop"]}                                  -Stops the queue\n" +
                        "${commandList["queue-status"]}                                -Returns the status of the song queue\n" +
                        "${commandList["queue-nowplaying"]}                            -Returns information on the currently playing track\n" +
                        "${commandList["queue-pause"]}                                 -Pauses playback\n" +
                        "${commandList["queue-resume"]}                                -Resumes playback\n" +
                        "${commandList["queue-repeat"]} <amount>                       -Adds the currently playing song to the top of the queue. <amount> is how many times the song should be queued.\n" +
                        "${commandList["search"]} <service> <type> <text> <limit>      -Search on SoundCloud, Spotify or YouTube. Shows 10 first results by default. <type> can be track, video, playlist or channel. You can set the amount of results with the -l/--limit flag.\n" +
                        "${commandList["info"]} <link/search query>                    -Shows info on the given search query or link(s). <link> can be one or more Spotify, YouTube or SoundCloud links, separated by a comma.\n" +
                        "${commandList["goto"]} <channelpath> -p <channelpassword>     -Move the bot to a different channel.\n" +
                        "${commandList["return"]}                                      -Return the bot back to the original channel.\n" +
                        "\n\n" +
                        "Player specific commands:\n" +
                        "(These aren't normally needed. Using the commands above is recommended instead)\n" +
                        "\n" +
                        "Spotify commands:\n" +
                        "${commandList["sp-pause"]}                    -Pauses the Spotify playback\n" +
                        "${commandList["sp-resume"]}                   -Resumes the Spotify playback\n" +
                        "${commandList["sp-play"]}                     -Resumes the Spotify playback\n" +
                        "${commandList["sp-stop"]}                     -Stops the Spotify playback\n" +
                        "${commandList["sp-skip"]}                     -Skips the currently playing track\n" +
                        "${commandList["sp-next"]}                     -Skips the currently playing track\n" +
                        "${commandList["sp-prev"]}                     -Plays the previous track\n" +
                        "${commandList["sp-playsong"]} <track>         -Plays a Spotify song. <track> should be your song link or Spotify URI\n" +
                        "${commandList["sp-playlist"]} <playlist>      -Plays a Spotify playlist. <playlist> should be your playlist's link or Spotify URI\n" +
                        "${commandList["sp-playalbum"]} <album>        -Plays a Spotify album <album> should be your album's link or Spotify URI\n" +
                        "${commandList["sp-nowplaying"]}               -Shows information on currently playing track\n" +
                        "\n" +
                        "YouTube commands:\n" +
                        "${commandList["yt-pause"]}                    -Pauses the YouTube playback\n" +
                        "${commandList["yt-resume"]}                   -Resumes the YouTube playback\n" +
                        "${commandList["yt-play"]}                     -Resumes the YouTube playback\n" +
                        "${commandList["yt-stop"]}                     -Stops the YouTube playback\n" +
                        "${commandList["yt-playsong"]} <link>          -Plays a YouTube song based on link\n" +
                        "${commandList["yt-nowplaying"]}               -Shows information on currently playing track\n" +
                        "\n" +
                        "SoundCloud commands:\n" +
                        "${commandList["sc-pause"]}                    -Pauses SoundCloud playback\n" +
                        "${commandList["sc-resume"]}                   -Resumes the SoundCloud playback\n" +
                        "${commandList["sc-play"]}                     -Resumes the SoundCloud playback\n" +
                        "${commandList["sc-stop"]}                     -Stops the SoundCloud playback\n" +
                        "${commandList["sc-playsong"]} <link>          -Plays a SoundCloud song based on link\n" +
                        "${commandList["sc-nowplaying"]}               -Shows information on currently playing track\n"
            ),
            Pair(
                "queue-add", "\n" +
                        "Showing help for ${commandList["queue-add"]} command:\n" +
                        "${commandList["queue-add"]} lets you add songs, albums and playlists to the end of the song queue.\n" +
                        "You can also pass in a link to an artist, which will result in the artist's top tracks getting added to the queue.\n" +
                        "Instead of using links, you can search like with the ${commandList["search"]} command,\n" +
                        "but the first search result will be automatically added to the queue.\n" +
                        "Counting starts from 0, so the first song is at position 0, second is at 1 and so on.\n" +
                        "You can add options either before or after song link(s).\n" +
                        "Available options:\n" +
                        "-s    \t-Shuffle the playlist/album etc. before adding to the queue.\n" +
                        "-p    \t-Add track(s) to a specific position in the queue.\n" +
                        "-r    \t-Reverse playlist/album etc. before adding to the queue.\n" +
                        "-l    \t-Limit the amount of tracks to add from the given list(s).\n" +
                        "-t    \t-Add only tracks from the given SoundCloud link (likes/reposts)\n" +
                        "-P    \t-Add only playlists from the given SoundCloud link (likes/reposts)\n" +
                        "Example - Add playlist to queue at position 15 and shuffle it before that:\n" +
                        "${commandList["queue-add"]} -s https://open.spotify.com/playlist/0wlRan09Ls8XDmFXNo07Tt -p 15\n" +
                        "Example - Search for an album on spotify and add it to the queue:\n" +
                        "${commandList["queue-add"]} spotify album Haken Affinity:\n" +
                        "Example - Search for a podcast on spotify and add it to the queue:\n" +
                        "${commandList["queue-add"]} sp podcast Joe Rogan Experience\n" +
                        "Example - Search for a video on YouTube and add it to the queue:\n" +
                        "${commandList["queue-add"]} youtube video Haken Initiate\n" +
                        "Example - Search for a playlist on SoundCloud and add it to the queue:\n" +
                        "${commandList["queue-add"]} soundcloud playlist jeesjees\n"
            ),
            Pair(
                "queue-playnext", "\n" +
                        "Showing help for ${commandList["queue-playnext"]} command:\n" +
                        "${commandList["queue-playnext"]} lets you add songs, albums and playlists to the start of the song queue.\n" +
                        "You can also pass in a link to an artist, which will result in the artist's top tracks getting added to the queue.\n" +
                        "Instead of using links, you can search like with the ${commandList["search"]} command,\n" +
                        "but the first search result will be automatically added to the queue.\n" +
                        "Counting starts from 0, so the first song is at position 0, second is at 1 and so on.\n" +
                        "You can add options either before or after song link(s).\n" +
                        "Available options:\n" +
                        "-s    \t-Shuffle the playlist/album before adding to the queue.\n" +
                        "-p    \t-Add track(s) to a specific position in the queue.\n" +
                        "-r    \t-Reverse playlist/album etc. before adding to the queue.\n" +
                        "-l    \t-Limit the amount of tracks to add from the given list(s).\n" +
                        "-t    \t-Add only tracks from the given SoundCloud link (likes/reposts)\n" +
                        "-P    \t-Add only playlists from the given SoundCloud link (likes/reposts)\n" +
                        "Example - Add playlist to queue at position 15 and shuffle it before that:\n" +
                        "${commandList["queue-playnext"]} -s https://open.spotify.com/playlist/0wlRan09Ls8XDmFXNo07Tt -p 15\n" +
                        "Example - Search for an album on spotify and add it to the queue:\n" +
                        "${commandList["queue-playnext"]} spotify album Haken Affinity:\n" +
                        "Example - Search for a podcast on spotify and add it to the queue:\n" +
                        "${commandList["queue-playnext"]} sp podcast Joe Rogan Experience\n" +
                        "Example - Search for a video on YouTube and add it to the queue:\n" +
                        "${commandList["queue-playnext"]} youtube video Haken Initiate\n" +
                        "Example - Search for a playlist on SoundCloud and add it to the queue:\n" +
                        "${commandList["queue-playnext"]} soundcloud playlist jeesjees\n"
            ),
            Pair(
                "queue-playnow", "\n" +
                        "Showing help for ${commandList["queue-playnow"]} command:\n" +
                        "${commandList["queue-playnow"]} can be used if you want to add songs to the top of the queue, and start playing them immediately.\n" +
                        "Example - Search on spotify for the track \"Pink Floyd - Time\", add the first result to the queue and then start playing it:\n" +
                        "${commandList["queue-playnow"]} sp track pink floyd time"
            ),
            Pair(
                "queue-play", "\n" +
                        "Showing help for ${commandList["queue-play"]} command:\n" +
                        "${commandList["queue-play"]} starts playing songs in the song queue."
            ),
            Pair(
                "queue-list", "\n" +
                        "Showing help for ${commandList["queue-list"]} command:\n" +
                        "${commandList["queue-list"]} shows a list of songs in the queue.\n" +
                        "By default it shows the first 15 songs in the queue.\n" +
                        "Available options:\n" +
                        "-a, --all    \t-Show all songs in the queue\n" +
                        "-l, --limit <amount> -Limit amount of songs to return.\n" +
                        "Example - run %queue-list with a limit of 30 songs:\n" +
                        "${commandList["queue-list"]} --limit 30"
            ),
            Pair(
                "queue-delete", "\n" +
                        "Showing help for ${commandList["queue-delete"]} command:\n" +
                        "${commandList["queue-delete"]} lets you delete a track, or tracks from the queue.\n" +
                        "If the same track is in the queue multiple times,\n" +
                        "the bot will ask you to choose which one you want to delete.\n" +
                        "Available options:\n" +
                        "-a, --all    \t-Delete all matching tracks from the queue.\n" +
                        "-f, --first    \t-Delete the first matching track(s) from the queue.\n" +
                        "-A, --all-artist-tracks    -Delete all tracks where the given artist appears.\n" +
                        "Example - Delete a track from the queue using a link:\n" +
                        "${commandList["queue-delete"]} https://open.spotify.com/track/54k9d97GSM3lBXY61UagKx\n" +
                        "Example - Delete multiple tracks using links:\n" +
                        "${commandList["queue-delete"]} https://open.spotify.com/track/54k9d97GSM3lBXY61UagKx, https://open.spotify.com/track/6le9zgS2y7MQKvDmmGABDW\n" +
                        "Example - Delete a track from the queue at position 86:\n" +
                        "${commandList["queue-delete"]} 86\n" +
                        "Example - Delete a track from the queue at position 86 and 23:\n" +
                        "${commandList["queue-delete"]} 86, 23\n" +
                        "Example - Delete all tracks matching the given link:\n" +
                        "${commandList["queue-delete"]} --all https://open.spotify.com/track/54k9d97GSM3lBXY61UagKx\n" +
                        "Example - Delete all tracks matching the given link:\n" +
                        "${commandList["queue-delete"]} https://open.spotify.com/track/54k9d97GSM3lBXY61UagKx, https://open.spotify.com/track/6le9zgS2y7MQKvDmmGABDW -a\n" +
                        "Example - Delete all tracks where the an artist appears:\n" +
                        "${commandList["queue-delete"]} -A https://open.spotify.com/artist/4l0zTor5S32Yly4uw96Bto\n" +
                        "Example - Delete all tracks where the spotify artist \"lil pump\" appears:\n" +
                        "${commandList["queue-delete"]} -aA sp artist lil pump\n"
            ),
            Pair(
                "queue-clear", "\n" +
                        "Showing help for ${commandList["queue-clear"]} command:\n" +
                        "${commandList["queue-clear"]} command clears the song queue."
            ),
            Pair(
                "queue-shuffle", "\n" +
                        "Showing help for ${commandList["queue-shuffle"]} command:\n" +
                        "${commandList["queue-shuffle"]} shuffles the song queue."
            ),
            Pair(
                "queue-skip", "\n" +
                        "Showing help for ${commandList["queue-skip"]} command:\n" +
                        "${commandList["queue-skip"]} skips to the next song in the queue."
            ),
            Pair(
                "queue-voteskip", "\n" +
                        "Showing help for ${commandList["queue-voteskip"]} command:\n" +
                        "${commandList["queue-voteskip"]} lets you vote to skip the currently playing track in the queue.\n" +
                        "All currently listening users will have to run ${commandList["queue-voteskip"]} too for the skip to happen."
            ),
            Pair(
                "queue-move", "\n" +
                        "Showing help for ${commandList["queue-move"]} command:\n" +
                        "${commandList["queue-move"]} lets you move a song or songs to a new position in the queue.\n" +
                        "If the same track is in the queue multiple times,\n" +
                        "the bot will ask you to choose which one you want to delete\n" +
                        "unless specified otherwise.\n" +
                        "If you don't specify a position using -p or --position, 0 will be used by default.\n" +
                        "Available arguments:\n" +
                        "-p, --position <pos>    \tSet a position where to move the song.\n" +
                        "-a, --all             \t\tMove all matching songs to the new position.\n" +
                        "-f, --first           \t\tMove the first matching song to the new position.\n" +
                        "Example - Move all matching songs to position 10:\n" +
                        "${commandList["queue-move"]} -a https://open.spotify.com/track/6H0zRPEV1ezBHOidNXSt1D -p 10\n" +
                        "${commandList["queue-move"]} --all https://open.spotify.com/track/6H0zRPEV1ezBHOidNXSt1D --position 10\n" +
                        "Example - Move a song from position 5 to position 0:\n" +
                        "${commandList["queue-move"]} 5 -p 0\n" +
                        "Example - Move first matching song to position 10:\n" +
                        "${commandList["queue-move"]} -f https://open.spotify.com/track/6H0zRPEV1ezBHOidNXSt1D -p 10\n" +
                        "${commandList["queue-move"]} --first https://open.spotify.com/track/6H0zRPEV1ezBHOidNXSt1D --position 10\n" +
                        "Example - Move songs from position 3 and 14 to position 5:\n" +
                        "${commandList["queue-move"]} 3, 14 -p 5\n" +
                        "Example - Move first matches of songs to position 8:\n" +
                        "${commandList["queue-move"]} -f https://open.spotify.com/track/54k9d97GSM3lBXY61UagKx, https://open.spotify.com/track/6le9zgS2y7MQKvDmmGABDW -p 8"

            ),
            Pair(
                "queue-stop", "\n" +
                        "Showing help for ${commandList["queue-stop"]} command:\n" +
                        "${commandList["queue-stop"]} stops the song queue.\n"
            ),
            Pair(
                "queue-status", "\n" +
                        "Showing help for ${commandList["queue-status"]} command:\n" +
                        "${commandList["queue-status"]} returns the status of the queue.\n" +
                        "The status can be either \"Active\" or \"Not active\""
            ),
            Pair(
                "queue-nowplaying", "\n" +
                        "Showing help for ${commandList["queue-nowplaying"]} command:\n" +
                        "${commandList["queue-nowplaying"]} returns information on the currently playing song."
            ),
            Pair(
                "queue-pause", "\n" +
                        "Showing help for ${commandList["queue-pause"]} command:\n" +
                        "${commandList["queue-pause"]} pauses the song queue."
            ),
            Pair(
                "queue-resume", "\n" +
                        "Showing help for ${commandList["queue-resume"]} command:\n" +
                        "${commandList["queue-resume"]} resumes playback if the song queue is paused."
            ),
            Pair(
                "queue-repeat", "\n" +
                        "Showing help for ${commandList["queue-repeat"]} command:\n" +
                        "${commandList["queue-repeat"]} Adds the currently playing song back to the top of the queue.\n" +
                        "Available arguments:\n" +
                        "-a, --amount <amount>    \tSet how many times the currently playing song should be added to the queue.\n" +
                        "Example - Repeat the currently playing track once:\n" +
                        "${commandList["queue-repeat"]}\n" +
                        "Example - Repeat the currently playing track 5 times:\n" +
                        "${commandList["queue-repeat"]} -a 5" 
            ),
            Pair(
                "info", "\n" +
                        "Showing help for ${commandList["info"]} command:\n" +
                        "${commandList["info"]} shows information on a given search query or link(s).\n" +
                        "You can either use 1 or more links,\n" +
                        "or search for something on Spotify/YouTube/SoundCloud using the same syntax as with the ${commandList["search"]} command.\n" +
                        "Example 1 - Get info on Spotify track link:\n" +
                        "${commandList["info"]} https://open.spotify.com/track/2igwFfvr1OAGX9SKDCPBwO\n" +
                        "Example 2 - Get info on Spotify URI:\n" +
                        "${commandList["info"]} spotify:track:2igwFfvr1OAGX9SKDCPBwO" +
                        "Example 3 - Get info on SoundCloud track link:\n" +
                        "${commandList["info"]} https://soundcloud.com/iamleeya/something-worth-dreaming-of\n" +
                        "Example 4 - Get info on YouTube track link:\n" +
                        "${commandList["info"]} https://www.youtube.com/watch?v=IKZnGWxJN3I\n" +
                        "Example 5 - Get info on YouTube and SoundCloud link:\n" +
                        "${commandList["info"]} https://youtu.be/IKZnGWxJN3I,https://soundcloud.com/iamleeya/something-worth-dreaming-of\n" +
                        "Example 6 - Show info on the Spotify artist \"The Algorithm\":\n" +
                        "${commandList["info"]} sp artist the algorithm"
            ),
            Pair(
                "search", "\n" +
                        "Showing help for ${commandList["search"]} command:\n" +
                        "${commandList["search"]} can be used to search for tracks, playlists and users on Spotify, Youtube and SoundCloud.\n" +
                        "When searching, you first need to specify which service you want to search on.\n" +
                        "Available services:\n" +
                        "sp, spotify    \t\tDo a Spotify search.\n" +
                        "sc, soundcloud \t\tDo a SoundCloud search.\n" +
                        "yt, youtube    \t\tDo a YouTube search.\n" +
                        "After that, specify what type of search you are doing.\n" +
                        "Available search types:\n" +
                        "track    \t\tSearch for a track. (Spotify, YouTube, SoundCloud)\n" +
                        "playlist    \tSearch for a playlist. (Spotify, YouTube, SoundCloud)\n" +
                        "user      \t\tSearch for a user. (SoundCloud)\n" +
                        "album   \t\tSearch for an album. (Spotify, SoundCloud)\n" +
                        "artist   \t\tSearch for an artist. (Spotify, SoundCloud)\n" +
                        "podcast  \t\tSearch for a podcast. (Spotify)\n" +
                        "show     \t\tSame as podcast. (Spotify)\n" +
                        "episode  \t\tSearch for a podcast episode. (Spotify)\n" +
                        "video    \t\tSearch for a video. Same as track. (YouTube)\n" +
                        "channel   \tSearch for a channel. (YouTube)\n" +
                        "Available options:\n" +
                        "-l, --limit    \tSet amount of search results to show.\n" +
                        "Example - Search on SoundCloud for a track with the name \"leeya - something worth dreaming of\":\n" +
                        "${commandList["search"]} sc track leeya something worth dreaming of\n" +
                        "Example 2 - Search on SoundCloud for a playlist with the name \"jeesjees\" and set a limit to show 10 search results:\n" +
                        "${commandList["search"]} sc playlist jeesjees --limit 10\n" +
                        "Example 3 - Search on SoundCloud for a user with the name \"bettehem\":\n" +
                        "${commandList["search"]} soundcloud user bettehem\n" +
                        "Example - Search for a Spotify album with the keywords \"The Algorithm Brute Force\" and set a limit to show only 5 search results:\n" +
                        "${commandList["search"]} sp album The Algorithm Brute Force --limit 5\n" +
                        "Example - Search on YouTube for a video with the name \"Jinjer Pisces\":\n" +
                        "${commandList["search"]} yt track Jinjer Pisces"
            ),
            Pair(
                "goto", "\n" +
                        "Showing help for ${commandList["goto"]} command:\n" +
                        "${commandList["goto"]} can be used to move the bot to another channel.\n" +
                        "Example - Move to a channel at \"Music/MusicBot\":\n" +
                        "${commandList["goto"]} Music/MusicBot\n" +
                        "Example 2 - Move to a channel at \"Music/MusicBot\" with the password \"123\":\n" +
                        "${commandList["goto"]} Music/MusicBot -p 123\n" +
                        "Example 3 - Move to a channel at \"Music/Music Bot\" with the password \"secret password\"\n" +
                        "${commandList["goto"]} \"Music/Music Bot\" -p \"secret password\"\n"
            ),
            Pair(
                "return", "\n" +
                        "Showing help for ${commandList["return"]} command:\n" +
                        "${commandList["goto"]} can be used to return the bot to the original channel."
            ),
            Pair(
                "sp-pause", "\n" +
                        "Showing help for ${commandList["sp-pause"]} command:\n" +
                        "${commandList["sp-pause"]} pauses Spotify playback."
            ),
            Pair(
                "sp-resume", "\n" +
                        "Showing help for ${commandList["sp-resume"]} command:\n" +
                        "${commandList["sp-resume"]} resumes Spotify playback."
            ),
            Pair(
                "sp-play", "\n" +
                        "Showing help for ${commandList["sp-play"]} command:\n" +
                        "${commandList["sp-play"]} starts/resumes Spotify playback."
            ),
            Pair(
                "sp-stop", "\n" +
                        "Showing help for ${commandList["sp-stop"]} command:\n" +
                        "${commandList["sp-stop"]} stops or pauses Spotify playback."
            ),
            Pair(
                "sp-skip", "\n" +
                        "Showing help for ${commandList["sp-skip"]} command:\n" +
                        "${commandList["sp-skip"]} skips to the next Spotify song in the queue.\n" +
                        "This only affects Spotify's internal song queue and has nothing to do with the music bot's own queue."
            ),
            Pair(
                "sp-next", "\n" +
                        "Showing help for ${commandList["sp-next"]} command:\n" +
                        "${commandList["sp-next"]} skips to the next Spotify song in the queue."
            ),
            Pair(
                "sp-prev", "\n" +
                        "Showing help for ${commandList["sp-prev"]} command:\n" +
                        "${commandList["sp-prev"]} goes back to the previous song in Spotify."
            ),
            Pair(
                "sp-playsong", "\n" +
                        "Showing help for ${commandList["sp-playsong"]} command:\n" +
                        "${commandList["sp-playsong"]} plays a song on Spotify.\n" +
                        "Example - Play a song on Spotify:\n" +
                        "${commandList["sp-playsong"]} https://open.spotify.com/track/2GYHyAoLWpkxLVa4oYTVko"
            ),
            Pair(
                "sp-playlist", "\n" +
                        "Showing help for ${commandList["sp-playlist"]} command:\n" +
                        "${commandList["sp-playlist"]} starts a playlist on Spotify.\n" +
                        "Example - Start a playlist on Spotify:\n" +
                        "${commandList["sp-playlist"]} https://open.spotify.com/playlist/0wlRan09Ls8XDmFXNo07Tt"
            ),
            Pair(
                "sp-playalbum", "\n" +
                        "Showing help for ${commandList["sp-playalbum"]} command:\n" +
                        "${commandList["sp-playalbum"]} plays an album on Spotify.\n" +
                        "Example - Start playing an album on Spotify:\n" +
                        "${commandList["sp-playalbum"]} https://open.spotify.com/album/4Ijivtrfqk2AMTF4dhrl2Q"
            ),
            Pair(
                "sp-nowplaying", "\n" +
                        "Showing help for ${commandList["sp-nowplaying"]} command:\n" +
                        "${commandList["sp-nowplaying"]} returns details on the currently playing track on Spotify."
            ),
            Pair(
                "yt-pause", "\n" +
                        "Showing help for ${commandList["yt-pause"]} command:\n" +
                        "${commandList["yt-pause"]} pauses YouTube playback."
            ),
            Pair(
                "yt-resume", "\n" +
                        "Showing help for ${commandList["yt-resume"]} command:\n" +
                        "${commandList["yt-resume"]} resumes YouTube playback."
            ),
            Pair(
                "yt-play", "\n" +
                        "Showing help for ${commandList["yt-play"]} command:\n" +
                        "${commandList["yt-play"]} resumes YouTube playback if it was paused."
            ),
            Pair(
                "yt-stop", "\n" +
                        "Showing help for ${commandList["yt-stop"]} command:\n" +
                        "${commandList["yt-stop"]} stops YouTube playback.\n" +
                        "This is different from ${commandList["yt-pause"]} command, because ${commandList["yt-stop"]} also exits the player,\n" +
                        "meaning the song can't be resumed."
            ),
            Pair(
                "yt-playsong", "\n" +
                        "Showing help for ${commandList["yt-playsong"]} command:\n" +
                        "${commandList["yt-playsong"]} plays a song from YouTube.\n" +
                        "Example - Play a song from YouTube:\n" +
                        "${commandList["jyt-playsong"]} https://youtu.be/Pn2xd6_-baY\n" +
                        "Example - Play a song from YouTube:\n" +
                        "${commandList["yt-playsong"]} https://www.youtube.com/watch?v=Pn2xd6_-baY"
            ),
            Pair(
                "yt-nowplaying", "\n" +
                        "Showing help for ${commandList["yt-nowplaying"]} command:\n" +
                        "${commandList["yt-nowplaying"]} returns information on the currently playing YouTube track."
            ),
            Pair(
                "sc-pause", "\n" +
                        "Showing help for ${commandList["sc-pause"]} command:\n" +
                        "${commandList["sc-pause"]} pauses SoundCloud playback."
            ),
            Pair(
                "sc-resume", "\n" +
                        "Showing help for ${commandList["sc-resume"]} command:\n" +
                        "${commandList["sc-resume"]} resumes SoundCloud playback."
            ),
            Pair(
                "sc-play", "\n" +
                        "Showing help for ${commandList["sc-play"]} command:\n" +
                        "${commandList["sc-play"]} resumes SoundCloud playback if it was paused."
            ),
            Pair(
                "sc-stop", "\n" +
                        "Showing help for ${commandList["sc-stop"]} command:\n" +
                        "${commandList["sc-stop"]} stops SoundCloud playback. This is different from %sc-pause,\n" +
                        "because %sc-stop also exits the player, which means that the song can't be resumed."
            ),
            Pair(
                "sc-playsong", "\n" +
                        "Showing help for ${commandList["sc-playsong"]} command:\n" +
                        "${commandList["sc-playsong"]} plays a song from SoundCloud.\n" +
                        "Example - play a song from SoundCloud:\n" +
                        "${commandList["sc-playsong"]} https://soundcloud.com/mrsuicidesheep/imagined-herbal-flows-clouds"
            ),
            Pair(
                "sc-nowplaying", "\n" +
                        "Showing help for ${commandList["sc-nowplaying"]} command:\n" +
                        "${commandList["sc-playsong"]} returns information on the currently playing SoundCloud track."
            )
        )

    fun applyCustomCommands(customPrefix: String, customCommands: Map<String, String>) {
        //remove existing command prefixes from commands
        commandList.forEach {
            if ((commandList[it.key] ?: return@forEach).startsWith(commandPrefix))
                commandList[it.key] = it.value.substringAfter(commandPrefix)
        }
        //update commandList with custom commands and apply the custom prefix too.
        commandPrefix = customPrefix
        customCommands.forEach {
            commandList[it.key] = commandPrefix + it.value
        }
        commandList.filterNot { customCommands.keys.contains(it.key) }.forEach {
            commandList[it.key] = commandPrefix + it.value
        }
        //create new help messages with the new command list
        helpMessages = createHelpMessages()
    }
}
