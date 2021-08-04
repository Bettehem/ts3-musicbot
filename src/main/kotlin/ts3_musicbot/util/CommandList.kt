package ts3_musicbot.util

object CommandList {
    val commandList =
        listOf(
            "%help",
            "%queue-add",
            "%queue-playnext",
            "%queue-play",
            "%queue-list",
            "%queue-clear",
            "%queue-shuffle",
            "%queue-skip",
            "%queue-voteskip",
            "%queue-move",
            "%queue-stop",
            "%queue-status",
            "%queue-nowplaying",
            "%queue-pause",
            "%queue-resume",
            "%sp-pause",
            "%sp-resume",
            "%sp-play",
            "%sp-skip",
            "%sp-next",
            "%sp-prev",
            "%sp-playsong",
            "%sp-playlist",
            "%sp-playalbum",
            "%sp-nowplaying",
            "%sp-search",
            "%sp-info",
            "%yt-pause",
            "%yt-resume",
            "%yt-play",
            "%yt-stop",
            "%yt-playsong",
            "%yt-nowplaying",
            "%yt-search",
            "%yt-info",
            "%sc-pause",
            "%sc-resume",
            "%sc-play",
            "%sc-stop",
            "%sc-playsong",
            "%sc-nowplaying",
            "%sc-search",
            "%sc-info"
        )

    val helpMessages = mapOf(
        Pair(
            "%help", "\n" +
                    "General commands:\n" +
                    "%help <command>              -Shows this help message. Use %help <command> to get more help on a specific command.\n" +
                    "%queue-add                   -Add track(s) to queue.\n" +
                    "%queue-playnext              -Add track/playlist/album to the top of the queue. Add multiple links separated by a comma \",\". Shuffle with the -s option\n" +
                    "%queue-play                  -Play the song queue\n" +
                    "%queue-list <--all,--limit>  -Lists current songs in queue. Add the -a/--all option to show all tracks or -l/--limit to set a limit to the amount of tracks.\n" +
                    "%queue-clear                 -Clears the song queue\n" +
                    "%queue-shuffle               -Shuffles the queue\n" +
                    "%queue-skip                  -Skips current song\n" +
                    "%queue-voteskip              -Vote to skip the currently playing track. All users currently listening will also have to run %queue-voteskip for the track to be skipped.\n" +
                    "%queue-move <link> <pos>     -Moves a track to a desired position in the queue. <link> should be your song link and <pos> should be the new position of your song.\n" +
                    "%queue-stop                  -Stops the queue\n" +
                    "%queue-status                -Returns the status of the song queue\n" +
                    "%queue-nowplaying            -Returns information on the currently playing track\n" +
                    "%queue-pause                 -Pauses playback\n" +
                    "%queue-resume                -Resumes playback\n" +
                    "%sp-search <type> <text>     -Search on Spotify. <type> can be track, album, playlist, artist, show or episode.\n" +
                    "%yt-search <type> <text>     -Search on YouTube. Shows 10 first results. <type> can be track, video or playlist\n" +
                    "%sc-search <type> <text>     -Search on SoundCloud. Shows 10 first results. <type> can be track, playlist, album, artist or user\n" +
                    "%sp-info <link>              -Shows info on the given link. <link> can be a Spotify link or a Spotify URI\n" +
                    "%yt-info <link>              -Shows info on the given link. <link> can be a YouTube link.\n" +
                    "%sc-info <link>              -Shows info on the given link. <link> can be a SoundCloud link\n" +
                    "\n\n" +
                    "Player specific commands:\n" +
                    "(These aren't normally needed. Using the commands above is recommended instead)\n" +
                    "\n" +
                    "Spotify commands:\n" +
                    "%sp-pause                    -Pauses the Spotify playback\n" +
                    "%sp-resume                   -Resumes the Spotify playback\n" +
                    "%sp-play                     -Resumes the Spotify playback\n" +
                    "%sp-skip                     -Skips the currently playing track\n" +
                    "%sp-next                     -Skips the currently playing track\n" +
                    "%sp-prev                     -Plays the previous track\n" +
                    "%sp-playsong <track>         -Plays a Spotify song. <track> should be your song link or Spotify URI\n" +
                    "%sp-playlist <playlist>      -Plays a Spotify playlist. <playlist> should be your playlist's link or Spotify URI\n" +
                    "%sp-playalbum <album>        -Plays a Spotify album <album> should be your album's link or Spotify URI\n" +
                    "%sp-nowplaying               -Shows information on currently playing track\n" +
                    "\n" +
                    "YouTube commands:\n" +
                    "%yt-pause                    -Pauses the YouTube playback\n" +
                    "%yt-resume                   -Resumes the YouTube playback\n" +
                    "%yt-play                     -Resumes the YouTube playback\n" +
                    "%yt-stop                     -Stops the YouTube playback\n" +
                    "%yt-playsong <link>          -Plays a YouTube song based on link\n" +
                    "%yt-nowplaying               -Shows information on currently playing track\n" +
                    "\n" +
                    "SoundCloud commands:\n" +
                    "%sc-pause                    -Pauses SoundCloud playback\n" +
                    "%sc-resume                   -Resumes the SoundCloud playback\n" +
                    "%sc-play                     -Resumes the SoundCloud playback\n" +
                    "%sc-stop                     -Stops the SoundCloud playback\n" +
                    "%sc-playsong <link>          -Plays a SoundCloud song based on link\n" +
                    "%sc-nowplaying               -Shows information on currently playing track\n"
        ),
        Pair(
            "%queue-add", "\n" +
                    "Showing help for %queue-add command:\n" +
                    "%queue-add lets you add songs, albums and playlists to the end of the song queue.\n" +
                    "You can also pass in a link to an artist, which will result in the artist's top tracks getting added to the queue.\n" +
                    "Counting starts from 0, so the first song is at position 0, second is at 1 and so on.\n" +
                    "You can add options either before or after song link(s).\n" +
                    "Available options:\n" +
                    "-s    \t-Shuffle the playlist/album before adding to the queue.\n" +
                    "-p    \t-Add track(s) to a specific position in the queue.\n" +
                    "Example - Add playlist to queue at position 15 and shuffle it before that:\n" +
                    "%queue-add -s https://open.spotify.com/playlist/0wlRan09Ls8XDmFXNo07Tt -p 15"
        ),
        Pair(
            "%queue-playnext", "\n" +
                    "Showing help for %queue-playnext command:\n" +
                    "%queue-playnext lets you add songs, albums and playlists to the start of the song queue.\n" +
                    "You can also pass in a link to an artist, which will result in the artist's top tracks getting added to the queue.\n" +
                    "Counting starts from 0, so the first song is at position 0, second is at 1 and so on.\n" +
                    "You can add options either before or after song link(s).\n" +
                    "Available options:\n" +
                    "-s    \t-Shuffle the playlist/album before adding to the queue.\n" +
                    "-p    \t-Add track(s) to a specific position in the queue.\n" +
                    "Example - Add playlist to queue at position 15 and shuffle it before that:\n" +
                    "%queue-playnext -s https://open.spotify.com/playlist/0wlRan09Ls8XDmFXNo07Tt -p 15"
        ),
        Pair(
            "%queue-play", "\n" +
                    "Showing help for %queue-play command:\n" +
                    "%queue-play starts playing songs in the song queue."
        ),
        Pair(
            "%queue-list", "\n" +
                    "Showing help for %queue-list command:\n" +
                    "%queue-list shows a list of songs in the queue.\n" +
                    "By default it shows the first 15 songs in the queue.\n" +
                    "Available options:\n" +
                    "-a, --all    \t-Show all songs in the queue\n" +
                    "-l, --limit <amount> -Limit amount of songs to return.\n" +
                    "Example - run %queue-list with a limit of 30 songs:\n" +
                    "%queue-list --limit 30"
        ),
        Pair(
            "%queue-clear", "\n" +
                    "Showing help for %queue-clear command:\n" +
                    "%queue-clear command clears the song queue."
        ),
        Pair(
            "%queue-shuffle", "\n" +
                    "Showing help for %queue-shuffle command:\n" +
                    "%queue-shuffle shuffles the song queue."
        ),
        Pair(
            "%queue-skip", "\n" +
                    "Showing help for %queue-skip command:\n" +
                    "%queue-skip skips to the next song in the queue."
        ),
        Pair(
            "%queue-voteskip", "\n" +
                    "Showing help for %queue-voteskip command:\n" +
                    "%queue-voteskip lets you vote to skip the currently playing track in the queue.\n" +
                    "All currently listening users will have to run %queue-voteskip too for the skip to happen."
        ),
        Pair(
            "%queue-move", "\n" +
                    "Showing help for %queue-move command:\n" +
                    "%queue-move lets you move a song to a new position in the queue.\n" +
                    "Counting starts from 0, so the first song is at position 0, second is at 1 and so on.\n" +
                    "Available arguments:\n" +
                    "-p, --position <pos>    \tSet a position where to move the song.\n" +
                    "Example - Move a song to position 10\n" +
                    "%queue-move https://open.spotify.com/track/6H0zRPEV1ezBHOidNXSt1D -p 10\n" +
                    "%queue-move https://open.spotify.com/track/6H0zRPEV1ezBHOidNXSt1D --position 10"
        ),
        Pair(
            "%queue-stop", "\n" +
                    "Showing help for %queue-stop command:\n" +
                    "%queue-stop stops the song queue.\n"
        ),
        Pair(
            "%queue-status", "\n" +
                    "Showing help for %queue-status command:\n" +
                    "%queue-status returns the status of the queue.\n" +
                    "The status can be either \"Active\" or \"Not active\""
        ),
        Pair(
            "%queue-nowplaying", "\n" +
                    "Showing help for %queue-nowplaying command:\n" +
                    "%queue-nowplaying returns information on the currently playing song."
        ),
        Pair(
            "%queue-pause", "\n" +
                    "Showing help for %queue-pause command:\n" +
                    "%queue-pause pauses the song queue."
        ),
        Pair(
            "%queue-resume", "\n" +
                    "Showing help for %queue-resume command:\n" +
                    "%queue-resume resumes playback if the song queue is paused."
        ),
        Pair(
            "%sp-search", "\n" +
                    "Showing help for %sp-search command:\n" +
                    "%sp-search can be used to search for tracks, albums, playlists, artists, shows and episodes on Spotify.\n" +
                    "To perform a search, you need to provide a search type followed by keywords.\n" +
                    "Example - Search for a Spotify track with the keywords \"Tesseract Exile\":\n" +
                    "%sp-search track Tesseract Exile\n" +
                    "Example - Search for a Spotify album with the keywords \"The Algorithm Brute Force\":\n" +
                    "%sp-search album The Algorithm Brute Force"
        ),
        Pair(
            "%yt-search", "\n" +
                    "Showing help for %yt-search command:\n" +
                    "%yt-search can be used to search for tracks/videos and playlists on YouTube.\n" +
                    "When searching, you need to specify what type of search you are doing.\n" +
                    "Available search types:\n" +
                    "video    \tSearch for a YouTube video.\n" +
                    "track    \tSame as video.\n" +
                    "playlist    \tSearch for a YouTube playlist.\n" +
                    "Example - Search on YouTube for a video with the name \"Jinjer Pisces\":\n" +
                    "%yt-search track Jinjer Pisces\n" +
                    "Example 2:\n" +
                    "%yt-search video Jinjer Pisces"
        ),
        Pair(
            "%sc-search", "\n" +
                    "Showing help for %sc-search command:\n" +
                    "%sc-search can be used to search for tracks, playlists and users on SoundCloud.\n" +
                    "When searching, you need to specify what type of search you are doing.\n" +
                    "Available search types:\n" +
                    "track    \t\tSearch for a SoundCloud track.\n" +
                    "playlist    \tSearch for a SoundCloud playlist.\n" +
                    "user     \t\tSearch for a SoundCloud user.\n" +
                    "album    \t\tSearch for a SoundCloud album.\n" +
                    "artist   \t\tSearch for a SoundCloud artist.\n" +
                    "Example - Search on SoundCloud for a track with the name \"leeya - something worth dreaming of\":\n" +
                    "%sc-search track leeya something worth dreaming of\n" +
                    "Example 2 - Search on SoundCloud for a playlist with the name \"jeesjees\":\n" +
                    "%sc-search playlist jeesjees\n" +
                    "Example 3 - Search on SoundCloud for a user with the name \"bettehem\":\n" +
                    "%sc-search user bettehem"
        ),
        Pair(
            "%sp-info", "\n" +
                    "Showing help for %sp-info command:\n" +
                    "%sp-info shows information on a given Spotify link.\n" +
                    "Example - Get info on Spotify track link:\n" +
                    "%sp-info https://open.spotify.com/track/2igwFfvr1OAGX9SKDCPBwO\n" +
                    "Example - Get info on Spotify URI:\n" +
                    "%sp-info spotify:track:2igwFfvr1OAGX9SKDCPBwO"
        ),
        Pair(
            "%sc-info", "\n" +
                    "Showing help for %sc-info command:\n" +
                    "%sp-info shows information on a given SoundCloud link.\n" +
                    "Example - Get info on SoundCloud track link:\n" +
                    "%sc-info https://soundcloud.com/iamleeya/something-worth-dreaming-of\n"
        ),
        Pair(
            "%yt-info", "\n" +
                    "Showing help for %yt-info command:\n" +
                    "%yt-info shows information on a given YouTube link.\n" +
                    "Example - Get info on YouTube track link:\n" +
                    "%yt-info https://youtu.be/IKZnGWxJN3I\n"
        ),
        Pair(
            "%sp-pause", "\n" +
                    "Showing help for %sp-pause command:\n" +
                    "%sp-pause pauses Spotify playback."
        ),
        Pair(
            "%sp-resume", "\n" +
                    "Showing help for %sp-resume command:\n" +
                    "%sp-resume resumes Spotify playback."
        ),
        Pair(
            "%sp-play", "\n" +
                    "Showing help for %sp-play command:\n" +
                    "%sp-play starts/resumes Spotify playback."
        ),
        Pair(
            "%sp-skip", "\n" +
                    "Showing help for %sp-skip command:\n" +
                    "%sp-skip skips to the next Spotify song in the queue.\n" +
                    "This only affects Spotify's internal song queue and has nothing to do with the music bot's own queue."
        ),
        Pair(
            "%sp-next", "\n" +
                    "Showing help for %sp-next command:\n" +
                    "%sp-next skips to the next Spotify song in the queue."
        ),
        Pair(
            "%sp-prev", "\n" +
                    "Showing help for %sp-prev command:\n" +
                    "%sp-prev goes back to the previous song in Spotify."
        ),
        Pair(
            "%sp-playsong", "\n" +
                    "Showing help for %sp-playsong command:\n" +
                    "%sp-playsong plays a song on Spotify.\n" +
                    "Example - Play a song on Spotify:\n" +
                    "%sp-playsong https://open.spotify.com/track/2GYHyAoLWpkxLVa4oYTVko"
        ),
        Pair(
            "%sp-playlist", "\n" +
                    "Showing help for %sp-playlist command:\n" +
                    "%sp-playlist starts a playlist on Spotify.\n" +
                    "Example - Start a playlist on Spotify:\n" +
                    "%sp-playlist https://open.spotify.com/playlist/0wlRan09Ls8XDmFXNo07Tt"
        ),
        Pair(
            "%sp-playalbum", "\n" +
                    "Showing help for %sp-playalbum command:\n" +
                    "%sp-playalbum plays an album on Spotify.\n" +
                    "Example - Start playing an album on Spotify:\n" +
                    "%sp-playalbum https://open.spotify.com/album/4Ijivtrfqk2AMTF4dhrl2Q"
        ),
        Pair(
            "%sp-nowplaying", "\n" +
                    "Showing help for %sp-nowplaying command:\n" +
                    "%sp-nowplaying returns details on the currently playing track on Spotify."
        ),
        Pair(
            "%yt-pause", "\n" +
                    "Showing help for %yt-pause command:\n" +
                    "%yt-pause pauses YouTube playback."
        ),
        Pair(
            "%yt-resume", "\n" +
                    "Showing help for %yt-resume command:\n" +
                    "%yt-resume resumes YouTube playback."
        ),
        Pair(
            "%yt-play", "\n" +
                    "Showing help for %yt-play command:\n" +
                    "%yt-play resumes YouTube playback if it was paused."
        ),
        Pair(
            "%yt-stop", "\n" +
                    "Showing help for %yt-stop command:\n" +
                    "%yt-stop stops YouTube playback.\n" +
                    "This is different from %yt-pause command, because %yt-stop also exits the player,\n" +
                    "meaning the song can't be resumed."
        ),
        Pair(
            "%yt-playsong", "\n" +
                    "Showing help for %yt-playsong command:\n" +
                    "%yt-playsong plays a song from YouTube.\n" +
                    "Example - Play a song from YouTube:\n" +
                    "%yt-playsong https://youtu.be/Pn2xd6_-baY\n" +
                    "Example - Play a song from YouTube:\n" +
                    "%yt-playsong https://www.youtube.com/watch?v=Pn2xd6_-baY"
        ),
        Pair(
            "%yt-nowplaying", "\n" +
                    "Showing help for %yt-nowplaying command:\n" +
                    "%yt-nowplaying returns information on the currently playing YouTube track."
        ),
        Pair(
            "%sc-pause", "\n" +
                    "Showing help for %sc-pause command:\n" +
                    "%sc-pause pauses SoundCloud playback."
        ),
        Pair(
            "%sc-resume", "\n" +
                    "Showing help for %sc-resume command:\n" +
                    "%sc-resume resumes SoundCloud playback."
        ),
        Pair(
            "%sc-play", "\n" +
                    "Showing help for %sc-play command:\n" +
                    "%sc-play resumes SoundCloud playback if it was paused."
        ),
        Pair(
            "%sc-stop", "\n" +
                    "Showing help for %sc-stop command:\n" +
                    "%sc-stop stops SoundCloud playback. This is different from %sc-pause,\n" +
                    "because %sc-stop also exits the player, which means that the song can't be resumed."
        ),
        Pair(
            "%sc-playsong", "\n" +
                    "Showing help for %sc-playsong command:\n" +
                    "%sc-playsong plays a song from SoundCloud.\n" +
                    "Example - play a song from SoundCloud:\n" +
                    "%sc-playsong https://soundcloud.com/mrsuicidesheep/imagined-herbal-flows-clouds"
        ),
        Pair(
            "%sc-nowplaying", "\n" +
                    "Showing help for %sc-nowplaying command:\n" +
                    "%sc-playsong returns information on the currently playing SoundCloud track."
        )
    )

}
