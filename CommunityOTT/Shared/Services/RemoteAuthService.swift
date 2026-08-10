//
//  RemoteAuthService.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import Foundation
import Combine

public final class RemoteAuthService: AuthServiceProtocol, @unchecked Sendable {
    private let apiClient: APIClientProtocol
    private let tokenStore: TokenStoreProtocol
    private let fallbackMockAuth: AuthServiceProtocol
    private let statusSubject = CurrentValueSubject<AuthStatus, Never>(.guest)
    private var cancellables = Set<AnyCancellable>()
    
    public init(
        apiClient: APIClientProtocol = URLSessionAPIClient(),
        tokenStore: TokenStoreProtocol = KeychainTokenStore.shared,
        fallbackMockAuth: AuthServiceProtocol = MockAuthService()
    ) {
        self.apiClient = apiClient
        self.tokenStore = tokenStore
        self.fallbackMockAuth = fallbackMockAuth
        
        setupSubscription()
    }
    
    private func setupSubscription() {
        fallbackMockAuth.authStatusPublisher
            .sink { [weak self] status in
                if AppConfiguration.shared.isMockDataEnabled {
                    self?.statusSubject.send(status)
                }
            }
            .store(in: &cancellables)
    }
    
    public var authStatusPublisher: AnyPublisher<AuthStatus, Never> {
        statusSubject.eraseToAnyPublisher()
    }
    
    public func currentStatus() async -> AuthStatus {
        if AppConfiguration.shared.isMockDataEnabled {
            return await fallbackMockAuth.currentStatus()
        }
        if tokenStore.retrieveAccessToken() != nil {
            let user = User(id: "remote-user-1", name: "Community Member", email: "user@communityott.org")
            return .authenticated(user)
        }
        return .guest
    }
    
    public func login(email: String) async throws -> User {
        try await login(name: nil, email: email)
    }
    
    public func login(name: String?, email: String) async throws -> User {
        if AppConfiguration.shared.isMockDataEnabled {
            return try await fallbackMockAuth.login(name: name, email: email)
        }
        do {
            let user: User = try await apiClient.request(AuthEndpoint.login(email: email))
            try? tokenStore.saveAccessToken("remote-access-token-placeholder")
            statusSubject.send(.authenticated(user))
            return user
        } catch {
            return try await fallbackMockAuth.login(name: name, email: email)
        }
    }
    
    public func loginAsGuest() async throws {
        tokenStore.clearAllTokens()
        if AppConfiguration.shared.isMockDataEnabled {
            try await fallbackMockAuth.loginAsGuest()
        } else {
            statusSubject.send(.guest)
        }
    }
    
    public func logout() async throws {
        tokenStore.clearAllTokens()
        if AppConfiguration.shared.isMockDataEnabled {
            try await fallbackMockAuth.logout()
        } else {
            statusSubject.send(.unauthenticated)
        }
    }
}
