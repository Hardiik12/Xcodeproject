//
//  AppConfiguration.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import Foundation

public enum AppEnvironment: String, Codable, CaseIterable, Sendable {
    case development
    case staging
    case production
}

public final class AppConfiguration: @unchecked Sendable {
    public static let shared = AppConfiguration()
    
    public var currentEnvironment: AppEnvironment {
        didSet {
            UserDefaults.standard.set(currentEnvironment.rawValue, forKey: "communityott_app_environment")
        }
    }
    
    public var isMockDataEnabled: Bool {
        didSet {
            UserDefaults.standard.set(isMockDataEnabled, forKey: "communityott_mock_data_enabled")
        }
    }
    
    private init() {
        let envRaw = UserDefaults.standard.string(forKey: "communityott_app_environment") ?? AppEnvironment.development.rawValue
        self.currentEnvironment = AppEnvironment(rawValue: envRaw) ?? .development
        
        let mockDefault = UserDefaults.standard.object(forKey: "communityott_mock_data_enabled") as? Bool ?? true
        self.isMockDataEnabled = mockDefault
    }
    
    public var baseURL: URL {
        switch currentEnvironment {
        case .development:
            return URL(string: "https://api-dev.communityott.org/v1")!
        case .staging:
            return URL(string: "https://api-staging.communityott.org/v1")!
        case .production:
            return URL(string: "https://api.communityott.org/v1")!
        }
    }
}
