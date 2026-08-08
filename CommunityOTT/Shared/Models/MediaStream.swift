//
//  MediaStream.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import Foundation

public struct MediaStream: Identifiable, Codable, Hashable, Sendable {
    public let id: String
    public let contentID: String
    public let streamURL: String
    public let format: MediaFormat
    public let durationInSeconds: Double
    public let subtitleTracks: [SubtitleTrack]
    
    public init(
        id: String,
        contentID: String,
        streamURL: String,
        format: MediaFormat = .mp4,
        durationInSeconds: Double = 300.0,
        subtitleTracks: [SubtitleTrack] = []
    ) {
        self.id = id
        self.contentID = contentID
        self.streamURL = streamURL
        self.format = format
        self.durationInSeconds = durationInSeconds
        self.subtitleTracks = subtitleTracks
    }
}
