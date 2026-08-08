//
//  SubtitleTrack.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import Foundation

public struct SubtitleTrack: Identifiable, Codable, Hashable, Sendable {
    public let id: String
    public let languageCode: String
    public let languageName: String
    public let url: String?
    
    public init(id: String, languageCode: String, languageName: String, url: String? = nil) {
        self.id = id
        self.languageCode = languageCode
        self.languageName = languageName
        self.url = url
    }
}
