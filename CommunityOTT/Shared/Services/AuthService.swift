//
//  AuthService.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import Foundation
import Combine

public enum AuthStatus: Equatable, Sendable {
    case unauthenticated
    case authenticated(User)
    case guest
}

public protocol AuthServiceProtocol: Sendable {
    var authStatusPublisher: AnyPublisher<AuthStatus, Never> { get }
    func currentStatus() async -> AuthStatus
    func login(email: String) async throws -> User
    func loginAsGuest() async throws
    func logout() async throws
}

public final class MockAuthService: AuthServiceProtocol, @unchecked Sendable {
    private let statusSubject = CurrentValueSubject<AuthStatus, Never>(.guest)
    
    public init() {}
    
    public var authStatusPublisher: AnyPublisher<AuthStatus, Never> {
        statusSubject.eraseToAnyPublisher()
    }
    
    public func currentStatus() async -> AuthStatus {
        statusSubject.value
    }
    
    public func login(email: String) async throws -> User {
        try await Task.sleep(nanoseconds: 300_000_000) // Simulate auth network latency
        let user = User(
            id: "user-101",
            name: "Community Member",
            email: email,
            preferredLanguage: "Telugu",
            isSubscribed: true
        )
        statusSubject.send(.authenticated(user))
        return user
    }
    
    public func loginAsGuest() async throws {
        try await Task.sleep(nanoseconds: 150_000_000)
        statusSubject.send(.guest)
    }
    
    public func logout() async throws {
        try await Task.sleep(nanoseconds: 150_000_000)
        statusSubject.send(.unauthenticated)
    }
}
