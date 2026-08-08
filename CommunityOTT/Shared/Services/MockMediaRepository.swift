//
//  MockMediaRepository.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import Foundation

public final class MockMediaRepository: MediaRepositoryProtocol, @unchecked Sendable {
    nonisolated public init() {}
    
    public func fetchMediaStream(for contentID: String) async throws -> MediaStream {
        // Small async delay simulating media manifest fetch
        try await Task.sleep(nanoseconds: 100_000_000)
        
        // Resolve bundled sample_video.mp4 resource
        let streamPath: String
        if let resourceURL = Bundle.main.url(forResource: "sample_video", withExtension: "mp4") {
            streamPath = resourceURL.absoluteString
        } else {
            // Development fallback path
            streamPath = "sample_video.mp4"
        }
        
        let subtitles = [
            SubtitleTrack(id: "sub-te", language: "te", label: "Telugu"),
            SubtitleTrack(id: "sub-en", language: "en", label: "English")
        ]
        
        return MediaStream(
            id: "stream-\(contentID)",
            contentID: contentID,
            streamURL: streamPath,
            format: .mp4,
            durationInSeconds: 300.0,
            subtitleTracks: subtitles
        )
    }
}
