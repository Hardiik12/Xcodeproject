//
//  SubtitleTrack.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import Foundation

public struct SubtitleTrack: Identifiable, Codable, Hashable, Sendable {
    public let id: String
    public let language: String
    public let label: String
    public let url: String?
    
    public init(id: String, language: String, label: String, url: String? = nil) {
        self.id = id
        self.language = language
        self.label = label
        self.url = url
    }
}
