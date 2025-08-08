import os
import yt_dlp as yt
from java import jclass

# Java class bindings
Environment = jclass("android.os.Environment")
FFmpegKit = jclass("com.antonkarpenko.ffmpegkit.FFmpegKit")

downloaded_files = []

def my_hook(d):
    if d['status'] == 'finished':
        print(f"Downloaded: {d['filename']}")
        downloaded_files.append(d['filename'])

def download_video(url):
    global downloaded_files
    downloaded_files = []

    base_dir = str(Environment.getExternalStorageDirectory())
    out_folder = os.path.join(base_dir, "Download/ytdl")
    os.makedirs(out_folder, exist_ok=True)

    outtmpl = os.path.join(out_folder, '%(title)s.%(ext)s')

    ydl_opts = {
        "outtmpl": outtmpl,
       'format': 'bv*+ba/best',
        "ignoreerrors": True,
        "cachedir": False,
        "progress_hooks": [my_hook]
    }

    try:
        with yt.YoutubeDL(ydl_opts) as ydl:
            info_dict = ydl.extract_info(url, download=False)
            video_title = str(info_dict.get('title', 'video'))
            ydl.download([url])

            if len(downloaded_files) == 2:
                merged_output = os.path.join(out_folder, f"{video_title}_merged.mp4")

                command = (
                    f'-i "{downloaded_files[0]}" '
                    f'-i "{downloaded_files[1]}" '
                    f'-c:v copy -c:a aac -strict experimental "{merged_output}"'
                )

                session = FFmpegKit.execute(command)

                if session.getReturnCode().isValueSuccess():
                    os.remove(downloaded_files[0])
                    os.remove(downloaded_files[1])
                    return f"✅ Downloaded and merged: {merged_output}"
                else:
                    return f"❌ FFmpeg merge failed: {session.getFailStackTrace()}"

            return "✅ Single stream downloaded"
    except Exception as e:
        return f"❌ Error: {str(e)}"
