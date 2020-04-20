package src.main.util

val commandList =
    listOf(
        "%help" +
                "%queue-add" +
                "%queue-playnext" +
                "%queue-play" +
                "%queue-list" +
                "%queue-clear" +
                "%queue-shuffle" +
                "%queue-skip" +
                "%queue-move" +
                "%queue-stop" +
                "%queue-status" +
                "%queue-nowplaying" +
                "%queue-pause" +
                "%queue-resume" +
                "%sp-pause" +
                "%sp-resume" +
                "%sp-play" +
                "%sp-skip" +
                "%sp-next" +
                "%sp-prev" +
                "%sp-playsong" +
                "%sp-playlist" +
                "%sp-playalbum" +
                "%sp-nowplaying" +
                "%sp-search" +
                "%sp-info" +
                "%yt-pause" +
                "%yt-resume" +
                "%yt-play" +
                "%yt-stop" +
                "%yt-playsong" +
                "%yt-nowplaying" +
                "%yt-search" +
                "%sc-pause" +
                "%sc-resume" +
                "%sc-play" +
                "%sc-stop" +
                "%sc-playsong" +
                "%sc-nowplaying"
    )

val helpMessages = hashMapOf(
    Pair(
        "%help", "\n" +
                "General commands:\n" +
                "%help <command>              -Shows this help message. Use %help <command> to get more help on a specific command.\n" +
                "%queue-add                   -Add track(s) to queue.\n" +
                "%queue-playnext              -Add track/playlist/album to the top of the queue. Add multiple links separated by a comma \",\". Shuffle with the -s option\n" +
                "%queue-play                  -Play the song queue\n" +
                "%queue-list <-a,--all>       -Lists current songs in queue. Add the -a or --all option to show all tracks if there are more than 15\n" +
                "%queue-clear                 -Clears the song queue\n" +
                "%queue-shuffle               -Shuffles the queue\n" +
                "%queue-skip                  -Skips current song\n" +
                "%queue-move <link> <pos>     -Moves a track to a desired position in the queue. <link> should be your song link and <pos> should be the new position of your song.\n" +
                "%queue-stop                  -Stops the queue\n" +
                "%queue-status                -Returns the status of the song queue\n" +
                "%queue-nowplaying            -Returns information on the currently playing track\n" +
                "%queue-pause                 -Pauses playback\n" +
                "%queue-resume                -Resumes playback\n" +
                "\n\n" +
                "Player specific commands:\n" +
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
                "%sp-search <type> <text>     -Search on Spotify. <type> can be track, album or playlist\n" +
                "%sp-info <link>              -Shows info on the given link. <link> can be a Spotify track link or Spotify URI\n" +
                "\n" +
                "YouTube commands:\n" +
                "%yt-pause                    -Pauses the YouTube playback\n" +
                "%yt-resume                   -Resumes the YouTube playback\n" +
                "%yt-play                     -Resumes the YouTube playback\n" +
                "%yt-stop                     -Stops the YouTube playback\n" +
                "%yt-playsong <link>          -Plays a YouTube song based on link\n" +
                "%yt-nowplaying               -Shows information on currently playing track\n" +
                "%yt-search <type> <text>     -Search on YouTube. Shows 10 first results. <type> can be track, video or playlist\n" +
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
                "-a, --all    \t-Show all songs in the queue"
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
        "%sp-search", "\n" +
                "Showing help for %sp-search command:\n" +
                "%sp-search can be used to search for tracks, albums and playlists on Spotify.\n" +
                "To perform a search, you need to provide a search type followed by keywords.\n" +
                "Example - Search for a Spotify track with the keywords \"Tesseract Exile\":\n" +
                "%sp-search track Tesseract Exile\n" +
                "Example - Search for a Spotify album with the keywords \"The Algorithm Brute Force\":\n" +
                "%sp-search album The Algorithm Brute Force"
    ),
    Pair(
        "%sp-info", "\n" +
                "Showing help for %sp-info command:\n" +
                "%sp-info shows information on a given track.\n" +
                "Example - Get info on spotify track link:\n" +
                "%sp-info https://open.spotify.com/track/2igwFfvr1OAGX9SKDCPBwO\n" +
                "Example - Get info on spotify URI:\n" +
                "%sp-info spotify:track:2igwFfvr1OAGX9SKDCPBwO"
    )
)