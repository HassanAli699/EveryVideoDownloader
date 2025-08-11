import os
import yt_dlp as yt
from java import jclass

# Java bindings
Environment = jclass("android.os.Environment")
FFmpegKit = jclass("com.antonkarpenko.ffmpegkit.FFmpegKit")

downloaded_files = []
java_callback = None  # Store Java callback object

def my_hook(d):
    try:
        if java_callback:
            if d['status'] == 'downloading':
                # Calculate progress percentage
                percent = 0
                if d.get('_percent_str'):
                    try:
                        percent = int(float(d['_percent_str'].strip('%')))
                    except:
                        percent = 0

                status_text = f"{percent}% of {d.get('_total_bytes_str', '')}"
                java_callback.update_progress(percent, status_text)

            elif d['status'] == 'finished':
                java_callback.update_progress(100, "Download finished, processing...")
                downloaded_files.append(d['filename'])
    except Exception as e:
        print(f"Progress hook error: {e}")

def download_video_with_progress(url, callback):
    global downloaded_files, java_callback
    downloaded_files = []
    java_callback = callback

    try:
        base_dir = str(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
        out_folder = os.path.join(base_dir, "Every Downloader")
        os.makedirs(out_folder, exist_ok=True)

        outtmpl = os.path.join(out_folder, '%(title)s.%(ext)s')

        ydl_opts = {
            "outtmpl": outtmpl,
            'format': 'bv*+ba/best',
            "ignoreerrors": True,
            "cachedir": False,
            "progress_hooks": [my_hook]
        }

        with yt.YoutubeDL(ydl_opts) as ydl:
            info_dict = ydl.extract_info(url, download=False)
            video_title = str(info_dict.get('title', 'video'))
            ydl.download([url])

            # Merge if two streams were downloaded
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
                    java_callback.completed(out_folder)
                    return
                else:
                    java_callback.error(f"❌ FFmpeg merge failed: {session.getFailStackTrace()}")
                    return

            java_callback.completed(out_folder)

    except Exception as e:
        if java_callback:
            java_callback.error(f"❌ Error: {str(e)}")
