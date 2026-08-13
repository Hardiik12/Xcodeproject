//
//  ProfileViewModel.swift
//  CommunityOTT
//
//  Created by CommunityOTT Team on 09/08/26.
//

import SwiftUI
import Combine

@MainActor
public final class ProfileViewModel: ObservableObject {
    @Published public private(set) var authStatus: AuthStatus = .guest
    @Published public private(set) var displayName: String = "Hardik Gupta"
    @Published public private(set) var currentAvatar: AvatarOption = AvatarOption.defaultAvatars[0]
    
    @Published public private(set) var watchedCount: Int = 12
    @Published public private(set) var savedCount: Int = 0
    @Published public private(set) var completedCount: Int = 4
    @Published public private(set) var continueWatchingItems: [ContentItem] = []
    
    @Published public var isShowingSettings: Bool = false
    @Published public var isShowingSavedList: Bool = false
    @Published public var isShowingWatchHistory: Bool = false
    @Published public var isShowingEditProfile: Bool = false
    @Published public var isShowingAbout: Bool = false
    @Published public var isShowingHelp: Bool = false
    @Published public var isShowingPrivacy: Bool = false
    
    private let authService: AuthServiceProtocol
    private let repository: ContentRepositoryProtocol
    private var cancellables = Set<AnyCancellable>()
    
    public init(
        authService: AuthServiceProtocol? = nil,
        repository: ContentRepositoryProtocol? = nil
    ) {
        self.authService = authService ?? MockAuthService()
        self.repository = repository ?? MockContentRepository()
        
        setupSubscriptions()
    }
    
    private func setupSubscriptions() {
        // Observe AuthService Status
        authService.authStatusPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] status in
                self?.authStatus = status
            }
            .store(in: &cancellables)
        
        // Observe UserProfileStore
        UserProfileStore.shared.$displayName
            .receive(on: DispatchQueue.main)
            .sink { [weak self] name in
                self?.displayName = name
            }
            .store(in: &cancellables)
        
        UserProfileStore.shared.$avatarID
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.currentAvatar = UserProfileStore.shared.currentAvatar
            }
            .store(in: &cancellables)
        
        // Observe SavedContentStore
        SavedContentStore.shared.$savedIDs
            .receive(on: DispatchQueue.main)
            .sink { [weak self] savedSet in
                self?.savedCount = savedSet.count
            }
            .store(in: &cancellables)
        
        // Observe PlaybackProgressStore
        PlaybackProgressStore.shared.$savedRecords
            .receive(on: DispatchQueue.main)
            .sink { [weak self] records in
                guard let self = self else { return }
                let watched = records.count
                let completed = records.values.filter { $0.isCompleted }.count
                if watched > 0 {
                    self.watchedCount = watched
                }
                self.completedCount = max(self.completedCount, completed)
            }
            .store(in: &cancellables)
    }
    
    public func loadData() async {
        do {
            let items = try await repository.fetchContinueWatching()
            self.continueWatchingItems = items
        } catch {
            self.continueWatchingItems = []
        }
    }
    
    public func signOut() async {
        do {
            try await authService.logout()
        } catch {
            print("Sign out error: \(error)")
        }
    }
    
    public func signInAsMember() async {
        do {
            _ = try await authService.login(email: "hardik@communityott.org")
        } catch {
            print("Sign in error: \(error)")
        }
    }
}
