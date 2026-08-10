//
//  APIEndpoint.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import Foundation

public enum HTTPMethod: String {
    case get = "GET"
    case post = "POST"
    case put = "PUT"
    case delete = "DELETE"
    case patch = "PATCH"
}

public protocol APIEndpoint {
    var path: String { get }
    var method: HTTPMethod { get }
    var headers: [String: String]? { get }
    var queryParameters: [String: String]? { get }
    var body: Data? { get }
    var requiresAuth: Bool { get }
}

public extension APIEndpoint {
    var headers: [String: String]? { nil }
    var queryParameters: [String: String]? { nil }
    var body: Data? { nil }
    var requiresAuth: Bool { false }
}

// MARK: - Auth Endpoints (TBD — Backend Contract Required)
public enum AuthEndpoint: APIEndpoint {
    case login(email: String)
    case requestOTP(phone: String)
    case verifyOTP(phone: String, otp: String)
    case refreshToken(refreshToken: String)
    case logout
    
    public var path: String {
        switch self {
        case .login: return "/auth/login"
        case .requestOTP: return "/auth/otp/request"
        case .verifyOTP: return "/auth/otp/verify"
        case .refreshToken: return "/auth/token/refresh"
        case .logout: return "/auth/logout"
        }
    }
    
    public var method: HTTPMethod {
        switch self {
        case .login, .requestOTP, .verifyOTP, .refreshToken, .logout: return .post
        }
    }
    
    public var requiresAuth: Bool {
        switch self {
        case .logout: return true
        default: return false
        }
    }
}

// MARK: - Content Endpoints (TBD — Backend Contract Required)
public enum ContentEndpoint: APIEndpoint {
    case homeSections
    case contentDetails(id: String)
    case contentByCategory(categoryID: String)
    case contentByIDs(ids: [String])
    
    public var path: String {
        switch self {
        case .homeSections: return "/content/home"
        case .contentDetails(let id): return "/content/\(id)"
        case .contentByCategory(let categoryID): return "/content/category/\(categoryID)"
        case .contentByIDs: return "/content/batch"
        }
    }
    
    public var method: HTTPMethod {
        switch self {
        case .contentByIDs: return .post
        default: return .get
        }
    }
}

// MARK: - Category Endpoints (TBD — Backend Contract Required)
public enum CategoryEndpoint: APIEndpoint {
    case listCategories
    
    public var path: String {
        switch self {
        case .listCategories: return "/categories"
        }
    }
    
    public var method: HTTPMethod { .get }
}

// MARK: - Search Endpoints (TBD — Backend Contract Required)
public enum SearchEndpoint: APIEndpoint {
    case search(query: String)
    
    public var path: String { "/search" }
    public var method: HTTPMethod { .get }
    public var queryParameters: [String: String]? {
        switch self {
        case .search(let query): return ["q": query]
        }
    }
}

// MARK: - User Endpoints (TBD — Backend Contract Required)
public enum UserEndpoint: APIEndpoint {
    case currentProfile
    case updateProfile
    
    public var path: String { "/user/profile" }
    public var method: HTTPMethod {
        switch self {
        case .currentProfile: return .get
        case .updateProfile: return .put
        }
    }
    public var requiresAuth: Bool { true }
}

// MARK: - Saved / My List Endpoints (TBD — Backend Contract Required)
public enum SavedEndpoint: APIEndpoint {
    case fetchSaved
    case addSaved(contentID: String)
    case removeSaved(contentID: String)
    
    public var path: String {
        switch self {
        case .fetchSaved: return "/user/saved"
        case .addSaved(let id), .removeSaved(let id): return "/user/saved/\(id)"
        }
    }
    
    public var method: HTTPMethod {
        switch self {
        case .fetchSaved: return .get
        case .addSaved: return .post
        case .removeSaved: return .delete
        }
    }
    public var requiresAuth: Bool { true }
}

// MARK: - Playback Progress Endpoints (TBD — Backend Contract Required)
public enum PlaybackEndpoint: APIEndpoint {
    case fetchProgress(contentID: String)
    case syncProgress(contentID: String, position: Double, duration: Double)
    
    public var path: String {
        switch self {
        case .fetchProgress(let id): return "/playback/progress/\(id)"
        case .syncProgress(let id, _, _): return "/playback/progress/\(id)"
        }
    }
    
    public var method: HTTPMethod {
        switch self {
        case .fetchProgress: return .get
        case .syncProgress: return .post
        }
    }
    public var requiresAuth: Bool { true }
}

// MARK: - Media Stream Endpoints (TBD — Backend Contract Required)
public enum MediaEndpoint: APIEndpoint {
    case stream(contentID: String)
    
    public var path: String {
        switch self {
        case .stream(let id): return "/media/stream/\(id)"
        }
    }
    
    public var method: HTTPMethod { .get }
    public var requiresAuth: Bool { true }
}
