//
//  UserProfileStore.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import Foundation
import Combine

public struct AvatarOption: Identifiable, Hashable, Sendable {
    public let id: String
    public let title: String
    public let systemIcon: String
    
    public init(id: String, title: String, systemIcon: String) {
        self.id = id
        self.title = title
        self.systemIcon = systemIcon
    }
    
    public static let defaultAvatars: [AvatarOption] = [
        AvatarOption(id: "gold", title: "Gold Ring", systemIcon: "person.crop.circle.fill"),
        AvatarOption(id: "crest", title: "Cultural Crest", systemIcon: "crown.fill"),
        AvatarOption(id: "star", title: "Community Star", systemIcon: "star.circle.fill"),
        AvatarOption(id: "folk", title: "Folk & Arts", systemIcon: "music.note.house.fill")
    ]
}

public final class UserProfileStore: ObservableObject, @unchecked Sendable {
    public static let shared = UserProfileStore()
    
    private static let nameKey = "communityott_user_display_name"
    private static let avatarKey = "communityott_user_avatar_id"
    
    @Published public var displayName: String {
        didSet {
            UserDefaults.standard.set(displayName, forKey: Self.nameKey)
        }
    }
    
    @Published public var avatarID: String {
        didSet {
            UserDefaults.standard.set(avatarID, forKey: Self.avatarKey)
        }
    }
    
    private init() {
        let name = UserDefaults.standard.string(forKey: Self.nameKey) ?? "Hardik Gupta"
        let avatar = UserDefaults.standard.string(forKey: Self.avatarKey) ?? "gold"
        
        self.displayName = name
        self.avatarID = avatar
    }
    
    public var currentAvatar: AvatarOption {
        AvatarOption.defaultAvatars.first { $0.id == avatarID } ?? AvatarOption.defaultAvatars[0]
    }
    
    public func updateProfile(name: String, avatarID: String) {
        DispatchQueue.main.async { [weak self] in
            self?.displayName = name
            self?.avatarID = avatarID
        }
    }
}
