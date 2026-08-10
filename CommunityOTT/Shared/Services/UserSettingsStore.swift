//
//  UserSettingsStore.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import Foundation
import Combine

public enum VideoQuality: String, Codable, CaseIterable, Identifiable, Sendable {
    case auto = "Auto"
    case dataSaver = "Data Saver"
    case highQuality = "High Quality"
    
    public var id: String { rawValue }
    
    public var displayName: String { rawValue }
}

public final class UserSettingsStore: ObservableObject, @unchecked Sendable {
    public static let shared = UserSettingsStore()
    
    private static let notificationsKey = "communityott_notifications_enabled"
    private static let autoplayKey = "communityott_autoplay_enabled"
    private static let qualityKey = "communityott_video_quality"
    
    @Published public var notificationsEnabled: Bool {
        didSet {
            UserDefaults.standard.set(notificationsEnabled, forKey: Self.notificationsKey)
        }
    }
    
    @Published public var autoplayEnabled: Bool {
        didSet {
            UserDefaults.standard.set(autoplayEnabled, forKey: Self.autoplayKey)
        }
    }
    
    @Published public var videoQuality: VideoQuality {
        didSet {
            UserDefaults.standard.set(videoQuality.rawValue, forKey: Self.qualityKey)
        }
    }
    
    private init() {
        let notifDefault = UserDefaults.standard.object(forKey: Self.notificationsKey) as? Bool ?? true
        let autoDefault = UserDefaults.standard.object(forKey: Self.autoplayKey) as? Bool ?? true
        let rawQuality = UserDefaults.standard.string(forKey: Self.qualityKey) ?? VideoQuality.auto.rawValue
        let qualDefault = VideoQuality(rawValue: rawQuality) ?? .auto
        
        self.notificationsEnabled = notifDefault
        self.autoplayEnabled = autoDefault
        self.videoQuality = qualDefault
    }
}
