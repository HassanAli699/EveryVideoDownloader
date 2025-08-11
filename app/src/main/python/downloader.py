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

def download_video_with_progress(url, folder_path, callback):
    global downloaded_files, java_callback, current_stage, stage_progress
    downloaded_files = []
    java_callback = callback
    stage_progress = {"video": 0, "audio": 0}

    try:
        java_callback.update_progress(0, "waiting")  # 👈 Tell Java we're waiting

        out_folder = str(folder_path)
        os.makedirs(out_folder, exist_ok=True)

        # VIDEO
        video_tmpl = os.path.join(out_folder, '%(title)s_video.%(ext)s')
        current_stage = "video"
        with yt.YoutubeDL({
            "outtmpl": video_tmpl,
            "format": "bv*",
            "ignoreerrors": True,
            "cachedir": False,
            "progress_hooks": [my_hook]
        }) as ydl:
            info_dict = ydl.extract_info(url, download=False)
            video_title = str(info_dict.get('title', 'video'))
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

        # MERGE
        java_callback.update_progress(0, "processing")  # 👈 Merging stage

        merged_output = get_unique_filename(os.path.join(out_folder, f"{video_title}.mp4"))
        if len(downloaded_files) < 2:
            raise Exception("Not enough files downloaded to merge")

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
            except Exception as e:
                print(f"Cleanup error: {e}")

            java_callback.completed(merged_output)
        else:
            error_msg = session.getFailStackTrace()
            java_callback.error(f"❌ FFmpeg merge failed: {error_msg}")

    except Exception as e:
        if java_callback:
            java_callback.error(f"❌ Error: {str(e)}")
