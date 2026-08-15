package com.communityott.content.processing;

import java.io.File;

public interface FFprobeService {

    /**
     * Probes media file metadata and validates media integrity.
     *
     * @param mediaFile Local media file to probe
     * @return MediaProbeResult capturing stream codecs, dimensions, duration, bitrate, and raw json
     */
    MediaProbeResult probe(File mediaFile);
}
