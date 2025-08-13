import os
import pathlib
import yt_dlp as yt
from java import jclass

# Java bindings
FFmpegKit = jclass("com.antonkarpenko.ffmpegkit.FFmpegKit")

downloaded_files = []
java_callback = None
current_stage = None
stage_progress = {"video": 0, "audio": 0}

def my_hook(d):
    try:
        if java_callback:
            if d['status'] == 'downloading':
                percent = 0
                if d.get('_percent_str'):
                    try:
                        percent = int(float(d['_percent_str'].strip('%')))
                    except:
                        percent = 0

                if current_stage in stage_progress:
                    stage_progress[current_stage] = percent

                total_percent = int(
                    (stage_progress["video"] + stage_progress["audio"]) / 2
                )
                print(f"[Python] Progress: stage={current_stage} percent={percent} total_percent={total_percent}")
                java_callback.update_progress(total_percent, str(total_percent))

            elif d['status'] == 'finished':
                filename = d.get('filename', 'unknown')
                print(f"[Python] Download finished: {filename}")
                downloaded_files.append(filename)
    except Exception as e:
        print(f"[Python] Progress hook error: {e}")

def get_unique_filename(path):
    path = pathlib.Path(path)
    counter = 1
    while path.exists():
        path = path.with_name(f"{path.stem}_{counter}{path.suffix}")
        counter += 1
    return str(path)

def format_user_friendly_error(e):
    """
    Convert raw errors into short, clear explanations for the user.
    """
    err_str = str(e).lower()

    # Network-related errors
    if "unable to download video" in err_str or "http error" in err_str:
        return "Network error — Check your internet connection or try again later."
    elif "url" in err_str and "invalid" in err_str:
        return "Invalid link — Please check if the video URL is correct."
    elif "video unavailable" in err_str or "private" in err_str:
        return "This video is private or unavailable."
    elif "no such file" in err_str or "file not found" in err_str:
        return "File not found — The video/audio file could not be located."
    elif "ffmpeg" in err_str:
        return "Video processing failed — FFmpeg could not merge the files."
    elif "permission" in err_str:
        return "Storage permission error — Please allow storage access."
    else:
        return "Unexpected error — Please try again."

def download_video_with_progress(url, folder_path, callback):
    global downloaded_files, java_callback, current_stage, stage_progress
    downloaded_files = []
    java_callback = callback
    stage_progress = {"video": 0, "audio": 0}

    try:
        java_callback.update_progress(0, "Waiting For Response...")

        out_folder = str(folder_path)
        os.makedirs(out_folder, exist_ok=True)

        # VIDEO
        video_tmpl = os.path.join(out_folder, '%(title)s_video.%(ext)s')
        current_stage = "video"
        with yt.YoutubeDL({
            "outtmpl": video_tmpl,
            "format": "bv*+ba/bv*/best",  # will try bestvideo+audio, else fallback to best
            "ignoreerrors": True,
            "cachedir": False,
            "progress_hooks": [my_hook]
        }) as ydl:
            info_dict = ydl.extract_info(url, download=False)
            video_title = str(info_dict.get('title', 'video'))
            video_ext = info_dict.get('ext', 'mp4')
            ydl.download([url])

        # AUDIO
        audio_tmpl = os.path.join(out_folder, '%(title)s_audio.%(ext)s')
        current_stage = "audio"
        with yt.YoutubeDL({
            "outtmpl": audio_tmpl,
            "format": "ba",
            "ignoreerrors": True,
            "cachedir": False,
            "progress_hooks": [my_hook]
        }) as ydl:
            ydl.download([url])

        java_callback.update_progress(90, "Processing...")

        # --- Decision logic ---
        if len(downloaded_files) == 1:
            # Only one file — check if it’s already a video
            single_file = downloaded_files[0]
            if single_file.lower().endswith(('.mp4', '.mov', '.mkv', '.avi', '.webm')):
                java_callback.update_progress(100, "Making Video Upload Ready Wait...")
                java_callback.completed(single_file)
                return
            else:
                raise Exception("Only one file downloaded but it’s not a supported video format")

        elif len(downloaded_files) >= 2:
            # Merge video and audio
            merged_output = get_unique_filename(os.path.join(out_folder, f"{video_title}.{video_ext}"))
            command = (
                f'-i "{downloaded_files[0]}" '
                f'-i "{downloaded_files[1]}" '
                f'-c:v copy -c:a aac -strict experimental "{merged_output}"'
            )

            session = FFmpegKit.execute(command)
            ret_code = session.getReturnCode()

            if ret_code.isValueSuccess():
                try:
                    os.remove(downloaded_files[0])
                    os.remove(downloaded_files[1])
                except Exception as cleanup_err:
                    print(f"Cleanup error: {cleanup_err}")
                java_callback.update_progress(100, "Making Video Upload Ready Wait...")
                java_callback.completed(merged_output)
                return
            else:
                raise Exception(session.getFailStackTrace())

        else:
            raise Exception("No files downloaded")

    except Exception as e:
        print(f"[Python] Full error: {e}")
        if java_callback:
            java_callback.error(format_user_friendly_error(e))
