//
//  TabItem.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI

public enum TabItem: String, CaseIterable, Identifiable, Sendable {
    case home
    case discover
    case search
    case saved
    case profile
    
    public var id: String { rawValue }
    
    public var title: String {
        switch self {
        case .home: return "Home"
        case .discover: return "Discover"
        case .search: return "Search"
        case .saved: return "Saved"
        case .profile: return "Profile"
        }
    }
    
    public var iconName: String {
        switch self {
        case .home: return "house.fill"
        case .discover: return "safari.fill"
        case .search: return "magnifyingglass"
        case .saved: return "bookmark.fill"
        case .profile: return "person.fill"
        }
    }
}
