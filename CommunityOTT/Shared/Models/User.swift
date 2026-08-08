//
//  User.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import Foundation

public struct User: Identifiable, Codable, Hashable, Sendable {
    public let id: String
    public let name: String
    public let email: String
    public let avatarURL: String?
    public let preferredLanguage: String
    public let isSubscribed: Bool
    
    public init(
        id: String,
        name: String,
        email: String,
        avatarURL: String? = nil,
        preferredLanguage: String = "English",
        isSubscribed: Bool = false
    ) {
        self.id = id
        self.name = name
        self.email = email
        self.avatarURL = avatarURL
        self.preferredLanguage = preferredLanguage
        self.isSubscribed = isSubscribed
    }
}
