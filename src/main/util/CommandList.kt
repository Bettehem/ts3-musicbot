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
                "%queue-add <-s> <link> <pos> -Add track(s) to queue. Also add the -s if you want a list/album to be pre-shuffled before adding to the queue. You can add multiple links separated by a comma \",\". pos can be the position in which you want to place your link(s) in the queue, starting from 0\n" +
                "%queue-playnext <-s> <link> <pos> -Add track/playlist/album to the top of the queue. Add multiple links separated by a comma \",\". Shuffle with the -s option\n" +
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
                "%queue-add lets you add songs, albums and playlists to the end of the song queue."
    ),
    Pair(
        "%queue-playnext", "\n" +
                "Showing help for %queue-playnext command:\n" +
                "%queue-playnext lets you add songs, albums and playlists to the end of the song queue."
    )
)