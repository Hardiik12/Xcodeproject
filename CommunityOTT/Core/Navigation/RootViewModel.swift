//
//  RootViewModel.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 08/08/26.
//

import SwiftUI
import Combine

@MainActor
public final class RootViewModel: ObservableObject {
    @Published public private(set) var state: RootViewState = .splash
    @Published public private(set) var currentUser: User?
    @Published public private(set) var isGuestSession: Bool = false
    
    private let authService: AuthServiceProtocol
    private var cancellables = Set<AnyCancellable>()
    
    public init(authService: AuthServiceProtocol = MockAuthService()) {
        self.authService = authService
        setupAuthListener()
    }
    
    public func bootstrap() async {
        state = .splash
        // Simulate initial bootstrap loading (session check, remote config, theme setup)
        try? await Task.sleep(nanoseconds: 1_000_000_000)
        
        let currentStatus = await authService.currentStatus()
        updateState(for: currentStatus)
    }
    
    public func authenticateUser(email: String = "member@communityott.org") {
        Task {
            do {
                _ = try await authService.login(email: email)
            } catch {
                updateState(for: .unauthenticated)
            }
        }
    }
    
    public func continueAsGuest() {
        Task {
            do {
                try await authService.loginAsGuest()
            } catch {
                updateState(for: .unauthenticated)
            }
        }
    }
    
    public func signOut() {
        Task {
            try? await authService.logout()
        }
    }
    
    private func setupAuthListener() {
        authService.authStatusPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] status in
                self?.updateState(for: status)
            }
            .store(in: &cancellables)
    }
    
    private func updateState(for status: AuthStatus) {
        withAnimation(.easeInOut(duration: 0.35)) {
            switch status {
            case .unauthenticated:
                self.currentUser = nil
                self.isGuestSession = false
                self.state = .unauthenticated
            case .authenticated(let user):
                self.currentUser = user
                self.isGuestSession = false
                self.state = .authenticated
            case .guest:
                self.currentUser = nil
                self.isGuestSession = true
                self.state = .authenticated
            }
        }
    }
}
