/*
 *
 * Copyright 2014 Jules White
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.magnum.dataup;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.magnum.dataup.model.Video;
import org.magnum.dataup.model.VideoStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

@org.springframework.stereotype.Controller
public class Controller {
    private static final AtomicLong currentId = new AtomicLong(0L);

    private final Map<Long, Video> videos = new HashMap<>();

    @GetMapping("/video")
    @ResponseBody
    public Collection<Video> getVideo() {
        return videos.values();
    }

    @PostMapping("/video")
    @ResponseBody
    public Video postVideo(@RequestBody Video video) {
        video.setId(currentId.incrementAndGet());
        video.setDataUrl(getDataUrl(video.getId()));
        videos.put(video.getId(), video);
        return video;
    }

    @PostMapping("/video/{id}/data")
    @ResponseBody
    public VideoStatus postVideoData(@PathVariable("id") Long id,
            @RequestParam("data") MultipartFile data,
            HttpServletResponse response) throws IOException {
        Video video = videos.get(id);

        if (video == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }

        InputStream in = data.getInputStream();
        VideoFileManager.get().saveVideoData(video, in);
        return new VideoStatus(VideoStatus.VideoState.READY);
    }

    @GetMapping("/video/{id}/data")
    public void getVideoData(@PathVariable("id") Long id,
            HttpServletResponse response) throws IOException {
        Video video = videos.get(id);

        if (video == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        VideoFileManager manager = VideoFileManager.get();

        if (!manager.hasVideoData(video)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        ServletOutputStream out = response.getOutputStream();
        manager.copyVideoData(video, out);
    }

    private String getDataUrl(long videoId) {
        return getUrlBaseForLocalServer() + "/video/" + videoId + "/data";
    }

    private String getUrlBaseForLocalServer() {
        HttpServletRequest request =
                ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String base =
                "http://" + request.getServerName()
                        + ((request.getServerPort() != 80) ? ":" + request.getServerPort() : "");
        return base;
    }
}
