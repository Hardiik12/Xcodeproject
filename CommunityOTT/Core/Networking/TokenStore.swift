//
//  TokenStore.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import Foundation
import Security

public protocol TokenStoreProtocol: Sendable {
    func saveAccessToken(_ token: String) throws
    func retrieveAccessToken() -> String?
    func removeAccessToken()
    func saveRefreshToken(_ token: String) throws
    func retrieveRefreshToken() -> String?
    func removeRefreshToken()
    func clearAllTokens()
}

public final class KeychainTokenStore: TokenStoreProtocol, @unchecked Sendable {
    public static let shared = KeychainTokenStore()
    
    private let service = "com.communityott.auth"
    private let accessTokenAccount = "access_token"
    private let refreshTokenAccount = "refresh_token"
    
    public init() {}
    
    public func saveAccessToken(_ token: String) throws {
        try save(token, account: accessTokenAccount)
    }
    
    public func retrieveAccessToken() -> String? {
        return retrieve(account: accessTokenAccount)
    }
    
    public func removeAccessToken() {
        delete(account: accessTokenAccount)
    }
    
    public func saveRefreshToken(_ token: String) throws {
        try save(token, account: refreshTokenAccount)
    }
    
    public func retrieveRefreshToken() -> String? {
        return retrieve(account: refreshTokenAccount)
    }
    
    public func removeRefreshToken() {
        delete(account: refreshTokenAccount)
    }
    
    public func clearAllTokens() {
        removeAccessToken()
        removeRefreshToken()
    }
    
    // MARK: - Private Keychain Helpers
    
    private func save(_ value: String, account: String) throws {
        guard let data = value.data(using: .utf8) else { return }
        
        delete(account: account)
        
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock
        ]
        
        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw APIError.networkError("Keychain write error: \(status)")
        }
    }
    
    private func retrieve(account: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: kCFBooleanTrue!,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        
        var dataTypeRef: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &dataTypeRef)
        
        guard status == errSecSuccess, let data = dataTypeRef as? Data else {
            return nil
        }
        
        return String(data: data, encoding: .utf8)
    }
    
    private func delete(account: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
    }
}
