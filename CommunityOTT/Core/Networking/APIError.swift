//
//  APIError.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import Foundation

public enum APIError: Error, LocalizedError, Equatable {
    case invalidURL
    case networkError(String)
    case serverError(statusCode: Int)
    case decodingError(String)
    case unauthorized
    case unknown
    
    public var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "The requested endpoint URL is invalid."
        case .networkError(let message):
            return "Network connection failed: \(message)"
        case .serverError(let statusCode):
            return "Server responded with status code \(statusCode)."
        case .decodingError(let details):
            return "Failed to parse response data: \(details)"
        case .unauthorized:
            return "Authentication required. Please sign in again."
        case .unknown:
            return "An unexpected error occurred."
        }
    }
}
