//
//  AppNotification.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import Foundation

public enum NotificationType: String, Codable, Sendable, CaseIterable {
    case content
    case podcast
    case culture
    case education
    case system
    
    public var iconName: String {
        switch self {
        case .content: return "play.tv.fill"
        case .podcast: return "mic.fill"
        case .culture: return "music.note.house.fill"
        case .education: return "book.closed.fill"
        case .system: return "bell.fill"
        }
    }
}

public struct AppNotification: Identifiable, Codable, Sendable, Hashable {
    public let id: String
    public let title: String
    public let message: String
    public let date: Date
    public let type: NotificationType
    public var isRead: Bool
    public let contentID: String?
    
    public init(
        id: String,
        title: String,
        message: String,
        date: Date,
        type: NotificationType,
        isRead: Bool = false,
        contentID: String? = nil
    ) {
        self.id = id
        self.title = title
        self.message = message
        self.date = date
        self.type = type
        self.isRead = isRead
        self.contentID = contentID
    }
}
